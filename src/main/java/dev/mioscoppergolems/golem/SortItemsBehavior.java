package dev.mioscoppergolems.golem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.courier.CourierBehavior;
import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.farming.FarmBehavior;
import dev.mioscoppergolems.farming.FarmerData;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.job.CraftBehavior;
import dev.mioscoppergolems.job.FishBehavior;
import dev.mioscoppergolems.job.ForestBehavior;
import dev.mioscoppergolems.job.KeeperBehavior;
import dev.mioscoppergolems.job.ToolFetchBehavior;
import dev.mioscoppergolems.mining.MineBehavior;
import dev.mioscoppergolems.mining.MinerData;
import dev.mioscoppergolems.repair.Errands;
import dev.mioscoppergolems.repair.RepairBehavior;
import dev.mioscoppergolems.travel.RideBehavior;
import dev.mioscoppergolems.upkeep.ChargeBehavior;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the vanilla "open every chest until one fits" search. The golem scans all containers in range and knows
 * where an item belongs: a chest that already holds it, else any barrel with room, else an empty chest. To keep some
 * charm it still opens 0-3 wrong containers per trip before the right one.
 * <p>
 * With a {@link CopperChestMinecart} chained to it, the golem also loads every item related to the one it picked
 * (see {@link ItemFamilies}) into the cart and delivers the whole load before returning to the copper chest.
 */
public class SortItemsBehavior extends Behavior<CopperGolem> {
	private static final int HORIZONTAL_RANGE = 32;
	private static final int VERTICAL_RANGE = 8;
	private static final int HAND_LIMIT = 16;
	/** Seeds left in a farmer's home chest for replanting. */
	private static final int SEED_RESERVE = 16;
	private static final int INTERACTION_TICKS = 60;
	private static final int SOUND_TICK = 9;
	private static final int IDLE_COOLDOWN = 140;
	private static final int MAX_TRAVEL_TICKS = 600;
	/** How long an unreachable or full container is skipped. */
	private static final int IGNORE_TICKS = 600;
	private static final int MAX_DECOYS = 3;
	/** Decoys are picked from this many containers closest to the real destination. */
	private static final int DECOY_CANDIDATES = 6;
	private static final int MAX_PATH_ATTEMPTS = 4;
	private static final double QUEUE_DISTANCE = 3.0;
	private static final double KEEP_INTERACTING_DISTANCE = 2.0;

	private enum Kind {
		SOURCE,
		CHEST,
		BARREL
	}

	private enum Purpose {
		PICKUP,
		DECOY,
		DELIVER
	}

	/**
	 * Where an item may go, in order of preference. A chest or barrel with item frames on it is reserved: it takes the
	 * items its frames show, and nothing else.
	 */
	private enum Tier {
		FRAMED,
		MATCHING_CHEST,
		BARREL,
		EMPTY_CHEST;

		boolean accepts(Spot spot, ItemStack item) {
			if (this == FRAMED) {
				return spot.kind != Kind.SOURCE && showsItem(spot.frames, item) && hasRoom(spot.container, item);
			}
			if (!spot.frames.isEmpty()) {
				return false;
			}
			return switch (this) {
				case MATCHING_CHEST -> spot.kind == Kind.CHEST && containsItem(spot.container, item) && hasRoom(spot.container, item);
				case BARREL -> spot.kind == Kind.BARREL && hasRoom(spot.container, item);
				case EMPTY_CHEST -> spot.kind == Kind.CHEST && spot.container.isEmpty();
				case FRAMED -> false;
			};
		}
	}

	/** {@code frames}: the items shown in item frames hung on this container (either half of a double chest). */
	private record Spot(BlockPos pos, Kind kind, Container container, List<ItemStack> frames) {
	}

	private @Nullable BlockPos target;
	private Purpose purpose = Purpose.PICKUP;
	private Tier tier = Tier.MATCHING_CHEST;
	/** What the frames on the current delivery target show. */
	private List<ItemStack> targetFrames = List.of();
	private boolean interacting;
	private int ticks;
	private final ArrayDeque<BlockPos> decoys = new ArrayDeque<>();
	private int decoyBudget;
	private boolean deliveryPrepared;
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();
	private Set<BlockPos> minerHomes = Set.of();
	private Set<BlockPos> farmerHomes = Set.of();

	public SortItemsBehavior() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	/**
	 * Vanilla's idle activity with the transport behaviour swapped for this one, plus every job behaviour, the ones
	 * add-ons registered included.
	 */
	public static ActivityData<CopperGolem> createIdleActivity() {
		List<Pair<Integer, ? extends BehaviorControl<? super CopperGolem>>> behaviors = new ArrayList<>(
			List.of(
				Pair.of(0, new RepairBehavior()),
				Pair.of(0, new SortItemsBehavior()),
				Pair.of(0, new CourierBehavior()),
				Pair.of(0, new MineBehavior()),
				Pair.of(0, new ChargeBehavior()),
				Pair.of(0, new FarmBehavior()),
				Pair.of(0, new ForestBehavior()),
				Pair.of(0, new FishBehavior()),
				Pair.of(0, new KeeperBehavior()),
				Pair.of(0, new CraftBehavior()),
				Pair.of(0, new ToolFetchBehavior())
			)
		);
		MiosCoppergolemsApi.behaviors().forEach(factory -> behaviors.add(Pair.of(0, factory.get())));
		// Last, so it sees the walk target the job behaviours just set.
		behaviors.add(Pair.of(0, new RideBehavior()));
		behaviors.add(Pair.of(1, SetEntityLookTargetSometimes.create(EntityTypes.PLAYER, 6.0F, UniformInt.of(40, 80))));
		behaviors.add(
			Pair.of(
				2,
				new RunOne<>(
					ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT),
					ImmutableList.of(Pair.of(new HomeStroll(), 1), Pair.of(new DoNothing(30, 60), 1))
				)
			)
		);
		return ActivityData.create(Activity.IDLE, ImmutableList.copyOf(behaviors));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		if (Couriers.isCourier(golem) || MiosCoppergolemsApi.jobOf(golem) != null) {
			// Their own behaviours decide when they rest; the idle cooldown below would stop them.
			return false;
		}
		if (!Upkeep.hasJob(golem) || Homes.get(golem) == null) {
			// Nothing to do (no job, or no home yet): wander about like a vanilla golem between jobs.
			if (golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty()) {
				golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, IDLE_COOLDOWN);
			}
			return false;
		}
		return !golem.isLeashed() && isActiveSorter(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !golem.isPanicking() && !golem.isLeashed() && isActiveSorter(level, golem);
	}

	/** Holds a bundle, has a home, and has no other job. */
	private static boolean isActiveSorter(ServerLevel level, CopperGolem golem) {
		return Sorter.isSorter(golem) && !Upkeep.isJobGolem(golem) && !Couriers.isCourier(golem) && !Errands.isBusy(golem) && Homes.valid(level, golem) != null;
	}

	/** A sorter only empties its own copper chest (either half, if it is a double chest). */
	private static boolean isOwnChest(ServerLevel level, CopperGolem golem, BlockPos pos) {
		BlockPos home = Homes.get(golem);
		return home != null && sameContainer(level, home).contains(pos);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		if (golem.getNavigation() instanceof GroundPathNavigation navigation) {
			navigation.setCanPathToTargetsBelowSurface(true);
		}
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.dropTarget(golem);
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		if (golem.getNavigation() instanceof GroundPathNavigation navigation) {
			navigation.setCanPathToTargetsBelowSurface(false);
		}
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		long now = level.getGameTime();
		if (this.target == null && !this.chooseTarget(level, golem, now)) {
			this.dropTarget(golem);
			golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, IDLE_COOLDOWN);
			return;
		}
		Container container = containerAt(level, this.target);
		if (container == null) {
			this.endInteraction(golem);
			this.dropTarget(golem);
		} else if (this.interacting) {
			this.tickInteraction(level, golem, container, now);
		} else {
			this.tickTravel(level, golem, container, now);
		}
	}

	// --- planning ---

	private boolean chooseTarget(ServerLevel level, CopperGolem golem, long now) {
		CopperChestMinecart cart = CartChaining.findCart(golem);
		ItemStack hand = golem.getMainHandItem();
		if (hand.isEmpty() && cart != null && !cart.isEmpty()) {
			hand = takeFirst(cart, MiosCoppergolemsApi.carryLimit(golem, HAND_LIMIT));
			this.setHandItem(golem, hand);
		}

		List<Spot> spots = this.scan(level, golem, now);
		if (hand.isEmpty()) {
			this.decoys.clear();
			this.deliveryPrepared = false;
			this.minerHomes = MineBehavior.minerHomesNear(level, golem.blockPosition());
			this.farmerHomes = FarmBehavior.farmerHomesNear(level, golem.blockPosition());
			return this.targetNearestReachable(
				level, golem, now, spots, spot -> spot.kind == Kind.SOURCE && isOwnChest(level, golem, spot.pos) && Inventories.contains(spot.container, this.sortableIn(spot.pos, spot.container)), Purpose.PICKUP
			);
		}

		ItemStack item = hand;
		for (Tier tier : Tier.values()) {
			Spot destination = nearest(golem, spots, spot -> tier.accepts(spot, item));
			if (destination == null) {
				continue;
			}
			if (!this.deliveryPrepared) {
				this.deliveryPrepared = true;
				this.planDecoys(level.getRandom(), golem, spots, destination, item, cart != null && !cart.isEmpty());
			}
			while (!this.decoys.isEmpty()) {
				BlockPos decoy = this.decoys.poll();
				if (containerAt(level, decoy) != null && this.canReach(level, golem, decoy)) {
					this.setTarget(golem, decoy, Purpose.DECOY);
					return true;
				}
			}
			this.tier = tier;
			boolean found = this.targetNearestReachable(level, golem, now, spots, spot -> tier.accepts(spot, item), Purpose.DELIVER);
			BlockPos target = this.target;
			this.targetFrames = spots.stream().filter(spot -> spot.pos.equals(target)).findFirst().map(Spot::frames).orElse(List.of());
			return found;
		}
		return false;
	}

	/**
	 * Spreads the trip's decoy budget over its deliveries. The last delivery of a trip uses up whatever is left.
	 */
	private void planDecoys(RandomSource random, CopperGolem golem, List<Spot> spots, Spot destination, ItemStack item, boolean moreDeliveriesAfter) {
		this.decoys.clear();
		int count = moreDeliveriesAfter ? random.nextInt(this.decoyBudget + 1) : this.decoyBudget;
		this.decoyBudget -= count;
		if (count == 0) {
			return;
		}

		Set<BlockPos> excluded = new HashSet<>(sameContainer(golem.level(), destination.pos));
		List<Spot> candidates = new ArrayList<>();
		for (Spot spot : spots) {
			if (spot.kind != Kind.SOURCE && !excluded.contains(spot.pos) && !containsItem(spot.container, item)) {
				candidates.add(spot);
			}
		}
		candidates.sort(Comparator.comparingDouble(spot -> spot.pos.distSqr(destination.pos)));
		List<Spot> pool = new ArrayList<>(candidates.subList(0, Math.min(DECOY_CANDIDATES, candidates.size())));
		Util.shuffle(pool, random);

		List<BlockPos> chosen = new ArrayList<>();
		for (Spot spot : pool) {
			if (chosen.size() >= count) {
				break;
			}
			if (excluded.contains(spot.pos)) {
				continue;
			}
			chosen.add(spot.pos);
			excluded.addAll(sameContainer(golem.level(), spot.pos));
		}
		chosen.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(golem.position())));
		this.decoys.addAll(chosen);
	}

	private boolean targetNearestReachable(ServerLevel level, CopperGolem golem, long now, List<Spot> spots, Predicate<Spot> filter, Purpose purpose) {
		List<Spot> candidates = new ArrayList<>(spots.stream().filter(filter).toList());
		candidates.sort(Comparator.comparingDouble(spot -> spot.pos.distToCenterSqr(golem.position())));
		for (int i = 0; i < Math.min(MAX_PATH_ATTEMPTS, candidates.size()); i++) {
			BlockPos pos = candidates.get(i).pos;
			if (this.canReach(level, golem, pos)) {
				this.setTarget(golem, pos, purpose);
				return true;
			}
			this.ignore(pos, now);
		}
		return false;
	}

	private void setTarget(CopperGolem golem, BlockPos pos, Purpose purpose) {
		this.target = pos;
		this.purpose = purpose;
		this.interacting = false;
		this.ticks = 0;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
	}

	private void dropTarget(CopperGolem golem) {
		this.target = null;
		this.interacting = false;
		this.ticks = 0;
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private void ignore(BlockPos pos, long now) {
		this.ignoredUntil.put(pos, now + IGNORE_TICKS);
	}

	private boolean isIgnored(BlockPos pos, long now) {
		Long until = this.ignoredUntil.get(pos);
		if (until == null) {
			return false;
		}
		if (until <= now) {
			this.ignoredUntil.remove(pos);
			return false;
		}
		return true;
	}

	// --- moving and interacting ---

	private void tickTravel(ServerLevel level, CopperGolem golem, Container container, long now) {
		this.ticks++;
		if (this.ticks > MAX_TRAVEL_TICKS) {
			this.ignore(this.target, now);
			this.dropTarget(golem);
			return;
		}
		if (isWithinReach(level, golem, this.target, QUEUE_DISTANCE) && isOpenedByOthers(container, golem)) {
			stopInPlace(golem);
		} else if (isWithinReach(level, golem, this.target, interactionRange(golem))) {
			this.interacting = true;
			this.ticks = 0;
		} else {
			BehaviorUtils.setWalkAndLookTargetMemories(golem, this.target, 1.0F, 0);
		}
	}

	private void tickInteraction(ServerLevel level, CopperGolem golem, Container container, long now) {
		if (!isWithinReach(level, golem, this.target, KEEP_INTERACTING_DISTANCE)) {
			this.endInteraction(golem);
			this.interacting = false;
			this.ticks = 0;
			return;
		}
		this.ticks++;
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.target));
		stopInPlace(golem);
		if (this.ticks == 1) {
			container.startOpen(golem);
			golem.setOpenedChestPos(this.target);
			golem.setState(this.animationFor(golem, container));
		}
		if (this.ticks == SOUND_TICK) {
			SoundEvent sound = soundFor(golem.getState());
			if (sound != null) {
				golem.playSound(sound);
			}
		}
		if (this.ticks >= INTERACTION_TICKS) {
			switch (this.purpose) {
				case PICKUP -> this.pickUp(level, golem, container);
				case DELIVER -> this.deliver(golem, container, now);
				case DECOY -> {
				}
			}
			container.setChanged();
			this.endInteraction(golem);
			if (container.getEntitiesWithContainerOpen().contains(golem)) {
				container.stopOpen(golem);
			}
			this.dropTarget(golem);
		}
	}

	private void endInteraction(CopperGolem golem) {
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
	}

	private CopperGolemState animationFor(CopperGolem golem, Container container) {
		return switch (this.purpose) {
			case PICKUP -> container.isEmpty() ? CopperGolemState.GETTING_NO_ITEM : CopperGolemState.GETTING_ITEM;
			case DELIVER -> hasRoom(container, golem.getMainHandItem()) ? CopperGolemState.DROPPING_ITEM : CopperGolemState.DROPPING_NO_ITEM;
			case DECOY -> CopperGolemState.DROPPING_NO_ITEM;
		};
	}

	private static @Nullable SoundEvent soundFor(CopperGolemState state) {
		return switch (state) {
			case GETTING_ITEM -> SoundEvents.COPPER_GOLEM_ITEM_GET;
			case GETTING_NO_ITEM -> SoundEvents.COPPER_GOLEM_ITEM_NO_GET;
			case DROPPING_ITEM -> SoundEvents.COPPER_GOLEM_ITEM_DROP;
			case DROPPING_NO_ITEM -> SoundEvents.COPPER_GOLEM_ITEM_NO_DROP;
			case IDLE -> null;
		};
	}

	/** A miner's home chest keeps its torches, rails and wood; everything else in it gets sorted as usual. */
	private Predicate<ItemStack> sortableIn(BlockPos source, Container container) {
		if (this.minerHomes.contains(source)) {
			return stack -> !MineBehavior.isSupply(stack);
		}
		if (this.farmerHomes.contains(source)) {
			// A farm chest keeps a few of every seed so its farmer can always replant.
			return stack -> !FarmerData.isSeed(stack) || countOf(container, stack) > SEED_RESERVE;
		}
		return stack -> true;
	}

	private static int countOf(Container container, ItemStack item) {
		int count = 0;
		for (ItemStack stack : container) {
			if (ItemStack.isSameItem(stack, item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private void pickUp(ServerLevel level, CopperGolem golem, Container source) {
		Predicate<ItemStack> sortable = this.sortableIn(this.target, source);
		ItemStack kind = ItemStack.EMPTY;
		for (ItemStack stack : source) {
			if (!stack.isEmpty() && sortable.test(stack)) {
				kind = stack.copy();
				break;
			}
		}
		if (kind.isEmpty()) {
			return;
		}
		ItemStack hand = Inventories.takeFirst(source, MiosCoppergolemsApi.carryLimit(golem, HAND_LIMIT), sortable);
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			for (int slot = 0; slot < source.getContainerSize(); slot++) {
				ItemStack stack = source.getItem(slot);
				if (!stack.isEmpty() && sortable.test(stack) && ItemFamilies.related(kind, stack)) {
					source.setItem(slot, insert(cart, stack));
				}
			}
			cart.setChanged();
		}
		this.setHandItem(golem, hand);
		this.decoyBudget = level.getRandom().nextInt(MAX_DECOYS + 1);
		this.deliveryPrepared = false;
		this.ignoredUntil.clear();
	}

	private void deliver(CopperGolem golem, Container destination, long now) {
		ItemStack hand = golem.getMainHandItem();
		ItemStack delivered = hand.copyWithCount(1);
		boolean matchingChest = this.tier == Tier.MATCHING_CHEST;
		boolean framed = this.tier == Tier.FRAMED;
		ItemStack left = insert(destination, hand);
		this.setHandItem(golem, left);
		this.deliveryPrepared = false;

		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			// Unload everything of the same kind, and into a sorted chest also anything else it already holds.
			for (int slot = 0; slot < cart.getContainerSize(); slot++) {
				ItemStack stack = cart.getItem(slot);
				boolean belongs = framed ? showsItem(this.targetFrames, stack) : ItemStack.isSameItem(stack, delivered) || matchingChest && containsItem(destination, stack);
				if (!stack.isEmpty() && belongs) {
					cart.setItem(slot, insert(destination, stack));
				}
			}
			cart.setChanged();
		}
		if (!left.isEmpty()) {
			this.ignore(this.target, now);
		}
	}

	private void setHandItem(CopperGolem golem, ItemStack stack) {
		golem.setItemSlot(EquipmentSlot.MAINHAND, stack);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
	}

	// --- world queries ---

	private List<Spot> scan(ServerLevel level, CopperGolem golem, long now) {
		int range = Eyes.radius(golem, HORIZONTAL_RANGE);
		AABB area = new AABB(golem.blockPosition()).inflate(range, VERTICAL_RANGE, range);
		List<Spot> spots = new ArrayList<>();
		Map<BlockPos, List<ItemStack>> frames = new HashMap<>();
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, area.inflate(1.0), frame -> !frame.getItem().isEmpty())) {
			// A frame hangs on the block behind it.
			frames.computeIfAbsent(frame.getPos().relative(frame.getDirection().getOpposite()), pos -> new ArrayList<>()).add(frame.getItem());
		}
		for (ChunkPos chunkPos : ChunkPos.rangeClosed(ChunkPos.containing(golem.blockPosition()), range / 16 + 1).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk == null) {
				continue;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				BlockPos pos = blockEntity.getBlockPos();
				if (!area.contains(pos.getX(), pos.getY(), pos.getZ()) || this.isIgnored(pos, now)) {
					continue;
				}
				Kind kind = kindOf(blockEntity.getBlockState());
				Container container = kind == null ? null : containerOf(level, pos, blockEntity);
				if (container != null) {
					List<ItemStack> shown = new ArrayList<>();
					for (BlockPos part : sameContainer(level, pos)) {
						shown.addAll(frames.getOrDefault(part, List.of()));
					}
					spots.add(new Spot(pos, kind, container, shown));
				}
			}
		}
		return spots;
	}

	private static boolean showsItem(List<ItemStack> frames, ItemStack item) {
		for (ItemStack shown : frames) {
			if (ItemStack.isSameItem(shown, item)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Kind kindOf(BlockState state) {
		if (state.is(BlockTags.COPPER_CHESTS)) {
			return Kind.SOURCE;
		}
		if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
			return Kind.CHEST;
		}
		return state.is(Blocks.BARREL) ? Kind.BARREL : null;
	}

	private static @Nullable Container containerAt(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity == null || kindOf(blockEntity.getBlockState()) == null ? null : containerOf(level, pos, blockEntity);
	}

	/** The container behind a block, with double chests combined. Locked or blocked containers count as absent. */
	private static @Nullable Container containerOf(Level level, BlockPos pos, BlockEntity blockEntity) {
		if (blockEntity instanceof BaseContainerBlockEntity base && base.isLocked()) {
			return null;
		}
		BlockState state = blockEntity.getBlockState();
		if (state.getBlock() instanceof ChestBlock chestBlock) {
			return ChestBlock.getContainer(chestBlock, state, level, pos, false);
		}
		return blockEntity instanceof Container container ? container : null;
	}

	/** Both halves of a double chest, or just the position itself. */
	private static List<BlockPos> sameContainer(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			return List.of(pos, ChestBlock.getConnectedBlockPos(pos, state));
		}
		return List.of(pos);
	}

	private static @Nullable Spot nearest(CopperGolem golem, List<Spot> spots, Predicate<Spot> filter) {
		return spots.stream().filter(filter).min(Comparator.comparingDouble(spot -> spot.pos.distToCenterSqr(golem.position()))).orElse(null);
	}

	private static boolean isOpenedByOthers(Container container, CopperGolem golem) {
		return container.getEntitiesWithContainerOpen().stream().anyMatch(user -> user != golem);
	}

	private boolean canReach(ServerLevel level, CopperGolem golem, BlockPos pos) {
		if (isWithinReach(level, golem, pos, 1.0)) {
			return true;
		}
		Path path = golem.getNavigation().createPath(pos, 0);
		if (path == null || path.getEndNode() == null) {
			return false;
		}
		Vec3 end = Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());
		return isWithinReach(level, golem, pos, 1.0, end);
	}

	private static double interactionRange(CopperGolem golem) {
		Path path = golem.getNavigation().getPath();
		return path != null && path.isDone() ? 1.0 : 0.5;
	}

	private static boolean isWithinReach(Level level, CopperGolem golem, BlockPos pos, double distance) {
		return isWithinReach(level, golem, pos, distance, golem.position());
	}

	private static boolean isWithinReach(Level level, CopperGolem golem, BlockPos pos, double distance, Vec3 feet) {
		AABB box = golem.getBoundingBox();
		AABB golemBox = AABB.ofSize(feet.add(0.0, box.getYsize() / 2.0, 0.0), box.getXsize(), box.getYsize(), box.getZsize());
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		AABB blockBox = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
		return blockBox.inflate(distance, 0.5, distance).move(pos).intersects(golemBox);
	}

	private static void stopInPlace(CopperGolem golem) {
		golem.getNavigation().stop();
		golem.setXxa(0.0F);
		golem.setYya(0.0F);
		golem.setSpeed(0.0F);
		golem.setDeltaMovement(0.0, golem.getDeltaMovement().y, 0.0);
	}

	// --- inventory helpers ---

	private static ItemStack takeFirst(Container container, int limit) {
		return Inventories.takeFirst(container, limit, stack -> true);
	}

	private static boolean containsItem(Container container, ItemStack item) {
		return Inventories.containsItem(container, item);
	}

	private static boolean hasRoom(Container container, ItemStack item) {
		return Inventories.hasRoom(container, item);
	}

	private static ItemStack insert(Container container, ItemStack stack) {
		return Inventories.insert(container, stack);
	}
}
