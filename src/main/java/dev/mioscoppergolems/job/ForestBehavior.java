package dev.mioscoppergolems.job;

import dev.mioscoppergolems.golem.Eyes;
import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fells the natural trees around its home (trunks with leaves that grew there, never logs a player placed), picks up
 * the saplings, apples and sticks that fall, and plants a sapling on every stump it left.
 */
public class ForestBehavior extends JobBehavior<ForestBehavior.Task> {
	private static final int RADIUS = 12;
	private static final int HEIGHT = 6;
	private static final int MAX_LOGS = 200;
	private static final int SAPLING_RESERVE = 8;
	private static final int ENERGY_PER_LOG = 3;

	enum Kind {
		FELL,
		PICK_UP,
		PLANT
	}

	record Task(Kind kind, BlockPos pos, @Nullable ItemEntity item, @Nullable Item sapling) {
	}

	private int chopTicks;
	private int walkTicks;

	public ForestBehavior() {
		super(Job.FORESTER);
	}

	@Override
	protected @Nullable Task chooseTask(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos home) {
		int radius = Eyes.radius(golem, RADIUS);
		AABB area = new AABB(home).inflate(radius, HEIGHT, radius);
		// Picking up what fell off the trees comes first, before it despawns.
		ItemEntity drop = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && isForestDrop(item.getItem()) && isOnGround(level, item))
			.stream()
			.min(Comparator.comparingDouble(golem::distanceToSqr))
			.orElse(null);
		if (drop != null && !this.isIgnored(level, drop.blockPosition())) {
			return new Task(Kind.PICK_UP, drop.blockPosition(), drop, null);
		}
		// Then the stumps waiting for a sapling.
		data.marks().removeIf(stump -> !level.isLoaded(stump) || !level.getBlockState(stump).isAir());
		for (BlockPos stump : data.marks()) {
			Item sapling = this.saplingFor(golem, data, stump);
			if (sapling != null && !this.isIgnored(level, stump)) {
				return new Task(Kind.PLANT, stump, null, sapling);
			}
		}
		// Then the nearest tree.
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos mutable : BlockPos.betweenClosed(home.offset(-radius, -HEIGHT, -radius), home.offset(radius, HEIGHT, radius))) {
			if (!level.isLoaded(mutable) || !level.getBlockState(mutable).is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (this.isIgnored(level, pos) || !isTreeBase(level, pos)) {
				continue;
			}
			double distance = pos.distToCenterSqr(golem.position());
			if (distance < bestDistance && standFor(level, pos, golem) != null && isNatural(level, trunk(level, pos))) {
				best = pos;
				bestDistance = distance;
			}
		}
		return best == null ? null : new Task(Kind.FELL, best, null, null);
	}

	@Override
	protected boolean tickTask(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos home, Task task) {
		return switch (task.kind()) {
			case PICK_UP -> this.pickUp(level, golem, data, task.item());
			case PLANT -> this.plant(level, golem, data, task.pos(), task.sapling());
			case FELL -> this.fell(level, golem, data, task.pos());
		};
	}

	private boolean pickUp(ServerLevel level, CopperGolem golem, WorkerData data, @Nullable ItemEntity item) {
		if (item == null || !item.isAlive()) {
			return true;
		}
		if (!this.reach(level, golem, item)) {
			return false;
		}
		stash(golem, data, item.getItem().copy());
		item.discard();
		golem.setState(CopperGolemState.GETTING_ITEM);
		golem.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.3F, 1.0F);
		this.pause(15);
		return true;
	}

	private boolean plant(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos stump, @Nullable Item sapling) {
		if (!(sapling instanceof BlockItem blockItem)) {
			return true;
		}
		if (!this.reach(level, golem, stump)) {
			return false;
		}
		if (++this.workTicks < slowed(golem, 20)) {
			golem.setState(CopperGolemState.DROPPING_ITEM);
			return false;
		}
		BlockState state = blockItem.getBlock().defaultBlockState();
		boolean fits = level.getBlockState(stump).isAir() && state.canSurvive(level, stump);
		if (fits && (!WorkerData.takeOne(data.supplies(), stack -> stack.is(sapling)).isEmpty() || this.takeFromPouch(data, sapling))) {
			level.setBlockAndUpdate(stump, state);
			level.gameEvent(GameEvent.BLOCK_PLACE, stump, GameEvent.Context.of(golem, state));
			level.playSound(null, stump, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
			Upkeep.spend(golem, 2);
		}
		data.marks().remove(stump);
		return true;
	}

	private boolean takeFromPouch(WorkerData data, Item sapling) {
		return !WorkerData.takeOne(data.pouch(), stack -> stack.is(sapling)).isEmpty();
	}

	private boolean fell(ServerLevel level, CopperGolem golem, WorkerData data, BlockPos base) {
		if (!level.getBlockState(base).is(BlockTags.LOGS)) {
			return true;
		}
		if (this.workTicks == 0 && !this.standingAt(level, golem, base)) {
			return false;
		}
		List<BlockPos> logs = trunk(level, base);
		if (this.workTicks == 0) {
			if (this.hesitate(golem)) {
				return false;
			}
			// A few chops per log: big trees take a while.
			this.chopTicks = slowed(golem, 30 + logs.size() * 8);
			golem.setState(CopperGolemState.GETTING_ITEM);
		}
		this.workTicks++;
		level.destroyBlockProgress(golem.getId(), base, Math.min(9, this.workTicks * 10 / this.chopTicks));
		if (this.workTicks % 8 == 1) {
			SoundType sound = level.getBlockState(base).getSoundType();
			level.playSound(null, base, sound.getHitSound(), SoundSource.NEUTRAL, sound.getVolume(), sound.getPitch() * 0.8F);
		}
		if (this.workTicks % 20 == 0) {
			golem.setState(golem.getState() == CopperGolemState.GETTING_ITEM ? CopperGolemState.DROPPING_ITEM : CopperGolemState.GETTING_ITEM);
		}
		if (this.workTicks < this.chopTicks) {
			return false;
		}
		level.destroyBlockProgress(golem.getId(), base, -1);
		Item sapling = saplingOf(level.getBlockState(base).getBlock());
		ItemStack axe = golem.getMainHandItem();
		logs.sort(Comparator.comparingInt((BlockPos log) -> log.getY()).reversed());
		for (BlockPos log : logs) {
			if (axe.isEmpty()) {
				break;
			}
			BlockState state = level.getBlockState(log);
			Block.getDrops(state, level, log, null, golem, axe).forEach(drop -> stash(golem, data, drop));
			level.destroyBlock(log, false, golem);
			Tools.wear(golem, axe);
			Upkeep.spend(golem, ENERGY_PER_LOG);
		}
		if (sapling != null && level.getBlockState(base).isAir()) {
			data.marks().add(base);
		}
		return true;
	}

	/**
	 * Walks to a ground spot right next to the trunk. Walking towards the log itself makes the pathfinder end up on
	 * the leaves, and the golem would climb the tree.
	 */
	private boolean standingAt(ServerLevel level, CopperGolem golem, BlockPos base) {
		boolean beside = Math.abs(golem.getY() - base.getY()) < 0.6
			&& golem.position().subtract(Vec3.atBottomCenterOf(base)).horizontalDistanceSqr() < 2.0 * 2.0;
		if (beside) {
			this.walkTicks = 0;
			stopInPlace(golem);
			golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(base));
			return true;
		}
		BlockPos stand = standFor(level, base, golem);
		if (stand == null || this.stalledTowards(golem, Vec3.atBottomCenterOf(stand)) > MAX_TRAVEL_TICKS) {
			this.walkTicks = 0;
			this.ignore(level, base);
			this.giveUp();
			return false;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, stand, 0.8F * Upkeep.speed(golem), 0);
		return false;
	}

	/** An open spot on the ground next to the trunk, the one closest to the golem. */
	private static @Nullable BlockPos standFor(ServerLevel level, BlockPos base, CopperGolem golem) {
		BlockPos best = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos stand = base.relative(direction);
			boolean open = level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
				&& level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
				&& level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP);
			if (open && (best == null || stand.distToCenterSqr(golem.position()) < best.distToCenterSqr(golem.position()))) {
				best = stand;
			}
		}
		return best;
	}

	/** Saplings stuck on the leaves are left until the leaves decay and they fall. */
	private static boolean isOnGround(ServerLevel level, ItemEntity item) {
		return item.onGround() && !level.getBlockState(item.blockPosition().below()).is(BlockTags.LEAVES)
			&& !level.getBlockState(item.blockPosition()).is(BlockTags.LEAVES);
	}

	// --- trees ---

	/** The lowest log of a trunk, standing on soil. */
	private static boolean isTreeBase(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return !below.is(BlockTags.LOGS) && below.is(BlockTags.SUPPORTS_VEGETATION);
	}

	/** All logs connected to the base (diagonals included), as far as a tree goes. */
	private static List<BlockPos> trunk(ServerLevel level, BlockPos base) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(base);
		seen.add(base);
		while (!open.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos pos = open.poll();
			logs.add(pos);
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
				BlockPos candidate = next.immutable();
				if (candidate.getY() >= base.getY() && seen.add(candidate) && level.getBlockState(candidate).is(BlockTags.LOGS)) {
					open.add(candidate);
				}
			}
		}
		return logs;
	}

	/** Grown trees have natural leaves touching the trunk; logs placed by a player have none. */
	private static boolean isNatural(ServerLevel level, List<BlockPos> logs) {
		for (BlockPos log : logs) {
			for (Direction direction : Direction.values()) {
				BlockState state = level.getBlockState(log.relative(direction));
				if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
					return true;
				}
			}
		}
		return false;
	}

	/** oak_log -> oak_sapling, mangrove_log -> mangrove_propagule. */
	private static @Nullable Item saplingOf(Block log) {
		String path = BuiltInRegistries.BLOCK.getKey(log).getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
		Identifier id = Identifier.withDefaultNamespace(path.equals("mangrove") ? "mangrove_propagule" : path + "_sapling");
		return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	/** A sapling for this stump: the kind that grew there if it has one, otherwise any. */
	private @Nullable Item saplingFor(CopperGolem golem, WorkerData data, BlockPos stump) {
		List<ItemStack> all = new ArrayList<>(data.supplies());
		all.addAll(data.pouch());
		for (ItemStack stack : all) {
			if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock().defaultBlockState().canSurvive(golem.level(), stump)) {
				return stack.getItem();
			}
		}
		return null;
	}

	private static boolean isForestDrop(ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE) || stack.is(Items.STICK) || stack.is(ItemTags.LOGS);
	}

	@Override
	protected int keepOnUnload(WorkerData data, ItemStack stack) {
		if (!stack.is(ItemTags.SAPLINGS)) {
			return 0;
		}
		return Math.max(0, SAPLING_RESERVE - WorkerData.countOf(data.supplies(), other -> other.is(stack.getItem())));
	}

	@Override
	protected void restock(ServerLevel level, CopperGolem golem, WorkerData data, Container chest) {
		if (WorkerData.countOf(data.supplies(), stack -> stack.is(ItemTags.SAPLINGS)) == 0) {
			WorkerData.addTo(data.supplies(), Inventories.takeFirst(chest, SAPLING_RESERVE, stack -> stack.is(ItemTags.SAPLINGS)));
		}
	}
}
