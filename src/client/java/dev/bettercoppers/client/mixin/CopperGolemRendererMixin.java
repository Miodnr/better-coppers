package dev.bettercoppers.client.mixin;

import dev.bettercoppers.client.render.TrimmedRenderState;
import dev.bettercoppers.registry.ModAttachments;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolemRenderer.class)
public abstract class CopperGolemRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/animal/golem/CopperGolem;Lnet/minecraft/client/renderer/entity/state/CopperGolemRenderState;F)V",
		at = @At("TAIL")
	)
	private void bettercoppers$extractTrim(CopperGolem golem, CopperGolemRenderState state, float partialTicks, CallbackInfo ci) {
		((TrimmedRenderState) state).bettercoppers$setTrim(golem.getAttached(ModAttachments.TRIM));
	}
}
