package dev.mioscoppergolems.combat;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.jspecify.annotations.Nullable;

/** An iron golem with copper golems around keeps near them instead of strolling off. */
public class GuardCopperGolemsGoal extends Goal {
	private static final double SEARCH_RANGE = 32.0;
	private static final double STAY_WITHIN = 10.0;
	private static final double CLOSE_ENOUGH = 5.0;
	private static final double SPEED = 0.6;

	private final IronGolem golem;
	private @Nullable CopperGolem ward;

	public GuardCopperGolemsGoal(IronGolem golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (this.golem.getTarget() != null || this.golem.getRandom().nextInt(reducedTickDelay(40)) != 0) {
			return false;
		}
		CopperGolem nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (CopperGolem candidate : this.golem.level().getEntitiesOfClass(CopperGolem.class, this.golem.getBoundingBox().inflate(SEARCH_RANGE), GolemGuards::isSettled)) {
			double distance = this.golem.distanceToSqr(candidate);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		if (nearest == null || nearestDistance < STAY_WITHIN * STAY_WITHIN) {
			return false;
		}
		this.ward = nearest;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.ward != null
			&& this.ward.isAlive()
			&& this.golem.getTarget() == null
			&& !this.golem.getNavigation().isDone()
			&& this.golem.distanceToSqr(this.ward) > CLOSE_ENOUGH * CLOSE_ENOUGH;
	}

	@Override
	public void start() {
		if (this.ward != null) {
			this.golem.getNavigation().moveTo(this.ward, SPEED);
		}
	}

	@Override
	public void stop() {
		this.ward = null;
		this.golem.getNavigation().stop();
	}
}
