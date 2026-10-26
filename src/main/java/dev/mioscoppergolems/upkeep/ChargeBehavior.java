package dev.mioscoppergolems.upkeep;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.api.ChargePad;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.block.ChargingPadBlock;
import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.registry.ModBlocks;
import dev.mioscoppergolems.repair.Errands;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * At night, and whenever it runs out of energy, a working golem walks to a free {@link ChargingPadBlock charging pad}
 * near its home and stands on it. The pad charges it with every redstone pulse, so it needs a clock; add-on pads
 * (like the Automatisation Charging Station) charge it every tick instead. Without a pad nearby the golem just waits.
 * <p>
 * When the players sleep through the night, every working golem with a free pad near its home wakes up fully charged,
 * as if it had spent the night on it ({@link #rechargeOvernight}).
 */
public class ChargeBehavior extends Behavior<CopperGolem> {
	private static final int SEARCH_RADIUS = 16;
	private static final int SEARCH_HEIGHT = 6;
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
		// Once it has come to charge because it ran empty, it stays until it is full, not just until it has a spark.
		return !golem.isPanicking()
			&& (wantsCharge(level, golem) || this.pad != null && !golem.isLeashed() && Upkeep.get(golem).energy() < GolemUpkeep.MAX_ENERGY);
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
		if (this.pad == null || !isPad(level.getBlockState(this.pad))) {
			this.pad = null;
			if (--this.searchCooldown > 0) {
				return;
			}
			this.searchCooldown = RESEARCH_TICKS;
			this.pad = findPad(level, Upkeep.homeOf(level, golem), golem, Set.of());
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
		// A charging pad charges on redstone pulses by itself; add-on pads charge every tick.
		ChargePad special = MiosCoppergolemsApi.chargePad(level.getBlockState(this.pad));
		GolemUpkeep upkeep = Upkeep.get(golem);
		if (special != null && upkeep.energy() < GolemUpkeep.MAX_ENERGY) {
			upkeep.setEnergy(upkeep.energy() + special.chargePerTick());
			if (level.getGameTime() % 10 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, golem.getX(), golem.getY() + 0.9, golem.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
			}
			if (upkeep.energy() >= GolemUpkeep.MAX_ENERGY) {
				Upkeep.playChargeSound(level, golem);
				Guide.awardNearby(level, golem.position(), "recharge");
			}
		}
	}

	/**
	 * The night was slept through: every working golem with a free pad near its home is fully charged, as if it had
	 * spent the night there. Each pad charges one golem.
	 */
	public static void rechargeOvernight(ServerLevel level) {
		Set<BlockPos> claimed = new HashSet<>();
		for (CopperGolem golem : level.getEntities(EntityTypeTest.forClass(CopperGolem.class), golem -> golem.isAlive() && Upkeep.isJobGolem(golem))) {
			GolemUpkeep upkeep = Upkeep.get(golem);
			if (upkeep.energy() >= GolemUpkeep.MAX_ENERGY) {
				continue;
			}
			BlockPos pad = findPad(level, Upkeep.homeOf(level, golem), golem, claimed);
			if (pad != null) {
				claimed.add(pad);
				upkeep.setEnergy(GolemUpkeep.MAX_ENERGY);
			}
		}
	}

	private static @Nullable BlockPos findPad(ServerLevel level, BlockPos around, CopperGolem golem, Set<BlockPos> claimed) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos mutable : BlockPos.betweenClosed(around.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS), around.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS))) {
			if (!level.isLoaded(mutable) || !isPad(level.getBlockState(mutable))) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (claimed.contains(pos) || !isStandable(level, pos) || isTaken(level, pos, golem)) {
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

	/** A charging pad, or a pad from an add-on. */
	private static boolean isPad(BlockState state) {
		return state.is(ModBlocks.CHARGING_PAD) || MiosCoppergolemsApi.chargePad(state) != null;
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
