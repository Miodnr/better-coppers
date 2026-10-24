package dev.bettercoppers.mixin;

import dev.bettercoppers.upkeep.Upkeep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public abstract class CopperGolemMixin {
	/** A golem turning into a statue would otherwise take its plan, pouch and supplies with it. */
	@Inject(method = "turnToStatue", at = @At("HEAD"))
	private void bettercoppers$dropJobItems(ServerLevel level, CallbackInfo ci) {
		Upkeep.dropJobItems((CopperGolem) (Object) this);
	}
}
