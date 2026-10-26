package dev.mioscoppergolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mioscoppergolems.MiosCoppergolems;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * An excited golem (a miner that found diamonds) blinks: the knob on its antenna lights up, on and off a few times a
 * second, until it has brought the diamonds home.
 */
public class GolemAntennaLayer extends RenderLayer<CopperGolemRenderState, CopperGolemModel> {
	private static final Identifier TEXTURE = MiosCoppergolems.id("textures/entity/copper_golem/antenna_glow.png");
	/** Ticks per on-off cycle. */
	private static final float BLINK_TICKS = 8.0F;

	private final CopperGolemModel model;

	public GolemAntennaLayer(RenderLayerParent<CopperGolemRenderState, CopperGolemModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.model = new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CopperGolemRenderState state, float yRot, float xRot) {
		if (!((GolemRenderExtras) state).mioscoppergolems$isExcited() || state.isInvisible || state.ageInTicks % BLINK_TICKS >= BLINK_TICKS / 2) {
			return;
		}
		submitNodeCollector.order(1)
			.submitModel(this.model, state, poseStack, RenderTypes.eyes(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
	}
}
