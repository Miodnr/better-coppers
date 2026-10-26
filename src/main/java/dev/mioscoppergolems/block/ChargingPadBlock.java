package dev.mioscoppergolems.block;

import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.upkeep.GolemUpkeep;
import dev.mioscoppergolems.upkeep.Upkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Where working golems recharge. A steady redstone signal does nothing: every redstone <em>pulse</em> (the signal
 * switching on) gives the golem standing on top {@link #ENERGY_PER_PULSE} energy, so the pad needs a redstone clock.
 * Pulses closer together than {@link #COOLDOWN_TICKS} only count once.
 * <p>
 * With a clock that pulses once a second, an empty golem is full in about two minutes; with the fastest clock, in
 * about 25 seconds.
 */
public class ChargingPadBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Set for a moment after each pulse; pulses meanwhile are ignored. */
	public static final BooleanProperty COOLING = BooleanProperty.create("cooling");
	public static final int ENERGY_PER_PULSE = 8;
	private static final int COOLDOWN_TICKS = 4;

	public ChargingPadBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false).setValue(COOLING, false));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// Placing it next to a lit torch is not a pulse.
		return this.defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		boolean signal = level.hasNeighborSignal(pos);
		if (signal == state.getValue(POWERED)) {
			return;
		}
		BlockState updated = state.setValue(POWERED, signal);
		if (signal && !state.getValue(COOLING)) {
			updated = updated.setValue(COOLING, true);
			level.scheduleTick(pos, this, COOLDOWN_TICKS);
			pulse(serverLevel, pos);
		}
		level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(COOLING)) {
			level.setBlock(pos, state.setValue(COOLING, false), Block.UPDATE_CLIENTS);
		}
	}

	/** Charges every working golem standing on the pad. */
	private static void pulse(ServerLevel level, BlockPos pos) {
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, new AABB(pos.above()))) {
			if (!Upkeep.isJobGolem(golem)) {
				continue;
			}
			GolemUpkeep upkeep = Upkeep.get(golem);
			if (upkeep.energy() >= GolemUpkeep.MAX_ENERGY) {
				continue;
			}
			upkeep.setEnergy(upkeep.energy() + ENERGY_PER_PULSE);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, golem.getX(), golem.getY() + 0.9, golem.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.3F, 1.6F + level.getRandom().nextFloat() * 0.3F);
			if (upkeep.energy() >= GolemUpkeep.MAX_ENERGY) {
				Upkeep.playChargeSound(level, golem);
				Guide.awardNearby(level, golem.position(), "recharge");
			}
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED, COOLING);
	}
}
