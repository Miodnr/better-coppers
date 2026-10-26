package dev.mioscoppergolems.automatisation.client;

import dev.mioscoppergolems.automatisation.client.render.BeltDriveRenderer;
import dev.mioscoppergolems.automatisation.client.render.GrabberArmRenderer;
import dev.mioscoppergolems.automatisation.client.render.PressRenderer;
import dev.mioscoppergolems.automatisation.client.render.SawRenderer;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class AutomatisationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(AutoBlockEntities.BELT_DRIVE, BeltDriveRenderer::new);
		BlockEntityRenderers.register(AutoBlockEntities.PRESS, PressRenderer::new);
		BlockEntityRenderers.register(AutoBlockEntities.SAW, SawRenderer::new);
		BlockEntityRenderers.register(AutoBlockEntities.GRABBER_ARM, GrabberArmRenderer::new);
	}
}
