package dev.mioscoppergolems.combat;

import dev.mioscoppergolems.guide.Guide;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

/** An iron golem goes for any monster that is after, or just hit, a copper golem near it. Creepers excepted. */
public class DefendCopperGolemsTargetGoal extends TargetGoal {
	private static final double RANGE = 16.0;
	/** How long ago a hit still counts. */
	private static final int MEMORY_TICKS = 100;

	private final TargetingConditions conditions = TargetingConditions.forCombat().range(RANGE * 2);
	private @Nullable LivingEntity attacker;

	public DefendCopperGolemsTargetGoal(IronGolem golem) {
		super(golem, false, true);
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (this.mob.getRandom().nextInt(reducedTickDelay(10)) != 0) {
			return false;
		}
		this.attacker = null;
		double best = Double.MAX_VALUE;
		var area = this.mob.getBoundingBox().inflate(RANGE);
		for (CopperGolem golem : this.mob.level().getEntitiesOfClass(CopperGolem.class, area, CopperGolem::isAlive)) {
			LivingEntity hitBy = golem.getLastHurtByMob();
			if (hitBy != null && golem.tickCount - golem.getLastHurtByMobTimestamp() < MEMORY_TICKS) {
				best = this.consider(hitBy, best);
			}
		}
		for (Mob hunter : this.mob.level().getEntitiesOfClass(Mob.class, area, other -> other.getTarget() instanceof CopperGolem)) {
			best = this.consider(hunter, best);
		}
		return this.attacker != null;
	}

	private double consider(LivingEntity candidate, double best) {
		if (!(candidate instanceof Enemy) || candidate instanceof Creeper || !this.canAttack(candidate, this.conditions)) {
			return best;
		}
		double distance = this.mob.distanceToSqr(candidate);
		if (distance < best) {
			this.attacker = candidate;
			return distance;
		}
		return best;
	}

	@Override
	public void start() {
		this.mob.setTarget(this.attacker);
		if (this.mob.level() instanceof ServerLevel level) {
			Guide.awardNearby(level, this.mob.position(), "guard");
		}
		super.start();
	}
}
