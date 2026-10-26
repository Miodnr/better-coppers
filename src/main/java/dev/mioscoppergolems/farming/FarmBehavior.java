package dev.mioscoppergolems.farming;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.golem.CartChaining;
import dev.mioscoppergolems.golem.Eyes;
import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.golem.TravelWatch;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.job.Tools;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Looks after every field around the golem's home copper chest: harvests ripe crops and replants them, picks pumpkins
 * and melons off their stems, cuts sugar cane and bamboo down to the bottom block, and plants seeds on empty farmland.
 * The harvest goes into the pouch or the chained cart and is brought to the home chest; seeds are kept for replanting.
 * <p>
 * Like the miners it takes its time: every harvest is a bit of fiddling, and now and then it stops to scratch its head.
 */
public class FarmBehavior extends Behavior<CopperGolem> {
	public static final int FIELD_RADIUS = 8;
	private static final int FIELD_HEIGHT = 3;
	private static final int HOME_SEARCH_RADIUS = 16;
	private static final int WORK_TICKS = 25;
	private static final int MAX_TRAVEL_TICKS = 300;
	private static final int HOME_INTERACTION_TICKS = 60;
	private static final int IGNORE_TICKS = 600;
	private static final int IDLE_COOLDOWN = 200;
	private static final int SEED_RESERVE = 16;
	private static final int STALK_MIN_HEIGHT = 3;
	private static final float HESITATE_CHANCE = 0.08F;
	private static final float WALK_SPEED = 0.8F;
	private static final int ENERGY_PER_ACTION = 3;

	private enum Job {
		/** Ripe wheat, carrots, potatoes, beetroot or nether wart: harvest and replant. */
		HARVEST_CROP,
		/** A pumpkin or melon grown from a stem. */
		HARVEST_FRUIT,
		/** Sugar cane or bamboo: cut everything above the bottom block. */
		HARVEST_STALK,
		PLANT
	}

	private record Task(Job job, BlockPos pos, @Nullable Item seed) {
	}

	private @Nullable Task task;
	private boolean returning;
	private boolean hesitated;
	private int travelTicks;
	private final TravelWatch travel = new TravelWatch();
	private int workTicks;
	private int workDuration;
	private int pauseTicks;
	private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

	public FarmBehavior() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return hasWork(golem) && Upkeep.canWork(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !golem.isPanicking() && hasWork(golem) && Upkeep.canWork(level, golem);
	}

	private static boolean hasWork(CopperGolem golem) {
		FarmerData data = FarmerData.get(golem);
		if (golem.isLeashed() || data == null || Homes.get(golem) == null) {
			return false;
		}
		if (data.harvestCount() > 0 || FarmerData.hasHoe(golem)) {
			return true;
		}
		// No hoe any more, but a cart full of harvest still goes home first.
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return cart != null && !cart.isEmpty();
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.task = null;
		this.returning = false;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		if (this.pauseTicks > 0) {
			if (--this.pauseTicks == 0) {
				golem.setState(CopperGolemState.IDLE);
			}
			return;
		}
		FarmerData data = FarmerData.get(golem);
		if (data == null) {
			return;
		}
		BlockPos home = Homes.valid(level, golem);
		if (home == null) {
			// Home chest gone (or not loaded): wait until it is back or the golem gets a new one.
			this.idle(golem);
			return;
		}
		if (this.returning) {
			this.tickReturn(level, golem, data, home);
			return;
		}
		if (!FarmerData.hasHoe(golem)) {
			this.startReturn(golem);
			return;
		}
		if (this.task == null) {
			if (this.isFull(golem, data)) {
				this.startReturn(golem);
				return;
			}
			this.task = this.chooseTask(level, golem, data, home);
			this.travelTicks = 0;
			this.travel.reset();
			this.workTicks = 0;
			this.hesitated = false;
			if (this.returning) {
				// Off to fetch seeds.
				return;
			}
			if (this.task == null) {
				CopperChestMinecart cart = CartChaining.findCart(golem);
				if (data.harvestCount() > 0 || cart != null && !cart.isEmpty()) {
					this.startReturn(golem);
				} else {
					this.idle(golem);
				}
				return;
			}
		}
		this.tickTask(level, golem, data);
	}

	private void idle(CopperGolem golem) {
		this.task = null;
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, IDLE_COOLDOWN);
	}

	// --- finding work ---

	private @Nullable Task chooseTask(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos home) {
		long now = level.getGameTime();
		Task best = null;
		double bestDistance = Double.MAX_VALUE;
		boolean seedsAtHome = false;
		int radius = Eyes.radius(golem, FIELD_RADIUS);
		for (BlockPos mutable : BlockPos.betweenClosed(home.offset(-radius, -FIELD_HEIGHT, -radius), home.offset(radius, FIELD_HEIGHT, radius))) {
			if (!level.isLoaded(mutable)) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (this.isIgnored(pos, now)) {
				continue;
			}
			Task found = this.taskAt(level, golem, data, pos);
			if (found == null) {
				if (!seedsAtHome && level.getBlockState(pos).is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir()) {
					seedsAtHome = homeHas(level, home, stack -> FarmerData.isSeed(stack) && cropOf(stack) instanceof CropBlock);
				}
				continue;
			}
			double distance = pos.distToCenterSqr(golem.position());
			if (distance < bestDistance) {
				best = found;
				bestDistance = distance;
			}
		}
		if (best == null && seedsAtHome && this.findSeed(golem, data, stack -> cropOf(stack) instanceof CropBlock).isEmpty()) {
			// Empty farmland and seeds waiting in the chest: fetch them.
			this.startReturn(golem);
		}
		return best;
	}

	private @Nullable Task taskAt(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
			return new Task(Job.HARVEST_CROP, pos, null);
		}
		if (block instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
			return new Task(Job.HARVEST_CROP, pos, null);
		}
		if ((state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) && isGrownFromStem(level, pos)) {
			return new Task(Job.HARVEST_FRUIT, pos, null);
		}
		if ((state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)) && !level.getBlockState(pos.below()).is(block) && stalkHeight(level, pos) >= STALK_MIN_HEIGHT) {
			return new Task(Job.HARVEST_STALK, pos, null);
		}
		BlockPos above = pos.above();
		if (!level.getBlockState(above).isAir()) {
			return null;
		}
		if (state.is(Blocks.FARMLAND)) {
			Item seed = this.pickSeed(level, golem, data, above, stack -> cropOf(stack) instanceof CropBlock);
			return seed == null ? null : new Task(Job.PLANT, above, seed);
		}
		if (state.is(Blocks.SOUL_SAND)) {
			Item seed = this.pickSeed(level, golem, data, above, stack -> cropOf(stack) instanceof NetherWartBlock);
			return seed == null ? null : new Task(Job.PLANT, above, seed);
		}
		return null;
	}

	private static boolean isGrownFromStem(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			if (neighbour.getBlock() instanceof AttachedStemBlock && neighbour.getValue(AttachedStemBlock.FACING) == direction.getOpposite()) {
				return true;
			}
		}
		return false;
	}

	private static int stalkHeight(ServerLevel level, BlockPos bottom) {
		Block block = level.getBlockState(bottom).getBlock();
		int height = 1;
		while (height < 32 && level.getBlockState(bottom.above(height)).is(block)) {
			height++;
		}
		return height;
	}

	/** Plants what the neighbours grow, so rows stay tidy; otherwise whatever seed it has. */
	private @Nullable Item pickSeed(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos pos, Predicate<ItemStack> fits) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			Item neighbourSeed = level.getBlockState(pos.relative(direction)).getBlock().asItem();
			ItemStack sample = new ItemStack(neighbourSeed);
			if (FarmerData.isSeed(sample) && fits.test(sample) && !this.findSeed(golem, data, stack -> stack.is(neighbourSeed)).isEmpty()) {
				return neighbourSeed;
			}
		}
		ItemStack any = this.findSeed(golem, data, stack -> FarmerData.isSeed(stack) && fits.test(stack));
		return any.isEmpty() ? null : any.getItem();
	}

	private static @Nullable Block cropOf(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}

	// --- working ---

	private void tickTask(ServerLevel level, CopperGolem golem, FarmerData data) {
		Task task = this.task;
		BlockPos pos = task.pos();
		if (!this.isInReach(golem, pos)) {
			if (this.travel.tick(golem, Vec3.atCenterOf(pos)) > MAX_TRAVEL_TICKS) {
				this.ignore(pos, level.getGameTime());
				this.task = null;
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, pos, WALK_SPEED * Upkeep.speed(golem), 1);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
		if (this.workTicks == 0) {
			if (this.taskAt(level, golem, data, task.job() == Job.PLANT ? pos.below() : pos) == null) {
				// Someone else got there first.
				this.task = null;
				return;
			}
			if (!this.hesitated && golem.getRandom().nextFloat() < HESITATE_CHANCE) {
				this.hesitated = true;
				golem.setState(CopperGolemState.GETTING_NO_ITEM);
				golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
				this.pauseTicks = 30 + golem.getRandom().nextInt(30);
				return;
			}
			this.workDuration = Math.round((WORK_TICKS + golem.getRandom().nextInt(15)) / Upkeep.speed(golem));
			golem.setState(task.job() == Job.PLANT ? CopperGolemState.DROPPING_ITEM : CopperGolemState.GETTING_ITEM);
		}
		this.workTicks++;
		if (this.workTicks % 10 == 1) {
			SoundType sound = level.getBlockState(task.job() == Job.PLANT ? pos.below() : pos).getSoundType();
			level.playSound(null, pos, sound.getHitSound(), SoundSource.NEUTRAL, sound.getVolume() * 0.5F, sound.getPitch() * 0.8F);
		}
		if (this.workTicks >= this.workDuration) {
			switch (task.job()) {
				case HARVEST_CROP -> this.harvestCrop(level, golem, data, pos);
				case HARVEST_FRUIT -> this.harvestBlocks(level, golem, data, List.of(pos));
				case HARVEST_STALK -> this.harvestStalk(level, golem, data, pos);
				case PLANT -> this.plant(level, golem, data, pos, task.seed());
			}
			this.task = null;
			golem.setState(CopperGolemState.IDLE);
			this.pauseTicks = 5;
		}
	}

	private void harvestCrop(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos pos) {
		Item seed = level.getBlockState(pos).getBlock().asItem();
		this.harvestBlocks(level, golem, data, List.of(pos));
		this.plant(level, golem, data, pos, seed);
	}

	private void harvestStalk(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos bottom) {
		List<BlockPos> stalk = new ArrayList<>();
		for (int up = stalkHeight(level, bottom) - 1; up >= 1; up--) {
			stalk.add(bottom.above(up));
		}
		this.harvestBlocks(level, golem, data, stalk);
	}

	/** Breaks the blocks (in the given order) and keeps their drops. */
	private void harvestBlocks(ServerLevel level, CopperGolem golem, FarmerData data, List<BlockPos> positions) {
		ItemStack tool = golem.getMainHandItem();
		for (BlockPos pos : positions) {
			BlockState state = level.getBlockState(pos);
			List<ItemStack> drops = Block.getDrops(state, level, pos, null, golem, tool);
			level.destroyBlock(pos, false, golem);
			if (state.getDestroySpeed(level, pos) > 0.0F) {
				Tools.wear(golem, tool);
			}
			drops.forEach(drop -> this.stash(golem, data, drop));
			Upkeep.spend(golem, ENERGY_PER_ACTION);
		}
	}

	private void plant(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos pos, @Nullable Item seed) {
		if (seed == null || !(seed instanceof BlockItem blockItem)) {
			return;
		}
		BlockState crop = blockItem.getBlock().defaultBlockState();
		if (!level.getBlockState(pos).isAir() || !crop.canSurvive(level, pos) || this.takeSeed(golem, data, seed).isEmpty()) {
			return;
		}
		level.setBlockAndUpdate(pos, crop);
		Upkeep.spend(golem, ENERGY_PER_ACTION);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(golem, crop));
		level.playSound(null, pos, crop.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	// --- pouches ---

	private void stash(CopperGolem golem, FarmerData data, ItemStack stack) {
		ItemStack rest = stack;
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			rest = Inventories.insert(cart, rest);
		}
		if (!rest.isEmpty()) {
			addTo(data.harvest(), rest);
		}
	}

	private boolean isFull(CopperGolem golem, FarmerData data) {
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return (cart == null || !Inventories.hasEmptySlot(cart)) && data.harvestCount() >= FarmerData.HARVEST_LIMIT;
	}

	/** Finds (without taking) a seed in the seed pouch, the harvest or the cart. */
	private ItemStack findSeed(CopperGolem golem, FarmerData data, Predicate<ItemStack> filter) {
		for (List<ItemStack> pouch : List.of(data.seeds(), data.harvest())) {
			for (ItemStack stack : pouch) {
				if (!stack.isEmpty() && filter.test(stack)) {
					return stack;
				}
			}
		}
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			for (ItemStack stack : cart) {
				if (!stack.isEmpty() && filter.test(stack)) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private ItemStack takeSeed(CopperGolem golem, FarmerData data, Item seed) {
		for (List<ItemStack> pouch : List.of(data.seeds(), data.harvest())) {
			for (ItemStack stack : pouch) {
				if (stack.is(seed)) {
					ItemStack one = stack.split(1);
					pouch.removeIf(ItemStack::isEmpty);
					return one;
				}
			}
		}
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return cart == null ? ItemStack.EMPTY : Inventories.takeFirst(cart, 1, stack -> stack.is(seed));
	}

	/** Adds to a pouch, merging with existing stacks. */
	static void addTo(List<ItemStack> pouch, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (ItemStack existing : pouch) {
			if (rest.isEmpty()) {
				return;
			}
			if (ItemStack.isSameItemSameComponents(existing, rest) && existing.getCount() < existing.getMaxStackSize()) {
				int moved = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
				existing.grow(moved);
				rest.shrink(moved);
			}
		}
		if (!rest.isEmpty()) {
			pouch.add(rest);
		}
	}

	// --- going home ---

	private void startReturn(CopperGolem golem) {
		this.returning = true;
		this.task = null;
		this.workTicks = 0;
		golem.setState(CopperGolemState.IDLE);
	}

	private void tickReturn(ServerLevel level, CopperGolem golem, FarmerData data, BlockPos home) {
		Container chest = homeContainer(level, home);
		if (chest == null) {
			this.returning = false;
			return;
		}
		if (!this.isInReach(golem, home)) {
			BehaviorUtils.setWalkAndLookTargetMemories(golem, home, WALK_SPEED * Upkeep.speed(golem), 1);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(home));
		this.workTicks++;
		if (this.workTicks == 1) {
			chest.startOpen(golem);
			golem.setOpenedChestPos(home);
			golem.setState(CopperGolemState.DROPPING_ITEM);
		}
		if (this.workTicks == 9) {
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP);
		}
		if (this.workTicks >= HOME_INTERACTION_TICKS) {
			this.unload(level, golem, data, chest);
			this.restockSeeds(data, chest);
			if (chest.getEntitiesWithContainerOpen().contains(golem)) {
				chest.stopOpen(golem);
			}
			golem.clearOpenedChestPos();
			golem.setState(CopperGolemState.IDLE);
			this.returning = false;
			this.workTicks = 0;
			if (!FarmerData.hasHoe(golem)) {
				this.idle(golem);
			}
		}
	}

	private void unload(ServerLevel level, CopperGolem golem, FarmerData data, Container chest) {
		for (ItemStack stack : data.harvest()) {
			// Keep a handful of every seed for replanting, the rest goes in the chest.
			if (FarmerData.isSeed(stack)) {
				int kept = countOf(data.seeds(), stack.getItem());
				if (kept < SEED_RESERVE) {
					addTo(data.seeds(), stack.split(SEED_RESERVE - kept));
				}
			}
			ItemStack rest = Inventories.insert(chest, stack);
			if (!rest.isEmpty()) {
				golem.spawnAtLocation(level, rest);
			}
		}
		data.harvest().clear();
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

	private void restockSeeds(FarmerData data, Container chest) {
		Set<Item> seedTypes = new HashSet<>();
		for (ItemStack stack : chest) {
			if (FarmerData.isSeed(stack)) {
				seedTypes.add(stack.getItem());
			}
		}
		for (Item seed : seedTypes) {
			int missing = SEED_RESERVE - countOf(data.seeds(), seed);
			if (missing > 0) {
				addTo(data.seeds(), Inventories.takeFirst(chest, missing, stack -> stack.is(seed)));
			}
		}
	}

	private static int countOf(List<ItemStack> pouch, Item item) {
		return pouch.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static boolean homeHas(ServerLevel level, BlockPos home, Predicate<ItemStack> filter) {
		Container chest = homeContainer(level, home);
		return chest != null && Inventories.contains(chest, filter);
	}

	public static @Nullable BlockPos findCopperChest(ServerLevel level, BlockPos around) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ChunkPos chunkPos : ChunkPos.rangeClosed(ChunkPos.containing(around), 1).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk == null) {
				continue;
			}
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				BlockPos pos = blockEntity.getBlockPos();
				double distance = pos.distSqr(around);
				if (blockEntity.getBlockState().is(BlockTags.COPPER_CHESTS) && distance <= HOME_SEARCH_RADIUS * HOME_SEARCH_RADIUS && distance < bestDistance) {
					best = pos;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private static @Nullable Container homeContainer(ServerLevel level, BlockPos pos) {
		return Homes.container(level, pos);
	}

	/** Home chests of the farming golems around {@code pos}. */
	public static Set<BlockPos> farmerHomesNear(ServerLevel level, BlockPos pos) {
		Set<BlockPos> homes = new HashSet<>();
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, new AABB(pos).inflate(64))) {
			BlockPos home = Homes.get(golem);
			if (home != null && FarmerData.hasHoe(golem)) {
				homes.add(home);
			}
		}
		return homes;
	}

	// --- helpers ---

	private void ignore(BlockPos pos, long now) {
		this.ignoredUntil.put(pos, now + IGNORE_TICKS);
	}

	private boolean isIgnored(BlockPos pos, long now) {
		Long until = this.ignoredUntil.get(pos);
		if (until != null && until <= now) {
			this.ignoredUntil.remove(pos);
			return false;
		}
		return until != null;
	}

	private boolean isInReach(CopperGolem golem, BlockPos pos) {
		return new AABB(pos).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox());
	}

	private static void stopInPlace(CopperGolem golem) {
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.setXxa(0.0F);
		golem.setYya(0.0F);
		golem.setSpeed(0.0F);
		golem.setDeltaMovement(0.0, golem.getDeltaMovement().y, 0.0);
	}
}
