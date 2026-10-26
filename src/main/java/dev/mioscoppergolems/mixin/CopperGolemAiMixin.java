package dev.mioscoppergolems.mixin;

import dev.mioscoppergolems.golem.SortItemsBehavior;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolemAi.class)
public abstract class CopperGolemAiMixin {
	@Inject(method = "initIdleActivity", at = @At("HEAD"), cancellable = true)
	private static void mioscoppergolems$replaceSorting(CallbackInfoReturnable<ActivityData<CopperGolem>> cir) {
		cir.setReturnValue(SortItemsBehavior.createIdleActivity());
	}
}
