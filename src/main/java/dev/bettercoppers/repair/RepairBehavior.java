package dev.bettercoppers.repair;

import com.google.common.collect.ImmutableMap;
import dev.bettercoppers.courier.CourierRoute;
import dev.bettercoppers.courier.Couriers;
import dev.bettercoppers.golem.TravelWatch;
import dev.bettercoppers.home.Homes;
import dev.bettercoppers.registry.ModAttachments;
import dev.bettercoppers.upkeep.Upkeep;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Any copper golem with a home (or a courier route) looks after the iron golems around it: when one is badly hurt, it
 * drops what it is doing, takes iron ingots from its home chest (or the nearest chest or barrel that has some) and
 * patches the iron golem up, 25 health per ingot, like a player would. Only one copper golem tends each iron golem.
 */
public class RepairBehavior extends Behavior<CopperGolem> {
	private static final double PATIENT_RANGE = 16.0;
	private static final int CHEST_RANGE = 16;
	private static final float HEAL_PER_INGOT = 25.0F;
	/** Repairs start once an iron golem is down a whole ingot's worth (and a bit). */
	private static final float HURT_THRESHOLD = 30.0F;
	private static final int MAX_INGOTS_PER_TRIP = 4;
	private static final int SEARCH_INTERVAL = 40;
	private static final int CHEST_TICKS = 30;
	private static final int REPAIR_TICKS = 25;
	private static final int MAX_STALL_TICKS = 300;

	/** Which copper golem is looking after which iron golem. */
	private static final Map<UUID, UUID> CLAIMS = new HashMap<>();

	private @Nullable IronGolem patient;
	private @Nullable BlockPos supply;
	private int ticks;
	private final TravelWatch travel = new TravelWatch();

	public RepairBehavior() {
		super(ImmutableMap.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		if ((level.getGameTime() + golem.getId()) % SEARCH_INTERVAL != 0 || golem.isLeashed() || golem.isPassenger() || !hasPlace(golem)) {
			return false;
		}
		// A sorter or courier finishes its delivery first; the hand of a job golem only holds its tool.
		if (!golem.getMainHandItem().isEmpty() && !Upkeep.isJobGolem(golem)) {
			return false;
		}
		IronGolem found = findPatient(level, golem);
		if (found == null) {
			return false;
		}
		BlockPos chest = carried(golem) > 0 ? null : findSupply(level, golem);
		if (carried(golem) == 0 && chest == null) {
			return false;
		}
		this.patient = found;
		this.supply = chest;
		return true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return this.patient != null && !golem.isPanicking() && !golem.isLeashed() && needsRepair(this.patient);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		Errands.setBusy(golem, true);
		CLAIMS.put(this.patient.getUUID(), golem.getUUID());
		this.ticks = 0;
		this.travel.reset();
		golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		Errands.setBusy(golem, false);
		if (this.patient != null) {
			CLAIMS.remove(this.patient.getUUID(), golem.getUUID());
		}
		this.closeChest(level, golem);
		this.patient = null;
		this.supply = null;
		golem.setState(CopperGolemState.IDLE);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		IronGolem target = this.patient;
		if (target == null) {
			return;
		}
		if (carried(golem) == 0) {
			this.tickFetch(level, golem, target);
		} else {
			this.tickRepair(level, golem, target);
		}
	}

	// --- fetching iron ---

	private void tickFetch(ServerLevel level, CopperGolem golem, IronGolem target) {
		if (this.supply == null) {
			this.supply = findSupply(level, golem);
			if (this.supply == null) {
				this.patient = null;
				return;
			}
		}
		Container chest = Couriers.container(level, this.supply);
		if (chest == null) {
			this.supply = null;
			return;
		}
		if (!isInReach(golem, this.supply)) {
			this.walkTo(golem, Vec3.atCenterOf(this.supply), this.supply);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.supply));
		this.ticks++;
		if (this.ticks == 1) {
			chest.startOpen(golem);
			golem.setOpenedChestPos(this.supply);
			golem.setState(CopperGolemState.GETTING_ITEM);
		}
		if (this.ticks == 9) {
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
		}
		if (this.ticks < CHEST_TICKS) {
			return;
		}
		// Rounded, matching when repairs stop (half an ingot's worth of damage left), so nothing is left over.
		int wanted = Mth.clamp(Math.round((target.getMaxHealth() - target.getHealth()) / HEAL_PER_INGOT), 1, MAX_INGOTS_PER_TRIP);
		int taken = 0;
		for (int slot = 0; slot < chest.getContainerSize() && taken < wanted; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.is(Items.IRON_INGOT)) {
				int amount = Math.min(stack.getCount(), wanted - taken);
				stack.shrink(amount);
				taken += amount;
			}
		}
		chest.setChanged();
		this.closeChest(level, golem);
		setCarried(golem, taken);
		this.ticks = 0;
		this.travel.reset();
		if (taken == 0) {
			this.supply = null;
		}
	}

	// --- repairing ---

	private void tickRepair(ServerLevel level, CopperGolem golem, IronGolem target) {
		if (golem.distanceToSqr(target) > 2.5 * 2.5) {
			this.ticks = 0;
			if (this.travel.tick(golem, target.position()) > MAX_STALL_TICKS) {
				this.patient = null;
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, target, Upkeep.speed(golem), 1);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		this.ticks++;
		if (this.ticks == 1) {
			golem.setState(CopperGolemState.DROPPING_ITEM);
		}
		if (this.ticks < REPAIR_TICKS) {
			return;
		}
		target.heal(HEAL_PER_INGOT);
		setCarried(golem, carried(golem) - 1);
		float pitch = 1.0F + (golem.getRandom().nextFloat() - golem.getRandom().nextFloat()) * 0.2F;
		level.playSound(null, target, SoundEvents.IRON_GOLEM_REPAIR, target.getSoundSource(), 1.0F, pitch);
		level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), target.getY() + 1.4, target.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
		golem.setState(CopperGolemState.IDLE);
		this.ticks = 0;
	}

	private void walkTo(CopperGolem golem, Vec3 goal, BlockPos block) {
		if (this.travel.tick(golem, goal) > MAX_STALL_TICKS) {
			// Cannot get there; leave it to another golem, or to the next try.
			this.patient = null;
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, block, Upkeep.speed(golem), 1);
	}

	private void closeChest(ServerLevel level, CopperGolem golem) {
		if (this.supply != null) {
			Container chest = Couriers.container(level, this.supply);
			if (chest != null && chest.getEntitiesWithContainerOpen().contains(golem)) {
				chest.stopOpen(golem);
			}
		}
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
	}

	// --- finding things ---

	/** Golems that belong somewhere look after the iron golems there; strays do not. */
	private static boolean hasPlace(CopperGolem golem) {
		return Homes.get(golem) != null || Couriers.isCourier(golem);
	}

	private static boolean needsRepair(IronGolem golem) {
		return golem.isAlive() && golem.getMaxHealth() - golem.getHealth() >= HEAL_PER_INGOT * 0.5F;
	}

	private static @Nullable IronGolem findPatient(ServerLevel level, CopperGolem golem) {
		IronGolem best = null;
		double bestDistance = Double.MAX_VALUE;
		for (IronGolem candidate : level.getEntitiesOfClass(IronGolem.class, golem.getBoundingBox().inflate(PATIENT_RANGE))) {
			if (!candidate.isAlive() || candidate.getMaxHealth() - candidate.getHealth() < HURT_THRESHOLD || isClaimedByOther(level, candidate, golem)) {
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

	private static boolean isClaimedByOther(ServerLevel level, IronGolem patient, CopperGolem golem) {
		UUID claimant = CLAIMS.get(patient.getUUID());
		if (claimant == null || claimant.equals(golem.getUUID())) {
			return false;
		}
		if (level.getEntity(claimant) instanceof CopperGolem other && other.isAlive() && Errands.isBusy(other)) {
			return true;
		}
		CLAIMS.remove(patient.getUUID());
		return false;
	}

	/** The home chest (or a courier's pick-up chest) if it has iron, else the nearest chest or barrel nearby that does. */
	private static @Nullable BlockPos findSupply(ServerLevel level, CopperGolem golem) {
		BlockPos home = Homes.get(golem);
		if (hasIron(level, home)) {
			return home;
		}
		CourierRoute route = Couriers.route(golem);
		if (route != null && hasIron(level, route.from())) {
			return route.from();
		}
		AABB area = new AABB(golem.blockPosition()).inflate(CHEST_RANGE, 6, CHEST_RANGE);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ChunkPos chunkPos : ChunkPos.rangeClosed(ChunkPos.containing(golem.blockPosition()), CHEST_RANGE / 16 + 1).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk == null) {
				continue;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				BlockPos pos = blockEntity.getBlockPos();
				double distance = pos.distToCenterSqr(golem.position());
				if (distance < bestDistance && area.contains(pos.getX(), pos.getY(), pos.getZ()) && hasIron(level, pos)) {
					best = pos;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private static boolean hasIron(ServerLevel level, @Nullable BlockPos pos) {
		if (pos == null) {
			return false;
		}
		Container container = Couriers.container(level, pos);
		if (container == null) {
			return false;
		}
		for (ItemStack stack : container) {
			if (stack.is(Items.IRON_INGOT)) {
				return true;
			}
		}
		return false;
	}

	// --- carried iron ---

	public static int carried(CopperGolem golem) {
		Integer count = golem.getAttached(ModAttachments.CARRIED_IRON);
		return count == null ? 0 : count;
	}

	private static void setCarried(CopperGolem golem, int count) {
		if (count <= 0) {
			golem.removeAttached(ModAttachments.CARRIED_IRON);
		} else {
			golem.setAttached(ModAttachments.CARRIED_IRON, count);
		}
	}

	/** Drops the iron a golem was carrying (on death or when it turns into a statue). */
	public static void dropCarried(ServerLevel level, CopperGolem golem) {
		int count = carried(golem);
		if (count > 0) {
			golem.spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, count));
			setCarried(golem, 0);
		}
	}

	private static boolean isInReach(CopperGolem golem, BlockPos pos) {
		return new AABB(pos).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox());
	}

	private static void stopInPlace(CopperGolem golem) {
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.setXxa(0.0F);
		golem.setZza(0.0F);
		golem.setSpeed(0.0F);
		golem.setDeltaMovement(0.0, golem.getDeltaMovement().y, 0.0);
	}
}
