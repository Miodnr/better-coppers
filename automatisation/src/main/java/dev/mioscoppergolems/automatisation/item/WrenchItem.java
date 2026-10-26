package dev.mioscoppergolems.automatisation.item;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.arm.GrabberArmBlockEntity;
import dev.mioscoppergolems.automatisation.funnel.FunnelBlock;
import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The copper wrench. Given to a golem it makes it an operator (see the golem package). In a player's hand it switches
 * funnels between pulling and pushing, and marks the spots of a grabber arm.
 */
public class WrenchItem extends Item {
	private static final DustParticleOptions INPUT = new DustParticleOptions(0x3F8FD8, 1.2F);
	private static final DustParticleOptions OUTPUT = new DustParticleOptions(0xD8843F, 1.2F);
	/** The arm each player is marking spots for. */
	private static final Map<UUID, BlockPos> MARKING = new HashMap<>();

	public WrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		BlockState state = level.getBlockState(pos);
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (state.getBlock() instanceof FunnelBlock) {
			if (level instanceof ServerLevel) {
				boolean pushing = !state.getValue(FunnelBlock.PUSHING);
				level.setBlock(pos, state.setValue(FunnelBlock.PUSHING, pushing), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.COPPER_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, pushing ? 1.4F : 1.0F);
				player.sendOverlayMessage(Component.translatable(pushing ? "message.mioscoppergolems_automatisation.funnel_push" : "message.mioscoppergolems_automatisation.funnel_pull"));
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			// Sneak + wrench: how worn the machine is.
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable(
					machine.isBroken() ? "message.mioscoppergolems_automatisation.machine_broken" : "message.mioscoppergolems_automatisation.machine_wear",
					machine.wearPercent(),
					new ItemStack(machine.repairItem()).getHoverName()
				));
			}
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return level.getBlockEntity(pos) instanceof GrabberArmBlockEntity || MARKING.containsKey(player.getUUID()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (level.getBlockEntity(pos) instanceof GrabberArmBlockEntity arm) {
			if (pos.equals(MARKING.get(player.getUUID()))) {
				MARKING.remove(player.getUUID());
				long inputs = arm.spots().stream().filter(GrabberArmBlockEntity.Spot::input).count();
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.arm_done", inputs, arm.spots().size() - inputs));
				if (inputs > 0 && arm.spots().size() > inputs) {
					MiosCoppergolemsApi.awardGuide(player, Automatisation.id("guide/arm"));
				}
			} else {
				MARKING.put(player.getUUID(), pos.immutable());
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.arm_marking", GrabberArmBlockEntity.RANGE));
				showSpots(serverLevel, arm);
			}
			level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.BLOCKS, 0.6F, 1.5F);
			return InteractionResult.SUCCESS;
		}
		GrabberArmBlockEntity arm = markingArm(serverLevel, player);
		if (arm == null) {
			return InteractionResult.PASS;
		}
		if (!arm.inRange(pos)) {
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.arm_too_far", GrabberArmBlockEntity.RANGE));
			return InteractionResult.FAIL;
		}
		Boolean role = arm.toggle(pos);
		String key = role == null ? "arm_spot_removed" : role ? "arm_spot_input" : "arm_spot_output";
		player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation." + key));
		level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.BLOCKS, 0.6F, role == null ? 0.8F : 1.4F);
		showSpots(serverLevel, arm);
		return InteractionResult.SUCCESS;
	}

	private static @Nullable GrabberArmBlockEntity markingArm(ServerLevel level, Player player) {
		BlockPos armPos = MARKING.get(player.getUUID());
		if (armPos == null) {
			return null;
		}
		if (level.getBlockEntity(armPos) instanceof GrabberArmBlockEntity arm) {
			return arm;
		}
		MARKING.remove(player.getUUID());
		return null;
	}

	/** Blue dust over inputs, orange over outputs. */
	private static void showSpots(ServerLevel level, GrabberArmBlockEntity arm) {
		for (GrabberArmBlockEntity.Spot spot : arm.spots()) {
			BlockPos pos = spot.pos();
			level.sendParticles(spot.input() ? INPUT : OUTPUT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.0);
		}
	}
}
