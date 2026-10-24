package dev.bettercoppers.job;

import com.google.common.collect.ImmutableMap;
import dev.bettercoppers.entity.CopperChestMinecart;
import dev.bettercoppers.home.Homes;
import dev.bettercoppers.golem.CartChaining;
import dev.bettercoppers.golem.Inventories;
import dev.bettercoppers.golem.TravelWatch;
import dev.bettercoppers.upkeep.Upkeep;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the forester, fisher, animal keeper and crafter have in common: a home copper chest, a pouch that is brought
 * home when full (or when there is nothing else to do), supplies restocked there, clumsy pauses, and the running costs
 * of {@link Upkeep}. Subclasses only decide what to do next and how to do it.
 *
 * @param <T> what a single task looks like for this job
 */
public abstract class JobBehavior<T> extends Behavior<CopperGolem> {
	protected static final int MAX_TRAVEL_TICKS = 300;
	private static final int HOME_INTERACTION_TICKS = 60;
	private static final int IDLE_COOLDOWN = 200;
	private static final int IGNORE_TICKS = 600;
	private static final float HESITATE_CHANCE = 0.08F;
	private static final float WALK_SPEED = 0.8F;

	protected final Job job;
	private @Nullable T task;
	private boolean returning;
	private boolean hesitated;
	private int pauseTicks;
	private int travelTicks;
	private final TravelWatch travel = new TravelWatch();
	private int homeTicks;
	/** Ticks spent on the current task once in reach; free for subclasses to use. */
	protected int workTicks;
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

	protected JobBehavior(Job job) {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
		this.job = job;
	}

	// --- what subclasses provide ---

	/** The next thing to do around {@code home}, or null. May call {@link #requestReturn} instead. */
	protected abstract @Nullable T chooseTask(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos home);

	/** Works on the task; returns true when it is finished (successfully or not). */
	protected abstract boolean tickTask(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos home, T task);

	/** How many of this unloaded stack to keep in the supplies instead of putting it in the chest. */
	protected int keepOnUnload(WorkerData data, ItemStack stack) {
		return 0;
	}

	/** Takes what the job needs from the home chest. */
	protected void restock(ServerLevel level, CopperGolem golem, WorkerData data, Container chest) {
	}

	/** Pouch size before going home. */
	protected int pouchLimit() {
		return 64;
	}

	// --- conditions ---

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return this.hasWork(golem) && Upkeep.canWork(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty()
			&& !golem.isPanicking()
			&& this.hasWork(golem)
			&& Upkeep.canWork(level, golem);
	}

	private boolean hasWork(CopperGolem golem) {
		WorkerData data = WorkerData.get(golem);
		if (golem.isLeashed() || data == null || Homes.get(golem) == null) {
			return false;
		}
		ItemStack tool = golem.getMainHandItem();
		if (this.job.isTool(tool)) {
			return true;
		}
		// Tool taken away: one of the job behaviours (the first) still brings the pouch home.
		return this.job == Job.values()[0] && Job.forTool(tool) == null && data.pouchCount() > 0;
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.task = null;
		this.returning = false;
		this.homeTicks = 0;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	// --- main loop ---

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		if (this.pauseTicks > 0) {
			if (--this.pauseTicks == 0) {
				golem.setState(CopperGolemState.IDLE);
			}
			return;
		}
		WorkerData data = WorkerData.get(golem);
		if (data == null) {
			return;
		}
		BlockPos home = Homes.valid(level, golem);
		if (home == null) {
			this.idle(golem);
			return;
		}
		if (this.returning) {
			this.tickReturn(level, golem, data, home);
			return;
		}
		if (!this.job.isTool(golem.getMainHandItem())) {
			this.requestReturn();
			return;
		}
		if (this.task == null) {
			if (this.isFull(golem, data)) {
				this.requestReturn();
				return;
			}
			this.task = this.chooseTask(level, golem, data, home);
			this.travelTicks = 0;
			this.travel.reset();
			this.workTicks = 0;
			this.hesitated = false;
			if (this.returning) {
				this.task = null;
				return;
			}
			if (this.task == null) {
				CopperChestMinecart cart = CartChaining.findCart(golem);
				if (data.pouchCount() > 0 || cart != null && !cart.isEmpty()) {
					this.requestReturn();
				} else {
					this.idle(golem);
				}
				return;
			}
		}
		T current = this.task;
		if (this.tickTask(level, golem, data, home, current) && this.task == current) {
			this.task = null;
			golem.setState(CopperGolemState.IDLE);
			this.pauseTicks = 5;
		}
	}

	// --- helpers for subclasses ---

	/** Walks towards {@code pos} until in reach. Gives up (and skips that spot for a while) after some time. */
	protected boolean reach(ServerLevel level, CopperGolem golem, BlockPos pos) {
		if (isInReach(golem, pos)) {
			stopInPlace(golem);
			golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
			return true;
		}
		if (this.travel.tick(golem, Vec3.atCenterOf(pos)) > MAX_TRAVEL_TICKS) {
			this.ignore(level, pos);
			this.task = null;
			return false;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, pos, WALK_SPEED * Upkeep.speed(golem), 1);
		return false;
	}

	/** Like {@link #reach(ServerLevel, CopperGolem, BlockPos)} for a moving target. */
	protected boolean reach(ServerLevel level, CopperGolem golem, Entity target) {
		if (golem.distanceToSqr(target) < 2.5 * 2.5) {
			stopInPlace(golem);
			golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
			return true;
		}
		if (this.travel.tick(golem, target.position()) > MAX_TRAVEL_TICKS) {
			this.task = null;
			return false;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, target, WALK_SPEED * Upkeep.speed(golem), 1);
		return false;
	}

	/** Now and then the golem stops, shakes its head and only then gets on with it. Call once a task is in reach. */
	protected boolean hesitate(CopperGolem golem) {
		if (this.hesitated || golem.getRandom().nextFloat() >= HESITATE_CHANCE) {
			this.hesitated = true;
			return false;
		}
		this.hesitated = true;
		golem.setState(CopperGolemState.GETTING_NO_ITEM);
		golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
		this.pauseTicks = 30 + golem.getRandom().nextInt(30);
		return true;
	}

	/** Work durations stretched by rust. */
	protected static int slowed(CopperGolem golem, int ticks) {
		return Math.round(ticks / Upkeep.speed(golem));
	}

	protected void pause(int ticks) {
		this.pauseTicks = ticks;
	}

	/** Ticks in a row the golem walked towards {@code target} without getting closer. */
	protected int stalledTowards(CopperGolem golem, Vec3 target) {
		return this.travel.tick(golem, target);
	}

	/** Drops the current task; the next tick picks a new one. */
	protected void giveUp() {
		this.task = null;
	}

	protected void requestReturn() {
		this.returning = true;
		this.task = null;
		this.homeTicks = 0;
	}

	protected void ignore(ServerLevel level, BlockPos pos) {
		this.ignoredUntil.put(pos, level.getGameTime() + IGNORE_TICKS);
	}

	protected boolean isIgnored(ServerLevel level, BlockPos pos) {
		Long until = this.ignoredUntil.get(pos);
		if (until != null && until <= level.getGameTime()) {
			this.ignoredUntil.remove(pos);
			return false;
		}
		return until != null;
	}

	/** Puts something the golem got into the chained cart, or else its pouch. */
	protected static void stash(CopperGolem golem, WorkerData data, ItemStack stack) {
		ItemStack rest = stack;
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			rest = Inventories.insert(cart, rest);
		}
		if (!rest.isEmpty()) {
			WorkerData.addTo(data.pouch(), rest);
		}
	}

	private boolean isFull(CopperGolem golem, WorkerData data) {
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return (cart == null || !Inventories.hasEmptySlot(cart)) && data.pouchCount() >= this.pouchLimit();
	}

	private void idle(CopperGolem golem) {
		this.task = null;
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, IDLE_COOLDOWN);
	}

	// --- home ---

	private void tickReturn(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos home) {
		Container chest = homeContainer(level, home);
		if (chest == null) {
			this.returning = false;
			return;
		}
		if (!isInReach(golem, home)) {
			BehaviorUtils.setWalkAndLookTargetMemories(golem, home, WALK_SPEED * Upkeep.speed(golem), 1);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(home));
		this.homeTicks++;
		if (this.homeTicks == 1) {
			chest.startOpen(golem);
			golem.setOpenedChestPos(home);
			golem.setState(CopperGolemState.DROPPING_ITEM);
		}
		if (this.homeTicks == 9) {
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP);
		}
		if (this.homeTicks >= HOME_INTERACTION_TICKS) {
			this.unload(level, golem, data, chest);
			if (this.job.isTool(golem.getMainHandItem())) {
				this.restock(level, golem, data, chest);
			}
			if (chest.getEntitiesWithContainerOpen().contains(golem)) {
				chest.stopOpen(golem);
			}
			golem.clearOpenedChestPos();
			golem.setState(CopperGolemState.IDLE);
			this.returning = false;
			this.homeTicks = 0;
			if (!this.job.isTool(golem.getMainHandItem())) {
				this.idle(golem);
			}
		}
	}

	private void unload(ServerLevel level, CopperGolem golem, WorkerData data, Container chest) {
		for (ItemStack stack : data.pouch()) {
			int keep = Math.min(stack.getCount(), this.keepOnUnload(data, stack));
			if (keep > 0) {
				WorkerData.addTo(data.supplies(), stack.split(keep));
			}
			ItemStack rest = Inventories.insert(chest, stack);
			if (!rest.isEmpty()) {
				golem.spawnAtLocation(level, rest);
			}
		}
		data.pouch().clear();
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			for (int slot = 0; slot < cart.getContainerSize(); slot++) {
				ItemStack stack = cart.getItem(slot);
				if (!stack.isEmpty()) {
					cart.setItem(slot, Inventories.insert(chest, stack));
				}
			}
		}
	}

	protected static @Nullable Container homeContainer(ServerLevel level, BlockPos pos) {
		return Homes.container(level, pos);
	}

	protected static boolean isInReach(CopperGolem golem, BlockPos pos) {
		return new AABB(pos).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox());
	}

	protected static void stopInPlace(CopperGolem golem) {
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.setXxa(0.0F);
		golem.setYya(0.0F);
		golem.setSpeed(0.0F);
		golem.setDeltaMovement(0.0, golem.getDeltaMovement().y, 0.0);
	}
}
