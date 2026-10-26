package dev.mioscoppergolems.automatisation.golem;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import dev.mioscoppergolems.golem.TravelWatch;
import dev.mioscoppergolems.upkeep.Upkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.phys.Vec3;

/**
 * An operator walks to its machine and turns the crank for as long as the machine has work, reaching out every couple
 * of seconds like it does at a chest. With nothing to do it potters about nearby. Slow when rusty, idle at night.
 */
public class OperatorBehavior extends Behavior<CopperGolem> {
	private static final double REACH = 2.2;
	private static final double STAY_NEAR = 5.0;
	private static final int IDLE_COOLDOWN = 40;
	private static final int UNREACHABLE_COOLDOWN = 200;
	private static final int MAX_STALL_TICKS = 300;
	/** One energy every second of cranking: a full charge lasts most of a day. */
	private static final int ENERGY_INTERVAL = 20;
	private static final int REACH_ANIMATION_INTERVAL = 40;

	private final TravelWatch travel = new TravelWatch();
	private int crankTicks;

	public OperatorBehavior() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return Operators.isOperator(golem) && Operators.machine(golem) != null && !golem.isLeashed() && Upkeep.canWork(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty()
			&& !golem.isPanicking()
			&& this.checkExtraStartConditions(level, golem);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		this.travel.reset();
		this.crankTicks = 0;
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		golem.setState(CopperGolemState.IDLE);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		BlockPos pos = Operators.machine(golem);
		if (pos == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
			this.rest(golem, UNREACHABLE_COOLDOWN);
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		double distance = horizontalDistance(golem.position(), center);
		boolean inReach = distance <= REACH && Math.abs(golem.getY() - pos.getY()) <= 2.5;

		if (!machine.wantsWork(level)) {
			if (distance > STAY_NEAR) {
				BehaviorUtils.setWalkAndLookTargetMemories(golem, pos, 0.8F * Upkeep.speed(golem), 2);
			} else {
				this.rest(golem, IDLE_COOLDOWN);
			}
			return;
		}
		if (!inReach) {
			this.crankTicks = 0;
			if (this.travel.tick(golem, center) > MAX_STALL_TICKS) {
				this.rest(golem, UNREACHABLE_COOLDOWN);
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, pos, 0.8F * Upkeep.speed(golem), 1);
			return;
		}
		this.travel.reset();
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
		// Rust makes the golem crank more slowly: some ticks it just does not get the crank round.
		if (golem.getRandom().nextFloat() < Upkeep.speed(golem)) {
			machine.operate(level);
		}
		this.crankTicks++;
		if (this.crankTicks % REACH_ANIMATION_INTERVAL == 1) {
			golem.setState(CopperGolemState.GETTING_ITEM);
			level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.NEUTRAL, 0.35F, 0.6F + golem.getRandom().nextFloat() * 0.2F);
		} else if (this.crankTicks % REACH_ANIMATION_INTERVAL == 25) {
			golem.setState(CopperGolemState.IDLE);
		}
		if (this.crankTicks % ENERGY_INTERVAL == 0) {
			Upkeep.spend(golem, 1);
		}
	}

	private void rest(CopperGolem golem, int ticks) {
		golem.setState(CopperGolemState.IDLE);
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, ticks);
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
	}
}
