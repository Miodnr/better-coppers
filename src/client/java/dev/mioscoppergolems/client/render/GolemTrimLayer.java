package dev.mioscoppergolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mioscoppergolems.MiosCoppergolems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.palette.PalettedTextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/**
 * Draws a copper golem's armour trim over its body. The trim textures are the vanilla armour trims refitted to the
 * golem's shape (see tools/TrimGen.java); the game's palette system colours them by material, exactly like armour.
 * Patterns without a golem texture (from other mods) are simply not drawn.
 */
public class GolemTrimLayer extends RenderLayer<CopperGolemRenderState, CopperGolemModel> {
	private final CopperGolemModel model;
	private final Map<Identifier, Boolean> hasTexture = new HashMap<>();

	public GolemTrimLayer(RenderLayerParent<CopperGolemRenderState, CopperGolemModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.model = new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CopperGolemRenderState state, float yRot, float xRot) {
		ArmorTrim trim = ((GolemRenderExtras) state).mioscoppergolems$getTrim();
		if (trim == null || state.isInvisible) {
			return;
		}
		TrimPattern pattern = trim.pattern().value();
		Identifier base = MiosCoppergolems.id("trims/entity/copper_golem/" + pattern.assetId().getPath());
		if (!this.hasTexture.computeIfAbsent(base, GolemTrimLayer::textureExists)) {
			return;
		}
		PalettedTextureManager.Handle texture = Minecraft.getInstance().getPalettedTextureManager().getOrPrepare(base, trim.material().value().paletteId());
		submitNodeCollector.order(1)
			.submitModel(
				this.model, state, poseStack, RenderTypes.armorTrim(texture.textureLocation(), pattern.decal()), lightCoords, OverlayTexture.NO_OVERLAY, -1, texture, state.outlineColor
			);
	}

	private static boolean textureExists(Identifier base) {
		return Minecraft.getInstance().getResourceManager().getResource(base.withPath(path -> "textures/" + path + ".png")).isPresent();
	}
}
