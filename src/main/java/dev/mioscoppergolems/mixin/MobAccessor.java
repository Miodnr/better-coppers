package dev.mioscoppergolems.mixin;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Goals are added once a mob loads, to make monsters hunt copper golems and iron golems guard them. */
@Mixin(net.minecraft.world.entity.Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector mioscoppergolems$getGoalSelector();

	@Accessor("targetSelector")
	GoalSelector mioscoppergolems$getTargetSelector();
}
