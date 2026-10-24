package dev.bettercoppers.golem;

import dev.bettercoppers.home.Homes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A golem only sorts once it is given a bundle (kept in its off hand, since the main hand carries the items). Without
 * one it just idles. Right-clicking a sorter with an empty hand, while it carries nothing, takes the bundle back.
 */
public final class Sorter {
	private Sorter() {
	}

	public static boolean isSorter(CopperGolem golem) {
		return golem.getOffhandItem().is(ItemTags.BUNDLES);
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(ItemTags.BUNDLES) && !isSorter(golem)) {
			if (!level.isClientSide()) {
				golem.setItemSlot(EquipmentSlot.OFFHAND, held.copyWithCount(1));
				golem.setGuaranteedDrop(EquipmentSlot.OFFHAND);
				held.consume(1, player);
				golem.playSound(SoundEvents.BUNDLE_INSERT);
				player.sendOverlayMessage(
					Component.translatable(Homes.get(golem) == null ? "message.bettercoppers.sorter_needs_home" : "message.bettercoppers.sorter_ready")
				);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && isSorter(golem) && golem.getMainHandItem().isEmpty()) {
			if (!level.isClientSide()) {
				ItemStack bundle = golem.getOffhandItem();
				golem.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				if (!player.getInventory().add(bundle)) {
					golem.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, bundle);
				}
				golem.playSound(SoundEvents.BUNDLE_REMOVE_ONE);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
