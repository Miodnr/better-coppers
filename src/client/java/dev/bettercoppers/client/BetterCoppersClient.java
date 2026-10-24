package dev.bettercoppers.client;

import dev.bettercoppers.client.render.CopperChestMinecartRenderer;
import dev.bettercoppers.client.render.GolemTrimLayer;
import dev.bettercoppers.client.screen.SurveyTableScreen;
import dev.bettercoppers.registry.ModEntities;
import dev.bettercoppers.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class BetterCoppersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.COPPER_CHEST_MINECART, CopperChestMinecartRenderer::new);
		MenuScreens.register(ModMenus.SURVEY_TABLE, SurveyTableScreen::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof CopperGolemRenderer golemRenderer) {
				helper.register(new GolemTrimLayer(golemRenderer, context));
			}
		});
	}
}
