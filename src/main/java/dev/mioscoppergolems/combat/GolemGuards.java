package dev.mioscoppergolems.combat;

import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.mixin.MobAccessor;
import dev.mioscoppergolems.mixin.NearestAttackableTargetGoalAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * Copper golems are prey now: every monster that hunts villagers or iron golems hunts copper golems too. Iron golems
 * in turn stay close to the copper golems that live somewhere and go for whatever attacks them.
 */
public final class GolemGuards {
	private GolemGuards() {
	}

	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof IronGolem ironGolem) {
			addGuardGoals(ironGolem);
		} else if (entity instanceof Mob mob && !(entity instanceof CopperGolem)) {
			addHuntGoal(mob);
		}
	}

	/** Copper golems with a home or a route are worth guarding; strays wander off anyway. */
	public static boolean isSettled(CopperGolem golem) {
		return golem.isAlive() && (Homes.get(golem) != null || Couriers.isCourier(golem));
	}

	private static void addHuntGoal(Mob mob) {
		GoalSelector targets = ((MobAccessor) mob).mioscoppergolems$getTargetSelector();
		int priority = -1;
		for (WrappedGoal wrapped : targets.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> goal) {
				Class<?> type = ((NearestAttackableTargetGoalAccessor) goal).mioscoppergolems$getTargetType();
				if (type == CopperGolem.class) {
					// Already added when this mob loaded before.
					return;
				}
				if (IronGolem.class.isAssignableFrom(type) || AbstractVillager.class.isAssignableFrom(type)) {
					priority = priority < 0 ? wrapped.getPriority() : Math.min(priority, wrapped.getPriority());
				}
			}
		}
		if (priority >= 0) {
			targets.addGoal(priority, new NearestAttackableTargetGoal<>(mob, CopperGolem.class, true));
		}
	}

	private static void addGuardGoals(IronGolem golem) {
		MobAccessor accessor = (MobAccessor) golem;
		if (accessor.mioscoppergolems$getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof GuardCopperGolemsGoal)) {
			return;
		}
		accessor.mioscoppergolems$getGoalSelector().addGoal(2, new GuardCopperGolemsGoal(golem));
		accessor.mioscoppergolems$getTargetSelector().addGoal(1, new DefendCopperGolemsTargetGoal(golem));
	}
}
