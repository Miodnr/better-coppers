package dev.mioscoppergolems.upkeep;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.repair.Errands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * At night, and whenever it runs out of energy, a working golem walks to a redstone-powered block near its home and
 * stands on it to recharge. A powered block is one receiving a redstone signal (from a lever, a redstone torch, dust,
 * a daylight sensor...) or a block of redstone itself. Without such a block nearby the golem just waits.
 */
public class ChargeBehavior extends Behavior<CopperGolem> {
	private static final int SEARCH_RADIUS = 16;
	private static final int SEARCH_HEIGHT = 6;
	/** Empty to full in a bit under a minute. */
	private static final int CHARGE_PER_TICK = 1;
	private static final int RESEARCH_TICKS = 200;

	private @Nullable BlockPos pad;
	private int searchCooldown;
	private int travelTicks;

	public ChargeBehavior() {
		super(ImmutableMap.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return wantsCharge(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return !golem.isPanicking() && wantsCharge(level, golem);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	private static boolean wantsCharge(ServerLevel level, CopperGolem golem) {
		if (golem.isLeashed() || !Upkeep.isJobGolem(golem) || Errands.isBusy(golem)) {
			return false;
		}
		int energy = Upkeep.get(golem).energy();
		// At night it rests on its pad even when full, like going to bed.
		return level.isDarkOutside() || energy <= 0;
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.pad = null;
		this.travelTicks = 0;
		golem.setState(CopperGolemState.IDLE);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		if (this.pad == null || !isPowered(level, this.pad)) {
			this.pad = null;
			if (--this.searchCooldown > 0) {
				return;
			}
			this.searchCooldown = RESEARCH_TICKS;
			this.pad = findPad(level, Upkeep.homeOf(level, golem), golem);
			this.travelTicks = 0;
			if (this.pad == null) {
				return;
			}
		}
		BlockPos standOn = this.pad.above();
		if (!golem.blockPosition().equals(standOn)) {
			if (++this.travelTicks > 600) {
				// Cannot get there; try another one later.
				this.pad = null;
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, standOn, 1.0F, 0);
			return;
		}
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		GolemUpkeep upkeep = Upkeep.get(golem);
		if (upkeep.energy() < GolemUpkeep.MAX_ENERGY) {
			upkeep.setEnergy(upkeep.energy() + CHARGE_PER_TICK);
			if (level.getGameTime() % 10 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, golem.getX(), golem.getY() + 0.9, golem.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
			}
			if (upkeep.energy() >= GolemUpkeep.MAX_ENERGY) {
				Upkeep.playChargeSound(level, golem);
			}
		}
	}

	private static @Nullable BlockPos findPad(ServerLevel level, BlockPos around, CopperGolem golem) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos mutable : BlockPos.betweenClosed(around.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS), around.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS))) {
			if (!level.isLoaded(mutable) || !isPowered(level, mutable)) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (!isStandable(level, pos) || isTaken(level, pos, golem)) {
				continue;
			}
			double distance = pos.distToCenterSqr(golem.position());
			if (distance < bestDistance) {
				best = pos;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** A solid block that receives redstone power (or gives it off, like a block of redstone). */
	private static boolean isPowered(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isFaceSturdy(level, pos, Direction.UP)) {
			return false;
		}
		return level.hasNeighborSignal(pos) || state.isSignalSource() && state.getSignal(level, pos, Direction.UP) > 0;
	}

	private static boolean isStandable(ServerLevel level, BlockPos pad) {
		return level.getBlockState(pad.above()).getCollisionShape(level, pad.above()).isEmpty()
			&& level.getBlockState(pad.above(2)).getCollisionShape(level, pad.above(2)).isEmpty();
	}

	/** One golem per pad. */
	private static boolean isTaken(ServerLevel level, BlockPos pad, CopperGolem golem) {
		return !level.getEntitiesOfClass(CopperGolem.class, new AABB(pad.above()), other -> other != golem).isEmpty();
	}
}
