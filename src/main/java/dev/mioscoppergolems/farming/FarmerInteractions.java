package dev.mioscoppergolems.farming;

import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turning a copper golem into a farmer:
 * <ul>
 * <li>right-click with a hoe: the golem holds it and looks after the fields around the nearest copper chest;</li>
 * <li>right-click a farmer with seeds: it puts them in its seed pouch;</li>
 * <li>right-click with an empty hand (vanilla): the golem hands back the hoe and goes back to sorting.</li>
 * </ul>
 */
public final class FarmerInteractions {
	private FarmerInteractions() {
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(ItemTags.HOES)) {
			if (level instanceof ServerLevel serverLevel) {
				giveHoe(player, serverLevel, golem, held);
			}
			return InteractionResult.SUCCESS;
		}
		if (FarmerData.isSeed(held) && FarmerData.isFarmer(golem)) {
			if (!level.isClientSide()) {
				FarmBehavior.addTo(FarmerData.getOrCreate(golem).seeds(), held.copy());
				held.consume(held.getCount(), player);
				golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void giveHoe(Player player, ServerLevel level, CopperGolem golem, ItemStack held) {
		ItemStack current = golem.getMainHandItem();
		if (!current.isEmpty()) {
			BehaviorUtils.throwItem(golem, current, player.position());
		}
		golem.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		held.consume(1, player);
		FarmerData data = FarmerData.getOrCreate(golem);
		golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
		player.sendOverlayMessage(Component.translatable(Homes.get(golem) == null ? "message.mioscoppergolems.farmer_no_chest" : "message.mioscoppergolems.farmer_ready"));
	}

	/** Drops harvest and seeds with the golem. */
	public static void dropAll(CopperGolem golem) {
		if (!(golem.level() instanceof ServerLevel level)) {
			return;
		}
		FarmerData data = golem.removeAttached(ModAttachments.FARMER);
		if (data != null) {
			data.harvest().forEach(stack -> golem.spawnAtLocation(level, stack));
			data.seeds().forEach(stack -> golem.spawnAtLocation(level, stack));
		}
	}
}
