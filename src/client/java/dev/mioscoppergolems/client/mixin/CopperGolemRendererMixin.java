package dev.mioscoppergolems.client.mixin;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.client.render.GolemRenderExtras;
import dev.mioscoppergolems.registry.ModAttachments;
import java.util.function.Function;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolemRenderer.class)
public abstract class CopperGolemRendererMixin {
	@Unique
	private static final Identifier[] mioscoppergolems$EYES = {
		MiosCoppergolems.id("textures/entity/copper_golem/eyes_iron.png"),
		MiosCoppergolems.id("textures/entity/copper_golem/eyes_gold.png"),
		MiosCoppergolems.id("textures/entity/copper_golem/eyes_diamond.png"),
	};

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/animal/golem/CopperGolem;Lnet/minecraft/client/renderer/entity/state/CopperGolemRenderState;F)V",
		at = @At("TAIL")
	)
	private void mioscoppergolems$extractExtras(CopperGolem golem, CopperGolemRenderState state, float partialTicks, CallbackInfo ci) {
		GolemRenderExtras extras = (GolemRenderExtras) state;
		extras.mioscoppergolems$setTrim(golem.getAttached(ModAttachments.TRIM));
		Integer eyes = golem.getAttached(ModAttachments.EYES);
		extras.mioscoppergolems$setEyes(eyes == null ? 0 : eyes);
		extras.mioscoppergolems$setExcited(Boolean.TRUE.equals(golem.getAttached(ModAttachments.EXCITED)));
	}

	/** Upgraded eyes glow in their own colour instead of the vanilla one. */
	@Inject(method = "getEyeTextureLocationProvider", at = @At("RETURN"), cancellable = true)
	private static void mioscoppergolems$upgradedEyes(CallbackInfoReturnable<Function<CopperGolemRenderState, Identifier>> cir) {
		Function<CopperGolemRenderState, Identifier> vanilla = cir.getReturnValue();
		cir.setReturnValue(state -> {
			int eyes = ((GolemRenderExtras) state).mioscoppergolems$getEyes();
			return eyes >= 1 && eyes <= mioscoppergolems$EYES.length ? mioscoppergolems$EYES[eyes - 1] : vanilla.apply(state);
		});
	}
}
