package dev.bettercoppers.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bettercoppers.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import org.jspecify.annotations.Nullable;

/**
 * A farming golem's home chest, its pouch of harvest and the seeds it keeps for replanting. Stored on the golem as a
 * Fabric attachment.
 */
public final class FarmerData {
	/** Without a cart a farmer goes home once it carries this many harvested items. */
	public static final int HARVEST_LIMIT = 64;

	public static final Codec<FarmerData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				BlockPos.CODEC.optionalFieldOf("home").forGetter(data -> Optional.ofNullable(data.home)),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("harvest", List.of()).forGetter(data -> data.harvest),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("seeds", List.of()).forGetter(data -> data.seeds)
			)
			.apply(instance, (home, harvest, seeds) -> {
				FarmerData data = new FarmerData();
				data.home = home.orElse(null);
				data.harvest.addAll(harvest.stream().filter(stack -> !stack.isEmpty()).toList());
				data.seeds.addAll(seeds.stream().filter(stack -> !stack.isEmpty()).toList());
				return data;
			})
	);

	private @Nullable BlockPos home;
	private final List<ItemStack> harvest = new ArrayList<>();
	private final List<ItemStack> seeds = new ArrayList<>();

	public static @Nullable FarmerData get(CopperGolem golem) {
		return golem.getAttached(ModAttachments.FARMER);
	}

	public static FarmerData getOrCreate(CopperGolem golem) {
		return golem.getAttachedOrCreate(ModAttachments.FARMER, FarmerData::new);
	}

	public static boolean hasHoe(CopperGolem golem) {
		return golem.getMainHandItem().is(ItemTags.HOES);
	}

	/** A golem that was handed a hoe, or still has harvest to bring home. Farmers do not sort. */
	public static boolean isFarmer(CopperGolem golem) {
		FarmerData data = get(golem);
		return data != null && (hasHoe(golem) || data.harvestCount() > 0);
	}

	/** Anything a farmer can plant: crops for farmland and nether wart for soul sand. */
	public static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && (blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof NetherWartBlock);
	}

	public @Nullable BlockPos home() {
		return this.home;
	}

	public void setHome(@Nullable BlockPos home) {
		this.home = home;
	}

	public List<ItemStack> harvest() {
		return this.harvest;
	}

	public List<ItemStack> seeds() {
		return this.seeds;
	}

	public int harvestCount() {
		return this.harvest.stream().mapToInt(ItemStack::getCount).sum();
	}
}
