package dev.bettercoppers.courier;

import com.google.common.collect.ImmutableMap;
import dev.bettercoppers.entity.CopperChestMinecart;
import dev.bettercoppers.golem.CartChaining;
import dev.bettercoppers.golem.Inventories;
import dev.bettercoppers.golem.TravelWatch;
import dev.bettercoppers.repair.Errands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A courier walks to the first chest of its route, takes what it can carry (16 items, or a whole cart load with a
 * chained cart), walks to the second chest however far away it is, and puts everything in. While it travels it keeps
 * its chunks loaded. Stuck somewhere, it shakes its head, wanders off a few blocks and tries again; it never gives up.
 */
public class CourierBehavior extends Behavior<CopperGolem> {
	private static final int HAND_LIMIT = 16;
	private static final int INTERACTION_TICKS = 60;
	private static final int WAIT_TICKS = 200;
	private static final int STALL_TICKS = 200;
	private static final int DETOUR_TICKS = 100;
	/** Beyond this, a waiting courier still keeps its chunks loaded so it does not freeze on the way. */
	private static final double NEAR = 12.0;

	private final TravelWatch travel = new TravelWatch();
	private int interactionTicks;
	private @Nullable Vec3 detour;
	private int detourTicks;
	private int waitTicks;

	public CourierBehavior() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return Couriers.isCourier(golem) && !golem.isLeashed() && !Errands.isBusy(golem);
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
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.endInteraction(level, golem);
		this.travel.reset();
		this.detour = null;
		this.waitTicks = 0;
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		CourierRoute route = Couriers.route(golem);
		if (route == null) {
			return;
		}
		boolean carrying = isCarrying(golem);
		BlockPos target = carrying ? route.to() : route.from();
		if (carrying || golem.position().distanceTo(Vec3.atCenterOf(target)) > NEAR) {
			if (level.getGameTime() % 10 == 0) {
				Couriers.keepLoaded(level, golem);
			}
		}

		if (this.waitTicks > 0) {
			// Waiting happens here rather than on a brain cooldown, so the chunk ticket above stays fresh.
			if (--this.waitTicks == 0) {
				golem.setState(CopperGolemState.IDLE);
			}
			return;
		}
		if (this.detour != null) {
			this.tickDetour(golem);
			return;
		}
		if (!isInReach(golem, target)) {
			this.endInteraction(level, golem);
			if (this.travel.tick(golem, Vec3.atCenterOf(target)) > STALL_TICKS) {
				this.startDetour(golem);
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, target, 1.0F, 1);
			return;
		}

		Container container = Couriers.container(level, target);
		if (container == null) {
			// The chest is gone: nothing to do until the player fixes the route.
			this.waitAWhile(golem, CopperGolemState.GETTING_NO_ITEM);
			return;
		}
		this.travel.reset();
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		this.interactionTicks++;
		if (this.interactionTicks == 1) {
			container.startOpen(golem);
			golem.setOpenedChestPos(target);
			golem.setState(carrying ? CopperGolemState.DROPPING_ITEM : container.isEmpty() ? CopperGolemState.GETTING_NO_ITEM : CopperGolemState.GETTING_ITEM);
		}
		if (this.interactionTicks == 9) {
			golem.playSound(carrying ? SoundEvents.COPPER_GOLEM_ITEM_DROP : container.isEmpty() ? SoundEvents.COPPER_GOLEM_ITEM_NO_GET : SoundEvents.COPPER_GOLEM_ITEM_GET);
		}
		if (this.interactionTicks < INTERACTION_TICKS) {
			return;
		}
		if (carrying) {
			unload(golem, container);
		} else {
			load(golem, container);
		}
		container.setChanged();
		this.endInteraction(level, golem);
		if (carrying && isCarrying(golem)) {
			// The far chest is full: wait there and try again.
			this.waitAWhile(golem, CopperGolemState.DROPPING_NO_ITEM);
		} else if (!carrying && !isCarrying(golem)) {
			// Nothing to fetch yet.
			this.waitAWhile(golem, CopperGolemState.IDLE);
		}
	}

	private static void load(CopperGolem golem, Container from) {
		ItemStack hand = Inventories.takeFirst(from, HAND_LIMIT, stack -> true);
		golem.setItemSlot(EquipmentSlot.MAINHAND, hand);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			for (int slot = 0; slot < from.getContainerSize() && Inventories.hasEmptySlot(cart); slot++) {
				ItemStack stack = from.getItem(slot);
				if (!stack.isEmpty()) {
					from.setItem(slot, Inventories.insert(cart, stack));
				}
			}
			cart.setChanged();
		}
	}

	private static void unload(CopperGolem golem, Container to) {
		golem.setItemSlot(EquipmentSlot.MAINHAND, Inventories.insert(to, golem.getMainHandItem()));
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			for (int slot = 0; slot < cart.getContainerSize(); slot++) {
				ItemStack stack = cart.getItem(slot);
				if (!stack.isEmpty()) {
					cart.setItem(slot, Inventories.insert(to, stack));
				}
			}
			cart.setChanged();
		}
	}

	private static boolean isCarrying(CopperGolem golem) {
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return !golem.getMainHandItem().isEmpty() || cart != null && !cart.isEmpty();
	}

	private void waitAWhile(CopperGolem golem, CopperGolemState state) {
		golem.setState(state);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		this.waitTicks = WAIT_TICKS;
	}

	private void startDetour(CopperGolem golem) {
		this.travel.reset();
		golem.setState(CopperGolemState.GETTING_NO_ITEM);
		golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
		this.detour = LandRandomPos.getPos(golem, 10, 7);
		this.detourTicks = 0;
	}

	private void tickDetour(CopperGolem golem) {
		if (++this.detourTicks > DETOUR_TICKS || this.detour == null || golem.position().distanceTo(this.detour) < 1.5) {
			this.detour = null;
			golem.setState(CopperGolemState.IDLE);
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, BlockPos.containing(this.detour), 1.0F, 0);
	}

	private void endInteraction(ServerLevel level, CopperGolem golem) {
		if (this.interactionTicks == 0) {
			return;
		}
		this.interactionTicks = 0;
		CourierRoute route = Couriers.route(golem);
		if (route != null) {
			for (BlockPos pos : new BlockPos[] {route.from(), route.to()}) {
				Container container = Couriers.container(level, pos);
				if (container != null && container.getEntitiesWithContainerOpen().contains(golem)) {
					container.stopOpen(golem);
				}
			}
		}
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
	}

	private static boolean isInReach(CopperGolem golem, BlockPos pos) {
		return new AABB(pos).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox());
	}
}
