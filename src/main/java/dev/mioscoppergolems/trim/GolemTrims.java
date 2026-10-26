package dev.mioscoppergolems.trim;

import dev.mioscoppergolems.registry.ModAttachments;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Armour trims for copper golems, purely for looks. Right-click a golem with a smithing template, then with a trim
 * material; the material is used up, the template stays with the golem. A new template or material changes the trim.
 * Shears take it off again (after any flower on its head) and give the template back.
 */
public final class GolemTrims {
	private static final String TEMPLATE_SUFFIX = "_armor_trim_smithing_template";

	private GolemTrims() {
	}

	public static @Nullable ArmorTrim trim(CopperGolem golem) {
		return golem.getAttached(ModAttachments.TRIM);
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (isTemplate(held)) {
			if (level instanceof ServerLevel serverLevel) {
				return applyTemplate(serverLevel, player, golem, held);
			}
			return InteractionResult.SUCCESS;
		}
		Holder<TrimMaterial> material = held.get(DataComponents.PROVIDES_TRIM_MATERIAL);
		if (material != null && level instanceof ServerLevel serverLevel) {
			return applyMaterial(serverLevel, player, golem, held, material);
		}
		if (held.is(Items.SHEARS) && golem.getItemBySlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA).isEmpty()) {
			if (level instanceof ServerLevel serverLevel) {
				return removeTrim(serverLevel, player, golem, held);
			}
			return trim(golem) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult applyTemplate(ServerLevel level, Player player, CopperGolem golem, ItemStack held) {
		Optional<Holder.Reference<TrimPattern>> pattern = patternOf(level, held);
		if (pattern.isEmpty()) {
			return InteractionResult.PASS;
		}
		ItemStack old = golem.getAttached(ModAttachments.TRIM_TEMPLATE);
		if (old != null && ItemStack.isSameItem(old, held)) {
			return InteractionResult.PASS;
		}
		if (old != null) {
			giveBack(level, player, golem, old);
		}
		golem.setAttached(ModAttachments.TRIM_TEMPLATE, held.copyWithCount(1));
		held.consume(1, player);
		ArmorTrim current = trim(golem);
		if (current != null) {
			golem.setAttached(ModAttachments.TRIM, new ArmorTrim(current.material(), pattern.get()));
		} else {
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.trim_needs_material"));
		}
		golem.playSound(SoundEvents.SMITHING_TABLE_USE, 0.6F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult applyMaterial(ServerLevel level, Player player, CopperGolem golem, ItemStack held, Holder<TrimMaterial> material) {
		ItemStack template = golem.getAttached(ModAttachments.TRIM_TEMPLATE);
		Optional<Holder.Reference<TrimPattern>> pattern = template == null ? Optional.empty() : patternOf(level, template);
		if (pattern.isEmpty()) {
			// Iron, gold, redstone... are everyday items too: only take them once a template is on.
			return InteractionResult.PASS;
		}
		ArmorTrim trim = new ArmorTrim(material, pattern.get());
		if (trim.equals(trim(golem))) {
			return InteractionResult.PASS;
		}
		golem.setAttached(ModAttachments.TRIM, trim);
		held.consume(1, player);
		golem.playSound(SoundEvents.SMITHING_TABLE_USE);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult removeTrim(ServerLevel level, Player player, CopperGolem golem, ItemStack shears) {
		ItemStack template = golem.removeAttached(ModAttachments.TRIM_TEMPLATE);
		ArmorTrim trim = golem.removeAttached(ModAttachments.TRIM);
		if (template == null && trim == null) {
			return InteractionResult.PASS;
		}
		if (template != null) {
			giveBack(level, player, golem, template);
		}
		golem.playSound(SoundEvents.SHEARS_SNIP);
		shears.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		return InteractionResult.SUCCESS;
	}

	/** Drops the template (on death, or when the golem turns into a statue). */
	public static void dropAll(ServerLevel level, CopperGolem golem) {
		ItemStack template = golem.removeAttached(ModAttachments.TRIM_TEMPLATE);
		golem.removeAttached(ModAttachments.TRIM);
		if (template != null) {
			golem.spawnAtLocation(level, template);
		}
	}

	private static void giveBack(ServerLevel level, Player player, CopperGolem golem, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			golem.spawnAtLocation(level, stack);
		}
	}

	private static boolean isTemplate(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith(TEMPLATE_SUFFIX);
	}

	/** Templates are named after their pattern: {@code sentry_armor_trim_smithing_template} -> {@code sentry}. */
	private static Optional<Holder.Reference<TrimPattern>> patternOf(ServerLevel level, ItemStack template) {
		Identifier item = BuiltInRegistries.ITEM.getKey(template.getItem());
		if (!item.getPath().endsWith(TEMPLATE_SUFFIX)) {
			return Optional.empty();
		}
		Identifier pattern = Identifier.fromNamespaceAndPath(item.getNamespace(), item.getPath().substring(0, item.getPath().length() - TEMPLATE_SUFFIX.length()));
		return level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(ResourceKey.create(Registries.TRIM_PATTERN, pattern));
	}
}
