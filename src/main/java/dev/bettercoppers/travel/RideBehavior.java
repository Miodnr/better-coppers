package dev.bettercoppers.travel;

import com.google.common.collect.ImmutableMap;
import dev.bettercoppers.golem.CartChaining;
import dev.bettercoppers.upkeep.Upkeep;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whatever a golem is walking to, if it is far away and an empty minecart on a track (or a boat on water) nearby
 * points that way, the golem climbs in and rides. It pushes the cart along the rails, or paddles the boat straight at
 * its goal, and gets out once it is close, the cart stops, or it stops getting any closer.
 * <p>
 * Runs after the job behaviours, so it reads their walk target first and only then overrides it to reach the vehicle.
 */
public class RideBehavior extends Behavior<CopperGolem> {
	private static final double MIN_TRIP = 24.0;
	private static final double VEHICLE_RANGE = 8.0;
	private static final double ARRIVE_DISTANCE = 10.0;
	private static final double BOARD_DISTANCE = 1.8;
	private static final int SEARCH_INTERVAL = 20;
	private static final int APPROACH_TICKS = 200;
	private static final int NO_PROGRESS_TICKS = 60;
	/** A golem that just got off steps out again if the cart scoops it back up within this time. */
	private static final int LEFT_TICKS = 100;
	/** A cart or boat that did not get the golem anywhere is not tried again for the same goal for a minute. */
	private static final int DEAD_END_TICKS = 1200;
	private static final double SAME_GOAL = 16.0;
	private static final double USEFUL_RIDE = 8.0;
	private static final double CART_PUSH = 0.03;
	private static final double CART_TOP_SPEED = 0.35;
	private static final double BOAT_PADDLE = 0.04;
	private static final float BOAT_TURN = 5.0F;

	private record DeadEnd(Vec3 goal, long until) {
	}

	private final Map<UUID, Long> leftUntil = new HashMap<>();
	private final Map<UUID, DeadEnd> deadEnds = new HashMap<>();
	private boolean deadEnd;
	private @Nullable Entity vehicle;
	private @Nullable Vec3 destination;
	private boolean riding;
	private boolean finished;
	private int ticks;
	private double bestDistance;
	private double startDistance;
	private int stalled;

	public RideBehavior() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		Entity current = golem.getVehicle();
		if (current != null && this.leftUntil.getOrDefault(current.getUUID(), 0L) > level.getGameTime()) {
			// A cart rolling on picks up whoever stands in its way, the golem that just got off included.
			golem.stopRiding();
			current.setDeltaMovement(Vec3.ZERO);
			return false;
		}
		if ((level.getGameTime() + golem.getId()) % SEARCH_INTERVAL != 0 || golem.isLeashed() || CartChaining.findCart(golem) != null) {
			// A golem towing a chained cart stays on foot: the cart would not keep up.
			return false;
		}
		Vec3 goal = walkGoal(golem);
		if (goal == null) {
			return false;
		}
		boolean far = horizontalDistance(golem.position(), goal) >= MIN_TRIP;
		if (current != null) {
			// A working golem that ended up alone in a cart or boat by bumping into it uses it if it goes the right
			// way, or else climbs out and walks. Golems without a job may stay parked in a boat, as in vanilla.
			if (!(current instanceof Minecart || current instanceof AbstractBoat) || current.getPassengers().size() != 1 || !Upkeep.hasJob(golem)) {
				return false;
			}
			if (far && leadsTowards(level, current, goal.subtract(golem.position()).multiply(1.0, 0.0, 1.0).normalize())) {
				this.vehicle = current;
				this.destination = goal;
				return true;
			}
			golem.stopRiding();
			return false;
		}
		if (!far) {
			return false;
		}
		Entity found = this.findVehicle(level, golem, goal);
		if (found == null) {
			return false;
		}
		this.vehicle = found;
		this.destination = goal;
		return true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return !this.finished && this.vehicle != null && this.vehicle.isAlive() && golem.isAlive() && !golem.isLeashed() && (!this.riding || golem.getVehicle() == this.vehicle);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		this.riding = false;
		this.finished = false;
		this.deadEnd = false;
		this.ticks = 0;
		this.stalled = 0;
		this.bestDistance = Double.MAX_VALUE;
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		if (this.vehicle != null) {
			this.leftUntil.put(this.vehicle.getUUID(), level.getGameTime() + LEFT_TICKS);
			if (this.deadEnd && this.destination != null) {
				this.deadEnds.put(this.vehicle.getUUID(), new DeadEnd(this.destination, level.getGameTime() + DEAD_END_TICKS));
			}
		}
		if (this.riding && golem.getVehicle() == this.vehicle) {
			golem.stopRiding();
			// Brake, or a rolling cart would scoop the golem right back up.
			this.vehicle.setDeltaMovement(Vec3.ZERO);
		}
		this.vehicle = null;
		this.destination = null;
		this.riding = false;
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		Entity ride = this.vehicle;
		if (ride == null) {
			return;
		}
		// The job behaviours refresh the walk target every tick; follow it if it moved.
		Vec3 goal = walkGoal(golem);
		if (goal != null && goal.distanceToSqr(ride.position()) > 4.0) {
			this.destination = goal;
		}
		if (this.destination == null) {
			this.finished = true;
			return;
		}
		if (!this.riding) {
			this.tickApproach(golem, ride);
		} else {
			this.tickRide(golem, ride, this.destination);
		}
	}

	private void tickApproach(CopperGolem golem, Entity ride) {
		if (golem.getVehicle() == ride) {
			// A boat takes in whoever bumps into it, so the golem may already be aboard.
			this.riding = true;
			this.ticks = 0;
			return;
		}
		if (++this.ticks > APPROACH_TICKS || ride.isVehicle()) {
			this.finished = true;
			this.deadEnd = true;
			return;
		}
		if (golem.distanceTo(ride) > BOARD_DISTANCE) {
			BehaviorUtils.setWalkAndLookTargetMemories(golem, ride.blockPosition(), 1.0F, 0);
			return;
		}
		if (golem.startRiding(ride)) {
			this.riding = true;
			this.ticks = 0;
			golem.getNavigation().stop();
		} else {
			this.finished = true;
		}
	}

	private void tickRide(CopperGolem golem, Entity ride, Vec3 goal) {
		double distance = horizontalDistance(ride.position(), goal);
		// A boat goes on until it runs ashore (no more progress), so the golem steps out onto land, not into the water.
		if (distance < (ride instanceof AbstractBoat ? 1.5 : ARRIVE_DISTANCE)) {
			this.finished = true;
			return;
		}
		if (this.bestDistance == Double.MAX_VALUE) {
			this.startDistance = distance;
		}
		if (distance < this.bestDistance - 0.25) {
			this.bestDistance = distance;
			this.stalled = 0;
		} else if (++this.stalled > NO_PROGRESS_TICKS) {
			// End of the track, the shore, or it is heading the wrong way: get out and walk. Only a ride that got it
			// nowhere counts as a dead end.
			this.finished = true;
			this.deadEnd = this.startDistance - distance < USEFUL_RIDE;
			return;
		}
		golem.getNavigation().stop();
		if (ride instanceof Minecart cart) {
			pushCart(cart, goal);
		} else if (ride instanceof AbstractBoat boat) {
			paddle(boat, goal);
		}
	}

	private static void pushCart(Minecart cart, Vec3 goal) {
		Vec3 motion = cart.getDeltaMovement();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z);
		Vec3 direction;
		if (flat.length() < 0.05) {
			// Standing: push along the rails, towards the goal.
			Vec3 axis = railAxis(cart.level().getBlockState(cart.blockPosition()));
			if (axis == null) {
				return;
			}
			Vec3 toGoal = goal.subtract(cart.position());
			direction = axis.dot(toGoal) >= 0 ? axis : axis.reverse();
		} else {
			direction = flat.normalize();
		}
		if (flat.length() < CART_TOP_SPEED) {
			cart.setDeltaMovement(motion.add(direction.scale(CART_PUSH)));
		}
	}

	private static void paddle(AbstractBoat boat, Vec3 goal) {
		Vec3 toGoal = goal.subtract(boat.position());
		float wanted = (float) (Mth.atan2(toGoal.z, toGoal.x) * Mth.RAD_TO_DEG) - 90.0F;
		float difference = Mth.wrapDegrees(wanted - boat.getYRot());
		boat.setYRot(boat.getYRot() + Mth.clamp(difference, -BOAT_TURN, BOAT_TURN));
		if (Math.abs(difference) < 45.0F) {
			float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
			boat.setDeltaMovement(boat.getDeltaMovement().add(-Mth.sin(yaw) * BOAT_PADDLE, 0.0, Mth.cos(yaw) * BOAT_PADDLE));
		}
		boat.setPaddleState(true, true);
	}

	// --- finding a ride ---

	private @Nullable Entity findVehicle(ServerLevel level, CopperGolem golem, Vec3 goal) {
		long now = level.getGameTime();
		this.leftUntil.values().removeIf(until -> until <= now);
		this.deadEnds.values().removeIf(dead -> dead.until() <= now);
		Vec3 toGoal = goal.subtract(golem.position()).multiply(1.0, 0.0, 1.0).normalize();
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : level.getEntities(golem, golem.getBoundingBox().inflate(VEHICLE_RANGE), entity -> entity instanceof Minecart || entity instanceof AbstractBoat)) {
			DeadEnd dead = this.deadEnds.get(candidate.getUUID());
			if (candidate.isVehicle() || dead != null && dead.goal().distanceTo(goal) < SAME_GOAL || !leadsTowards(level, candidate, toGoal)) {
				continue;
			}
			double distance = golem.distanceToSqr(candidate);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** A cart on straight rails that run roughly towards the goal, or a boat with open water ahead in that direction. */
	private static boolean leadsTowards(ServerLevel level, Entity vehicle, Vec3 toGoal) {
		if (vehicle instanceof Minecart cart) {
			Vec3 axis = cart.isOnRails() ? railAxis(level.getBlockState(cart.blockPosition())) : null;
			return axis != null && Math.abs(axis.dot(toGoal)) > 0.6;
		}
		if (!vehicle.isInWater() && !level.getFluidState(vehicle.blockPosition().below()).is(FluidTags.WATER)) {
			return false;
		}
		int water = 0;
		for (int step = 3; step <= 12; step += 3) {
			BlockPos ahead = BlockPos.containing(vehicle.position().add(toGoal.scale(step)));
			if (level.getFluidState(ahead).is(FluidTags.WATER) || level.getFluidState(ahead.below()).is(FluidTags.WATER)) {
				water++;
			}
		}
		return water >= 3;
	}

	/** The direction straight rails run in, or null for curves and anything that is not a rail. */
	private static @Nullable Vec3 railAxis(BlockState state) {
		if (!(state.getBlock() instanceof BaseRailBlock rail)) {
			return null;
		}
		RailShape shape = state.getValue(rail.getShapeProperty());
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Vec3(0.0, 0.0, 1.0);
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Vec3(1.0, 0.0, 0.0);
			default -> null;
		};
	}

	private static @Nullable Vec3 walkGoal(CopperGolem golem) {
		return golem.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(WalkTarget::getTarget).map(target -> target.currentPosition()).orElse(null);
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
	}
}
