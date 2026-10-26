package dev.mioscoppergolems.mining;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.golem.CartChaining;
import dev.mioscoppergolems.golem.Eyes;
import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.golem.TravelWatch;
import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.job.Tools;
import dev.mioscoppergolems.registry.ModAttachments;
import dev.mioscoppergolems.registry.ModTags;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works through a {@link MinePlan} step by step: dig, lay floor, build supports, lay rails, hang torches. Mined items go
 * into the golem's pouch or its chained cart; when full (or out of supplies) it walks back to the copper chest at the
 * entrance, unloads, and restocks torches, wood and rails.
 * <p>
 * Golems are not good at this: they dig slowly, sometimes stop to scratch their heads, and give up on blocks they
 * cannot reach. Water and lava are sealed off with filler blocks before they can flood the tunnel.
 */
public class MineBehavior extends Behavior<CopperGolem> {
	/** Slower than a player with the same pickaxe. */
	private static final float DIG_SLOWNESS = 2.0F;
	private static final int MIN_DIG_TICKS = 10;
	private static final int MAX_DIG_TICKS = 400;
	private static final int PLACE_TICKS = 20;
	private static final int MAX_TRAVEL_TICKS = 300;
	private static final int HOME_INTERACTION_TICKS = 60;
	private static final float HESITATE_CHANCE = 0.08F;
	private static final int ORE_BUDGET = 16;
	private static final int RECENT_DIGS = 32;
	private static final int HOME_SEARCH_RADIUS = 12;
	private static final int FILLER_RESERVE = 16;
	private static final float WALK_SPEED = 0.9F;
	private static final int ENERGY_PER_DIG = 2;
	private static final int ENERGY_PER_BUILD = 1;
	/** Miners further than this from a sorting golem cannot be the reason to leave a chest alone. */
	private static final int MINER_HOME_SEARCH_RADIUS = 64;

	private enum Mode {
		WORKING,
		RETURNING
	}

	private @Nullable MinePlan layoutPlan;
	private List<MineLayout.Step> steps = List.of();

	private Mode mode = Mode.WORKING;
	private boolean stopAfterReturn;
	private @Nullable BlockPos home;

	private MineLayout.@Nullable Step work;
	private boolean extraWork;
	private boolean hesitated;
	private int travelTicks;
	private final TravelWatch travel = new TravelWatch();
	private int workTicks;
	private int digTicks;
	private int pauseTicks;

	private final ArrayDeque<MineLayout.Step> oreDigs = new ArrayDeque<>();
	private int oreBudget = ORE_BUDGET;
	private final ArrayDeque<MineLayout.Step> recentDigs = new ArrayDeque<>();
	/** Highest slice whose supports the golem already checked it carries. */
	private int checkedSlice = -1;
	/** Extra digs (ores, blockers) the golem could not get to; not tried again. */
	private final Set<BlockPos> unreachable = new HashSet<>();

	public MineBehavior() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return this.hasWork(level, golem) && Upkeep.canWork(level, golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !golem.isPanicking() && this.hasWork(level, golem) && Upkeep.canWork(level, golem);
	}

	private boolean hasWork(ServerLevel level, CopperGolem golem) {
		MinerData data = MinerData.get(golem);
		if (golem.isLeashed() || data == null || Homes.get(golem) == null) {
			return false;
		}
		if (data.lootCount() > 0) {
			return true;
		}
		MinePlan plan = data.plan();
		if (plan == null || !plan.isMarked()) {
			return false;
		}
		// A loaded cart still has to be driven home, even when the plan is done or the pickaxe broke.
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null && !cart.isEmpty() && Homes.valid(level, golem) != null) {
			return true;
		}
		return MinerData.hasPickaxe(golem) && plan.progress() < this.steps(level, plan).size();
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
		if (this.work != null) {
			level.destroyBlockProgress(golem.getId(), this.work.pos(), -1);
		}
		this.work = null;
		this.mode = Mode.WORKING;
		this.home = null;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (golem.getNavigation() instanceof GroundPathNavigation navigation) {
			navigation.setCanPathToTargetsBelowSurface(false);
		}
	}

	private List<MineLayout.Step> steps(ServerLevel level, MinePlan plan) {
		MinePlan key = plan.withProgress(0);
		if (!key.equals(this.layoutPlan)) {
			this.layoutPlan = key;
			this.steps = MineLayout.build(plan, level.getMinY());
			this.oreDigs.clear();
			this.recentDigs.clear();
			this.checkedSlice = -1;
			this.unreachable.clear();
		}
		return this.steps;
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		if (this.pauseTicks > 0) {
			if (--this.pauseTicks == 0) {
				golem.setState(CopperGolemState.IDLE);
			}
			return;
		}
		MinerData data = MinerData.get(golem);
		if (data == null) {
			return;
		}
		if (this.mode == Mode.RETURNING) {
			this.tickReturn(level, golem, data);
			return;
		}
		MinePlan plan = data.plan();
		if (plan == null || !plan.isMarked() || !MinerData.hasPickaxe(golem)) {
			// Pickaxe gone or plan taken away: just bring the pouch home.
			this.startReturn(level, golem, data, true);
			return;
		}
		if (isExcited(golem)) {
			if (level.getGameTime() % 10 == 0) {
				level.sendParticles(ParticleTypes.WAX_ON, golem.getX(), golem.getY() + 1.3, golem.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
			if (this.work == null && this.oreDigs.stream().noneMatch(ore -> isDiamond(level.getBlockState(ore.pos())))) {
				// The whole vein is out: off home to show the diamonds.
				this.startReturn(level, golem, data, false);
				return;
			}
		}
		if (this.work == null && !this.chooseWork(level, golem, data, plan)) {
			return;
		}
		this.tickWork(level, golem, data, plan);
	}

	// --- choosing what to do next ---

	private boolean chooseWork(ServerLevel level, CopperGolem golem, MinerData data, MinePlan plan) {
		// Gravel or sand that fell into the tunnel comes out first.
		for (MineLayout.Step dug : this.recentDigs) {
			if (level.isLoaded(dug.pos()) && level.getBlockState(dug.pos()).getBlock() instanceof FallingBlock) {
				return this.setWork(dug, true);
			}
		}
		while (!this.oreDigs.isEmpty()) {
			MineLayout.Step ore = this.oreDigs.poll();
			if (level.isLoaded(ore.pos()) && isOre(level.getBlockState(ore.pos())) && this.canDig(level, golem, ore.pos())) {
				if (this.isLootFull(golem, data)) {
					this.oreDigs.addFirst(ore);
					this.startReturn(level, golem, data, false);
					return false;
				}
				return this.setWork(ore, true);
			}
		}
		this.oreBudget = Eyes.radius(golem, ORE_BUDGET);

		List<MineLayout.Step> steps = this.steps(level, plan);
		while (plan.progress() < steps.size()) {
			MineLayout.Step step = steps.get(plan.progress());
			if (!level.isLoaded(step.pos())) {
				return false;
			}
			if (this.isDone(level, golem, step)) {
				plan = this.advance(data, plan);
				continue;
			}
			Predicate<ItemStack> material = this.materialFor(step.action(), plan);
			if (material != null && !this.hasMaterial(golem, data, material)) {
				if (this.homeHas(level, golem, material)) {
					this.startReturn(level, golem, data, false);
					return false;
				}
				// Nothing to build with anywhere: leave this one out.
				plan = this.advance(data, plan);
				continue;
			}
			if (step.action() == MineLayout.Action.DIG && this.isLootFull(golem, data)) {
				this.startReturn(level, golem, data, false);
				return false;
			}
			if (step.action() == MineLayout.Action.DIG) {
				int slice = MineLayout.sliceOf(plan, step.pos());
				if (slice > this.checkedSlice) {
					if (!this.hasSliceMaterials(golem, data, slice, null)) {
						// No supports, no digging. Fetch them, or wait at home until someone fills the chest.
						BlockPos homePos = Homes.valid(level, golem);
						Container homeChest = homePos == null ? null : homeContainer(level, homePos);
						boolean available = homeChest != null && this.hasSliceMaterials(golem, data, slice, homeChest);
						this.startReturn(level, golem, data, !available);
						return false;
					}
					this.checkedSlice = slice;
				}
			}
			// No floor where the golem should stand (the entrance at a ledge, the edge of a cave)? Lay one first, from
			// one block further back, or it would never get there.
			BlockPos standFloor = step.stand().below();
			BlockState below = level.getBlockState(standFloor);
			if (!below.isFaceSturdy(level, standFloor, Direction.UP) && below.canBeReplaced() && !this.unreachable.contains(standFloor)
				&& this.hasMaterial(golem, data, this.materialFor(MineLayout.Action.FLOOR, plan))) {
				BlockPos further = step.stand().relative(plan.heading().orElseThrow().getOpposite());
				return this.setWork(new MineLayout.Step(MineLayout.Action.FLOOR, standFloor, further, Direction.UP), true);
			}
			return this.setWork(step, false);
		}
		// Plan finished.
		this.startReturn(level, golem, data, true);
		return false;
	}

	private boolean setWork(MineLayout.Step step, boolean extra) {
		this.work = step;
		this.extraWork = extra;
		this.hesitated = false;
		this.travelTicks = 0;
		this.travel.reset();
		this.workTicks = 0;
		this.digTicks = 0;
		return true;
	}

	private void finishWork(ServerLevel level, CopperGolem golem, MinerData data) {
		if (!this.extraWork && data.plan() != null) {
			this.advance(data, data.plan());
		}
		this.work = null;
		golem.setState(CopperGolemState.IDLE);
	}

	private MinePlan advance(MinerData data, MinePlan plan) {
		MinePlan next = plan.withProgress(plan.progress() + 1);
		data.setPlan(next);
		return next;
	}

	private boolean isDone(ServerLevel level, CopperGolem golem, MineLayout.Step step) {
		BlockPos pos = step.pos();
		BlockState state = level.getBlockState(pos);
		return switch (step.action()) {
			case DIG -> state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() || !this.canDig(level, golem, pos);
			case FLOOR -> state.isFaceSturdy(level, pos, Direction.UP);
			case FENCE -> state.is(BlockTags.WOODEN_FENCES) || !state.canBeReplaced();
			case BEAM -> state.is(BlockTags.PLANKS) || !state.canBeReplaced();
			case RAIL -> state.is(BlockTags.RAILS) || !state.canBeReplaced() || !Blocks.RAIL.defaultBlockState().canSurvive(level, pos);
			// Already bright (daylight or another torch) means no torch needed.
			case TORCH -> !state.canBeReplaced() || level.getMaxLocalRawBrightness(pos) >= 10;
		};
	}

	/** Blocks the golem leaves alone: unbreakable ones, containers and anything its pickaxe cannot harvest. */
	private boolean canDig(ServerLevel level, CopperGolem golem, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()) {
			return false;
		}
		return !state.requiresCorrectToolForDrops() || golem.getMainHandItem().isCorrectToolForDrops(state);
	}

	private static boolean isDiamond(BlockState state) {
		return state.is(ConventionalBlockTags.DIAMOND_ORES);
	}

	private static boolean isExcited(CopperGolem golem) {
		return Boolean.TRUE.equals(golem.getAttached(ModAttachments.EXCITED));
	}

	private static boolean isOre(BlockState state) {
		return state.is(ConventionalBlockTags.ORES);
	}

	// --- doing it ---

	private void tickWork(ServerLevel level, CopperGolem golem, MinerData data, MinePlan plan) {
		MineLayout.Step step = this.work;
		BlockPos pos = step.pos();
		// Sight only matters for digging, and only on the way: from its standing spot the block is always meant to be
		// within reach, even if a fence post or beam is in the way of the view.
		boolean blocked = step.action() == MineLayout.Action.DIG && !isAtStand(golem, step.stand()) && !canSee(level, golem, pos);
		if (!this.isInReach(golem, pos) || blocked) {
			// Something fell into the tunnel behind the golem (gravel, sand)? Every few seconds of walking, check and clear
			// that first, then try again.
			int stalled = this.travel.tick(golem, Vec3.atBottomCenterOf(step.stand()));
			if (stalled > 0 && stalled % 100 == 0) {
				MineLayout.Step blocker = this.findObstruction(level, golem, data);
				if (blocker != null && !blocker.pos().equals(pos)) {
					this.setWork(blocker, true);
					return;
				}
			}
			if (stalled > MAX_TRAVEL_TICKS) {
				// Clumsy, but not stubborn: give up on this one.
				this.unreachable.add(pos);
				level.destroyBlockProgress(golem.getId(), pos, -1);
				this.finishWork(level, golem, data);
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(golem, step.stand(), WALK_SPEED * Upkeep.speed(golem), 0);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));

		if (step.action() == MineLayout.Action.DIG) {
			this.tickDig(level, golem, data, step);
			return;
		}
		if (++this.workTicks == 1) {
			golem.setState(CopperGolemState.DROPPING_ITEM);
		}
		if (this.workTicks >= PLACE_TICKS) {
			this.build(level, golem, data, plan, step);
			this.finishWork(level, golem, data);
			this.pauseTicks = 10;
		}
	}

	private void tickDig(ServerLevel level, CopperGolem golem, MinerData data, MineLayout.Step step) {
		BlockPos pos = step.pos();
		if (this.digTicks == 0) {
			if (!this.canDig(level, golem, pos) || level.getBlockState(pos).isAir()) {
				this.finishWork(level, golem, data);
				return;
			}
			if (!this.sealFluids(level, golem, data, pos)) {
				return;
			}
			if (!this.hesitated && golem.getRandom().nextFloat() < HESITATE_CHANCE) {
				// Stops, looks at the wall, shakes its head, then gets on with it.
				this.hesitated = true;
				golem.setState(CopperGolemState.GETTING_NO_ITEM);
				golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
				this.pauseTicks = 30 + golem.getRandom().nextInt(30);
				return;
			}
			this.digTicks = this.digDuration(level, golem, pos);
			golem.setState(CopperGolemState.GETTING_ITEM);
		}
		this.workTicks++;
		BlockState state = level.getBlockState(pos);
		level.destroyBlockProgress(golem.getId(), pos, Math.min(9, this.workTicks * 10 / this.digTicks));
		if (this.workTicks % 5 == 1) {
			SoundType sound = state.getSoundType();
			level.playSound(null, pos, sound.getHitSound(), SoundSource.NEUTRAL, sound.getVolume() * 0.5F, sound.getPitch() * 0.8F);
		}
		if (this.workTicks % 20 == 0) {
			// Restart the reaching animation so it reads as swinging the pickaxe.
			golem.setState(golem.getState() == CopperGolemState.GETTING_ITEM ? CopperGolemState.DROPPING_ITEM : CopperGolemState.GETTING_ITEM);
		}
		if (this.workTicks >= this.digTicks) {
			level.destroyBlockProgress(golem.getId(), pos, -1);
			this.dig(level, golem, data, step, state);
			this.finishWork(level, golem, data);
		}
	}

	private int digDuration(ServerLevel level, CopperGolem golem, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		float speed = Math.max(1.0F, golem.getMainHandItem().getDestroySpeed(state));
		float clumsiness = DIG_SLOWNESS / Upkeep.speed(golem) * (1.0F + golem.getRandom().nextFloat() * 0.6F);
		return Math.clamp((int) Math.ceil(hardness * 30.0F / speed * clumsiness), MIN_DIG_TICKS, MAX_DIG_TICKS);
	}

	private void dig(ServerLevel level, CopperGolem golem, MinerData data, MineLayout.Step step, BlockState state) {
		BlockPos pos = step.pos();
		ItemStack tool = golem.getMainHandItem();
		BlockEntity blockEntity = level.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, golem, tool);
		level.destroyBlock(pos, false, golem);
		state.spawnAfterBreak(level, pos, tool, true);
		if (state.getDestroySpeed(level, pos) > 0.0F) {
			Tools.wear(golem, tool);
		}
		Upkeep.spend(golem, ENERGY_PER_DIG);
		for (ItemStack drop : drops) {
			this.stash(golem, data, drop);
		}
		if (isDiamond(state) && !isExcited(golem)) {
			golem.setAttached(ModAttachments.EXCITED, true);
			level.playSound(null, golem, SoundEvents.AMETHYST_BLOCK_CHIME, golem.getSoundSource(), 1.0F, 1.6F);
			level.playSound(null, golem, SoundEvents.COPPER_GOLEM_ITEM_GET, golem.getSoundSource(), 1.0F, 1.5F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + 1.2, golem.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
			Guide.awardNearby(level, golem.position(), "diamonds");
		}

		this.recentDigs.addLast(step);
		if (this.recentDigs.size() > RECENT_DIGS) {
			this.recentDigs.removeFirst();
		}
		// Ores it can now see get dug out too, a few blocks into the vein, standing in the hole it just made.
		BlockPos stand = floorBelow(level, pos);
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			boolean queued = this.oreDigs.stream().anyMatch(ore -> ore.pos().equals(next));
			if (this.oreBudget > 0 && !queued && level.isLoaded(next) && isOre(level.getBlockState(next))) {
				this.oreDigs.add(new MineLayout.Step(MineLayout.Action.DIG, next, stand, Direction.UP));
				this.oreBudget--;
			}
		}
	}

	/**
	 * A block standing in a part of the tunnel that was already dug out (usually gravel or sand that fell in later),
	 * the closest one to the golem. Supports, rails and torches the golem built itself do not count.
	 */
	private MineLayout.@Nullable Step findObstruction(ServerLevel level, CopperGolem golem, MinerData data) {
		MinePlan plan = data.plan();
		if (plan == null) {
			return null;
		}
		Set<BlockPos> built = new HashSet<>();
		for (MineLayout.Step step : this.steps) {
			// Rails and torches have no collision, so only fence posts and beams need leaving out.
			if (step.action() == MineLayout.Action.FENCE || step.action() == MineLayout.Action.BEAM) {
				built.add(step.pos());
			}
		}
		MineLayout.Step best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < Math.min(plan.progress(), this.steps.size()); i++) {
			MineLayout.Step step = this.steps.get(i);
			BlockPos pos = step.pos();
			if (step.action() != MineLayout.Action.DIG || built.contains(pos) || this.unreachable.contains(pos) || !level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			double distance = pos.distToCenterSqr(golem.position());
			if (!state.getCollisionShape(level, pos).isEmpty() && this.canDig(level, golem, pos) && distance < bestDistance) {
				best = step;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** The lowest open block below {@code pos}, i.e. where a golem would stand. */
	private static BlockPos floorBelow(ServerLevel level, BlockPos pos) {
		BlockPos stand = pos;
		for (int i = 0; i < 3 && !level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP); i++) {
			stand = stand.below();
		}
		return stand;
	}

	private void stash(CopperGolem golem, MinerData data, ItemStack stack) {
		ItemStack rest = stack;
		CopperChestMinecart cart = CartChaining.findCart(golem);
		if (cart != null) {
			rest = Inventories.insert(cart, rest);
		}
		if (!rest.isEmpty()) {
			MinerData.addTo(data.loot(), rest);
		}
	}

	private boolean isLootFull(CopperGolem golem, MinerData data) {
		CopperChestMinecart cart = CartChaining.findCart(golem);
		boolean cartFull = cart == null || !Inventories.hasEmptySlot(cart);
		return cartFull && data.lootCount() >= MinerData.LOOT_LIMIT;
	}

	/**
	 * Before opening a block, plugs any water or lava next to it. Returns false while it is still busy (or gave up).
	 */
	private boolean sealFluids(ServerLevel level, CopperGolem golem, MinerData data, BlockPos pos) {
		if (!level.getFluidState(pos).isEmpty()) {
			return this.plugOrFlee(level, golem, data, pos);
		}
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			if (!level.getFluidState(next).isEmpty() && !golem.getBoundingBox().intersects(new AABB(next))) {
				return this.plugOrFlee(level, golem, data, next);
			}
		}
		return true;
	}

	private boolean plugOrFlee(ServerLevel level, CopperGolem golem, MinerData data, BlockPos pos) {
		ItemStack filler = this.takeMaterial(golem, data, MineBehavior::isFiller);
		if (filler.isEmpty()) {
			// Nothing to plug the hole with: too dangerous, the golem packs up and goes home.
			MinePlan plan = data.plan();
			if (plan != null) {
				data.setPlan(plan.withProgress(this.steps(level, plan).size()));
			}
			this.work = null;
			this.startReturn(level, golem, data, true);
			return false;
		}
		this.placeBlock(level, golem, pos, ((BlockItem) filler.getItem()).getBlock().defaultBlockState());
		golem.setState(CopperGolemState.DROPPING_ITEM);
		this.pauseTicks = 15;
		return false;
	}

	private void build(ServerLevel level, CopperGolem golem, MinerData data, MinePlan plan, MineLayout.Step step) {
		BlockPos pos = step.pos();
		if (step.action() == MineLayout.Action.TORCH) {
			this.placeTorch(level, golem, data, pos, step.facing());
			return;
		}
		Predicate<ItemStack> material = this.materialFor(step.action(), plan);
		ItemStack item = material == null ? ItemStack.EMPTY : this.takeMaterial(golem, data, material);
		if (step.action() == MineLayout.Action.FLOOR && item.isEmpty()) {
			// No planks left for a mineshaft floor: any filler will do.
			item = this.takeMaterial(golem, data, MineBehavior::isFiller);
		}
		if (item.getItem() instanceof BlockItem blockItem) {
			this.placeBlock(level, golem, pos, blockItem.getBlock().defaultBlockState());
		}
	}

	private void placeTorch(ServerLevel level, CopperGolem golem, MinerData data, BlockPos pos, Direction facing) {
		BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
		BlockState standing = Blocks.TORCH.defaultBlockState();
		BlockPos target = pos;
		BlockState state;
		if (wall.canSurvive(level, pos)) {
			state = wall;
		} else if (standing.canSurvive(level, pos)) {
			state = standing;
		} else if (level.getBlockState(pos.below()).canBeReplaced() && standing.canSurvive(level, pos.below())) {
			target = pos.below();
			state = standing;
		} else {
			return;
		}
		if (!this.takeMaterial(golem, data, stack -> stack.is(Items.TORCH)).isEmpty()) {
			this.placeBlock(level, golem, target, state);
		}
	}

	private void placeBlock(ServerLevel level, CopperGolem golem, BlockPos pos, BlockState state) {
		BlockState placed = Block.updateFromNeighbourShapes(state, level, pos);
		level.setBlockAndUpdate(pos, placed);
		Upkeep.spend(golem, ENERGY_PER_BUILD);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(golem, placed));
		SoundType sound = placed.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
	}

	// --- materials ---

	private @Nullable Predicate<ItemStack> materialFor(MineLayout.Action action, MinePlan plan) {
		return switch (action) {
			case DIG -> null;
			case FLOOR -> stack -> stack.is(ItemTags.PLANKS) || isFiller(stack);
			case FENCE -> stack -> stack.is(ItemTags.WOODEN_FENCES);
			case BEAM -> stack -> stack.is(ItemTags.PLANKS);
			case RAIL -> stack -> stack.is(Items.RAIL);
			case TORCH -> stack -> stack.is(Items.TORCH);
		};
	}

	/** Plain blocks a golem uses to fill holes and plug water or lava. */
	static boolean isFiller(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().is(ModTags.FILLER);
	}

	private boolean hasMaterial(CopperGolem golem, MinerData data, Predicate<ItemStack> material) {
		if (data.supplies().stream().anyMatch(material) || data.loot().stream().anyMatch(material)) {
			return true;
		}
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return cart != null && Inventories.contains(cart, material);
	}

	/** Takes one matching item from the supplies, the pouch or the cart. */
	private ItemStack takeMaterial(CopperGolem golem, MinerData data, Predicate<ItemStack> material) {
		for (List<ItemStack> pouch : List.of(data.supplies(), data.loot())) {
			for (ItemStack stack : pouch) {
				if (!stack.isEmpty() && material.test(stack)) {
					ItemStack one = stack.split(1);
					pouch.removeIf(ItemStack::isEmpty);
					return one;
				}
			}
		}
		CopperChestMinecart cart = CartChaining.findCart(golem);
		return cart == null ? ItemStack.EMPTY : Inventories.takeFirst(cart, 1, material);
	}

	// --- going home ---

	private void startReturn(ServerLevel level, CopperGolem golem, MinerData data, boolean stopAfter) {
		this.mode = Mode.RETURNING;
		this.stopAfterReturn = stopAfter;
		this.travelTicks = 0;
		this.workTicks = 0;
		MinePlan plan = data.plan();
		this.home = Homes.valid(level, golem);
		golem.setState(CopperGolemState.IDLE);
	}

	private void endReturn(CopperGolem golem) {
		this.mode = Mode.WORKING;
		this.home = null;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		if (this.stopAfterReturn) {
			// Nothing left to do: idle for a while like a sorting golem with an empty chest.
			golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 140);
		}
	}

	private void tickReturn(ServerLevel level, CopperGolem golem, MinerData data) {
		Container chest = this.home == null ? null : homeContainer(level, this.home);
		if (chest == null) {
			// No copper chest at the entrance: leave everything at the golem's feet.
			for (ItemStack stack : data.loot()) {
				golem.spawnAtLocation(level, stack);
			}
			data.loot().clear();
			this.endReturn(golem);
			return;
		}
		if (!this.isInReach(golem, this.home)) {
			this.travelTicks++;
			// Hurrying when it has diamonds to show.
			float hurry = isExcited(golem) ? 1.3F : 1.0F;
			BehaviorUtils.setWalkAndLookTargetMemories(golem, this.home, WALK_SPEED * hurry * Upkeep.speed(golem), 1);
			return;
		}
		stopInPlace(golem);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.home));
		this.workTicks++;
		if (this.workTicks == 1) {
			chest.startOpen(golem);
			golem.setOpenedChestPos(this.home);
			golem.setState(CopperGolemState.DROPPING_ITEM);
		}
		if (this.workTicks == 9) {
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP);
		}
		if (this.workTicks >= HOME_INTERACTION_TICKS) {
			this.unload(level, golem, data, chest);
			if (golem.removeAttached(ModAttachments.EXCITED) != null) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + 1.2, golem.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
				golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP, 1.0F, 1.5F);
			}
			MinePlan plan = data.plan();
			if (plan != null && !this.stopAfterReturn) {
				this.restock(data, plan, chest);
			}
			if (chest.getEntitiesWithContainerOpen().contains(golem)) {
				chest.stopOpen(golem);
			}
			this.endReturn(golem);
		}
	}

	private void unload(ServerLevel level, CopperGolem golem, MinerData data, Container chest) {
		// Keep some plain stone back for plugging leaks and patching floors.
		int reserve = data.supplies().stream().filter(MineBehavior::isFiller).mapToInt(ItemStack::getCount).sum();
		for (ItemStack stack : data.loot()) {
			if (reserve < FILLER_RESERVE && isFiller(stack)) {
				ItemStack kept = stack.split(FILLER_RESERVE - reserve);
				reserve += kept.getCount();
				MinerData.addTo(data.supplies(), kept);
			}
			ItemStack rest = Inventories.insert(chest, stack);
			if (!rest.isEmpty()) {
				golem.spawnAtLocation(level, rest);
			}
		}
		data.loot().clear();
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

	private void restock(MinerData data, MinePlan plan, Container chest) {
		this.restockOne(data, chest, stack -> stack.is(Items.TORCH), 16);
		// Every rail stays in the pouch until the shaft is dug, so it takes enough for the whole plan.
		this.restockOne(data, chest, stack -> stack.is(Items.RAIL), plan.length());
		this.restockOne(data, chest, stack -> stack.is(ItemTags.PLANKS), 32);
		this.restockOne(data, chest, stack -> stack.is(ItemTags.WOODEN_FENCES), 16);
	}

	/**
	 * Whether the golem carries (plus {@code chest}, if given, holds) what slice {@code slice} needs: a rail for it and
	 * every slice before it (they are laid at the end), and on support slices 4 fences and 3 planks.
	 */
	private boolean hasSliceMaterials(CopperGolem golem, MinerData data, int slice, @Nullable Container chest) {
		boolean support = MineLayout.isSupportSlice(slice);
		return this.countCarried(golem, data, chest, stack -> stack.is(Items.RAIL)) >= slice + 1
			&& (!support || this.countCarried(golem, data, chest, stack -> stack.is(ItemTags.WOODEN_FENCES)) >= 4)
			&& (!support || this.countCarried(golem, data, chest, stack -> stack.is(ItemTags.PLANKS)) >= 3);
	}

	private int countCarried(CopperGolem golem, MinerData data, @Nullable Container chest, Predicate<ItemStack> material) {
		int count = data.supplies().stream().filter(material).mapToInt(ItemStack::getCount).sum()
			+ data.loot().stream().filter(material).mapToInt(ItemStack::getCount).sum();
		CopperChestMinecart cart = CartChaining.findCart(golem);
		for (Container container : Arrays.asList(cart, chest)) {
			if (container != null) {
				for (ItemStack stack : container) {
					if (material.test(stack)) {
						count += stack.getCount();
					}
				}
			}
		}
		return count;
	}

	private void restockOne(MinerData data, Container chest, Predicate<ItemStack> material, int target) {
		int have = data.supplies().stream().filter(material).mapToInt(ItemStack::getCount).sum();
		while (have < target) {
			ItemStack taken = Inventories.takeFirst(chest, target - have, material);
			if (taken.isEmpty()) {
				return;
			}
			have += taken.getCount();
			MinerData.addTo(data.supplies(), taken);
		}
	}

	private boolean homeHas(ServerLevel level, CopperGolem golem, Predicate<ItemStack> material) {
		BlockPos pos = Homes.valid(level, golem);
		Container chest = pos == null ? null : homeContainer(level, pos);
		return chest != null && Inventories.contains(chest, material);
	}

	/** What a miner takes along from its home chest; sorting golems leave these there. */
	public static boolean isSupply(ItemStack stack) {
		return stack.is(Items.TORCH) || stack.is(Items.RAIL) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_FENCES);
	}

	/** Home chests of the mining golems around {@code pos}. */
	public static Set<BlockPos> minerHomesNear(ServerLevel level, BlockPos pos) {
		Set<BlockPos> homes = new HashSet<>();
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, new AABB(pos).inflate(MINER_HOME_SEARCH_RADIUS))) {
			MinerData data = MinerData.get(golem);
			MinePlan plan = data == null ? null : data.plan();
			if (plan != null && plan.isMarked()) {
				BlockPos home = Homes.get(golem);
				if (home != null) {
					homes.add(home);
				}
			}
		}
		return homes;
	}

	private static @Nullable Container homeContainer(ServerLevel level, BlockPos pos) {
		return Homes.container(level, pos);
	}

	// --- movement ---

	private boolean isInReach(CopperGolem golem, BlockPos pos) {
		return new AABB(pos).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox());
	}

	private static boolean isAtStand(CopperGolem golem, BlockPos stand) {
		return golem.blockPosition().equals(stand) || golem.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) < 0.8;
	}

	/** No digging through walls: at least one face of the block must be in view, like vanilla's chest check. */
	private static boolean canSee(ServerLevel level, CopperGolem golem, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		for (Direction direction : Direction.values()) {
			Vec3 face = center.add(direction.getStepX() * 0.49, direction.getStepY() * 0.49, direction.getStepZ() * 0.49);
			BlockHitResult hit = level.clip(new ClipContext(golem.getEyePosition(), face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, golem));
			if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
				return true;
			}
		}
		return false;
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
