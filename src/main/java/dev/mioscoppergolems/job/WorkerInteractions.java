package dev.mioscoppergolems.job;

import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.registry.ModAttachments;
import dev.mioscoppergolems.upkeep.Upkeep;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Handing a copper golem its job tool: an axe (forester), a fishing rod (fisher), a brush (animal keeper) or a crafting
 * table (crafter). An axe only becomes a tool for a clean golem; on a rusty or waxed one it scrapes, as in vanilla.
 * Sneak + right-click with an empty hand shows any working golem's energy and rust.
 */
public final class WorkerInteractions {
	private WorkerInteractions() {
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		Job job = Job.forTool(held);
		if (job == null) {
			return InteractionResult.PASS;
		}
		if (job == Job.FORESTER && (golem.getWeatherState() != WeatheringCopper.WeatherState.UNAFFECTED || Upkeep.isWaxed(golem))) {
			// Let vanilla scrape it first.
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			ItemStack current = golem.getMainHandItem();
			if (!current.isEmpty()) {
				BehaviorUtils.throwItem(golem, current, player.position());
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
			golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			held.consume(1, player);
			Tools.clearLost(golem);
			WorkerData data = WorkerData.getOrCreate(golem);
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
			Component jobName = Component.translatable("job.mioscoppergolems." + job.id());
			Guide.job(player, job.id());
			player.sendOverlayMessage(
				Homes.get(golem) == null
					? Component.translatable("message.mioscoppergolems.worker_no_chest", jobName)
					: Component.translatable("message.mioscoppergolems.worker_ready", jobName)
			);
		}
		return InteractionResult.SUCCESS;
	}

	public static void dropAll(CopperGolem golem) {
		if (!(golem.level() instanceof ServerLevel level)) {
			return;
		}
		WorkerData data = golem.removeAttached(ModAttachments.WORKER);
		if (data != null) {
			data.pouch().forEach(stack -> golem.spawnAtLocation(level, stack));
			data.supplies().forEach(stack -> golem.spawnAtLocation(level, stack));
		}
	}
}
