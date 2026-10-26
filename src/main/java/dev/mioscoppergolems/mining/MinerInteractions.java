package dev.mioscoppergolems.mining;

import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.job.Tools;
import dev.mioscoppergolems.registry.ModAttachments;
import dev.mioscoppergolems.registry.ModComponents;
import dev.mioscoppergolems.registry.ModItems;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turning a copper golem into a miner and back:
 * <ul>
 * <li>right-click with a pickaxe: the golem holds it (and stops sorting);</li>
 * <li>right-click with a marked plan: the golem keeps the plan and starts digging;</li>
 * <li>sneak + right-click with an empty hand: the golem hands the plan back, progress included;</li>
 * <li>right-click with an empty hand (vanilla): the golem hands over what it holds, i.e. the pickaxe.</li>
 * </ul>
 */
public final class MinerInteractions {
	private MinerInteractions() {
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.is(ModItems.MINESHAFT_PLAN)) {
			return givePlan(player, level, golem, held);
		}
		if (held.is(Items.COPPER_PICKAXE)) {
			return givePickaxe(player, level, golem, held);
		}
		if (held.isEmpty() && !player.isShiftKeyDown() && MinerData.hasPickaxe(golem)) {
			// Taking the pickaxe back (vanilla hands it over) also returns the plan, progress included.
			MinerData data = MinerData.get(golem);
			if (data != null && data.plan() != null && !level.isClientSide()) {
				returnPlan(player, golem, data);
			}
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult givePlan(Player player, Level level, CopperGolem golem, ItemStack held) {
		MinePlan plan = held.get(ModComponents.MINE_PLAN);
		if (plan == null) {
			return InteractionResult.PASS;
		}
		if (!plan.isMarked()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.plan_unmarked"));
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			MinerData data = MinerData.getOrCreate(golem);
			if (data.plan() != null) {
				returnPlan(player, golem, data);
			}
			data.setPlan(plan);
			held.consume(1, player);
			golem.playSound(SoundEvents.BOOK_PAGE_TURN);
			if (!MinerData.hasPickaxe(golem)) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.needs_pickaxe"));
			} else {
				Guide.job(player, "miner");
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult givePickaxe(Player player, Level level, CopperGolem golem, ItemStack held) {
		if (!level.isClientSide()) {
			ItemStack current = golem.getMainHandItem();
			if (!current.isEmpty()) {
				BehaviorUtils.throwItem(golem, current, player.position());
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
			golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			held.consume(1, player);
			Tools.clearLost(golem);
			MinerData data = MinerData.getOrCreate(golem);
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
			if (data.plan() == null) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.needs_plan"));
			} else {
				Guide.job(player, "miner");
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void returnPlan(Player player, CopperGolem golem, MinerData data) {
		ItemStack stack = new ItemStack(ModItems.MINESHAFT_PLAN);
		stack.set(ModComponents.MINE_PLAN, data.plan());
		data.setPlan(null);
		if (!player.getInventory().add(stack)) {
			player.spawnAtLocation((ServerLevel) player.level(), stack);
		}
		golem.playSound(SoundEvents.BOOK_PAGE_TURN);
	}

	/** Drops the plan, pouch and supplies with the golem. */
	public static void dropAll(CopperGolem golem) {
		if (!(golem.level() instanceof ServerLevel level)) {
			return;
		}
		MinerData data = golem.removeAttached(ModAttachments.MINER);
		if (data == null) {
			return;
		}
		if (data.plan() != null) {
			ItemStack plan = new ItemStack(ModItems.MINESHAFT_PLAN);
			plan.set(ModComponents.MINE_PLAN, data.plan());
			golem.spawnAtLocation(level, plan);
		}
		data.loot().forEach(stack -> golem.spawnAtLocation(level, stack));
		data.supplies().forEach(stack -> golem.spawnAtLocation(level, stack));
	}
}
