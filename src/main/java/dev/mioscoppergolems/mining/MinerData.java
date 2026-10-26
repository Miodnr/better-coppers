package dev.mioscoppergolems.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.golem.CartChaining;
import dev.mioscoppergolems.job.Tools;
import dev.mioscoppergolems.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Everything a mining golem carries besides its pickaxe: the plan it follows, a pouch for what it digs up, and the
 * supplies (torches, wood, rails, filler blocks) it takes along. Stored on the golem as a Fabric attachment.
 */
public final class MinerData {
	/** Without a cart a golem goes home once it carries this many mined items. */
	public static final int LOOT_LIMIT = 64;

	public static final Codec<MinerData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				MinePlan.CODEC.optionalFieldOf("plan").forGetter(data -> Optional.ofNullable(data.plan)),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(data -> data.loot),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("supplies", List.of()).forGetter(data -> data.supplies)
			)
			.apply(instance, (plan, loot, supplies) -> {
				MinerData data = new MinerData();
				data.plan = plan.orElse(null);
				data.loot.addAll(loot.stream().filter(stack -> !stack.isEmpty()).toList());
				data.supplies.addAll(supplies.stream().filter(stack -> !stack.isEmpty()).toList());
				return data;
			})
	);

	private @Nullable MinePlan plan;
	private final List<ItemStack> loot = new ArrayList<>();
	private final List<ItemStack> supplies = new ArrayList<>();

	public static @Nullable MinerData get(CopperGolem golem) {
		return golem.getAttached(ModAttachments.MINER);
	}

	public static MinerData getOrCreate(CopperGolem golem) {
		return golem.getAttachedOrCreate(ModAttachments.MINER, MinerData::new);
	}

	public static boolean hasPickaxe(CopperGolem golem) {
		return golem.getMainHandItem().is(Items.COPPER_PICKAXE);
	}

	/** A golem that was handed a pickaxe, or still has mined items to bring home. Miners do not sort. */
	public static boolean isMiner(CopperGolem golem) {
		MinerData data = get(golem);
		if (data == null) {
			return false;
		}
		if (hasPickaxe(golem) || Tools.hasLost(golem, Items.COPPER_PICKAXE) || data.lootCount() > 0) {
			return true;
		}
		CopperChestMinecart cart = data.plan() == null ? null : CartChaining.findCart(golem);
		return cart != null && !cart.isEmpty();
	}

	public @Nullable MinePlan plan() {
		return this.plan;
	}

	public void setPlan(@Nullable MinePlan plan) {
		this.plan = plan;
	}

	/** Mutable pouch of mined items. */
	public List<ItemStack> loot() {
		return this.loot;
	}

	/** Mutable pouch of building supplies. */
	public List<ItemStack> supplies() {
		return this.supplies;
	}

	public int lootCount() {
		return this.loot.stream().mapToInt(ItemStack::getCount).sum();
	}

	/** Adds to a pouch, merging with existing stacks. */
	public static void addTo(List<ItemStack> pouch, ItemStack stack) {
		for (ItemStack existing : pouch) {
			if (stack.isEmpty()) {
				return;
			}
			if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				existing.grow(moved);
				stack.shrink(moved);
			}
		}
		if (!stack.isEmpty()) {
			pouch.add(stack.copy());
			stack.setCount(0);
		}
	}
}
