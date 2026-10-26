package dev.mioscoppergolems.automatisation.golem;

import dev.mioscoppergolems.api.GolemJob;
import dev.mioscoppergolems.api.GolemLinkTarget;
import dev.mioscoppergolems.automatisation.item.WrenchItem;
import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.registry.AutoAttachments;
import dev.mioscoppergolems.home.HomeDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The operator: a copper golem holding a copper wrench, linked to one machine (sneak + right-click the golem, then
 * right-click the machine). It walks over and works the machine's crank whenever the machine has something to do.
 * Operators pay the usual running costs: energy, rust, and they rest at night.
 */
public final class Operators {
	private static final double MAX_LINK_DISTANCE = 32.0;

	public static final GolemJob JOB = new GolemJob() {
		@Override
		public boolean has(CopperGolem golem) {
			return isOperator(golem);
		}

		@Override
		public Component name() {
			return Component.translatable("job.mioscoppergolems_automatisation.operator");
		}

		@Override
		public @Nullable BlockPos workplace(CopperGolem golem) {
			return machine(golem);
		}
	};

	public static final GolemLinkTarget LINK = new GolemLinkTarget() {
		@Override
		public boolean accepts(BlockState state) {
			return state.getBlock() instanceof MachineBlock<?>;
		}

		@Override
		public InteractionResult link(ServerPlayer player, ServerLevel level, CopperGolem golem, BlockPos pos) {
			if (!isOperator(golem)) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.needs_wrench"));
				return InteractionResult.FAIL;
			}
			if (golem.position().distanceTo(Vec3.atCenterOf(pos)) > MAX_LINK_DISTANCE) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.home_too_far"));
				return InteractionResult.FAIL;
			}
			golem.setAttached(AutoAttachments.MACHINE, pos.immutable());
			level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 1.0F, 1.2F);
			HomeDisplay.drawLine(level, golem, pos, null);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.operator_linked", level.getBlockState(pos).getBlock().getName()));
			return InteractionResult.SUCCESS;
		}
	};

	private Operators() {
	}

	public static boolean isOperator(CopperGolem golem) {
		return golem.getMainHandItem().getItem() instanceof WrenchItem;
	}

	public static @Nullable BlockPos machine(CopperGolem golem) {
		return golem.getAttached(AutoAttachments.MACHINE);
	}

	/** Right-clicking a golem with a wrench hands the wrench over. Its old main hand item is thrown back. */
	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof WrenchItem) || isOperator(golem)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel) {
			ItemStack current = golem.getMainHandItem();
			if (!current.isEmpty()) {
				BehaviorUtils.throwItem(golem, current, player.position());
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
			golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			held.consume(1, player);
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
			player.sendOverlayMessage(Component.translatable(
				machine(golem) == null ? "message.mioscoppergolems_automatisation.operator_needs_machine" : "message.mioscoppergolems_automatisation.operator_ready"
			));
		}
		return InteractionResult.SUCCESS;
	}
}
