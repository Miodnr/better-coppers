package dev.bettercoppers.mixin;

import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla keeps the weathering timer private; -2 means the golem is waxed, -1 that the timer is not running yet. */
@Mixin(CopperGolem.class)
public interface CopperGolemAccessor {
	@Accessor("nextWeatheringTick")
	long bettercoppers$getNextWeatheringTick();

	@Accessor("nextWeatheringTick")
	void bettercoppers$setNextWeatheringTick(long tick);
}
