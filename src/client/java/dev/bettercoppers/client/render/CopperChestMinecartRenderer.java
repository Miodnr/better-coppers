package dev.bettercoppers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bettercoppers.entity.CopperChestMinecart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Vanilla chest minecart rendering, plus a sagging iron chain built from chain block models running to the holder.
 */
public class CopperChestMinecartRenderer extends AbstractMinecartRenderer<CopperChestMinecart, CopperChestMinecartRenderState> {
	private static final double CHAIN_HEIGHT_ON_CART = 0.55;
	private static final double CHAIN_SAG = 0.25;
	private final BlockModelResolver blockModelResolver;

	public CopperChestMinecartRenderer(EntityRendererProvider.Context context) {
		super(context, ModelLayers.CHEST_MINECART);
		this.blockModelResolver = context.getBlockModelResolver();
	}

	@Override
	public CopperChestMinecartRenderState createRenderState() {
		return new CopperChestMinecartRenderState();
	}

	@Override
	public void extractRenderState(CopperChestMinecart cart, CopperChestMinecartRenderState state, float partialTicks) {
		super.extractRenderState(cart, state, partialTicks);
		Entity holder = cart.getHolder();
		if (holder == null) {
			state.chainEnd = null;
			return;
		}
		state.chainEnd = holder.getRopeHoldPosition(partialTicks).subtract(cart.getPosition(partialTicks));
		this.blockModelResolver.update(state.chainModel, Blocks.IRON_CHAIN.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
	}

	@Override
	protected AABB getBoundingBoxForCulling(CopperChestMinecart cart, float partialTicks) {
		AABB box = super.getBoundingBoxForCulling(cart, partialTicks);
		Entity holder = cart.getHolder();
		return holder == null ? box : box.minmax(holder.getInterpolatedBoundingBox(partialTicks));
	}

	@Override
	public void submit(CopperChestMinecartRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		super.submit(state, poseStack, submitNodeCollector, camera);
		if (state.chainEnd != null && !state.chainModel.isEmpty()) {
			this.submitChain(state, poseStack, submitNodeCollector);
		}
	}

	private void submitChain(CopperChestMinecartRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
		Vec3 end = state.chainEnd;
		Vec3 flat = new Vec3(end.x, 0.0, end.z);
		Vec3 start = new Vec3(0.0, CHAIN_HEIGHT_ON_CART, 0.0).add(flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(0.45) : Vec3.ZERO);
		double length = end.distanceTo(start);
		int segments = Math.max(1, (int) Math.round(length / 0.75));

		Vec3 previous = start;
		for (int i = 1; i <= segments; i++) {
			double progress = (double) i / segments;
			// A parabola that is zero at both ends gives the chain some slack.
			double sag = CHAIN_SAG * 4.0 * progress * (1.0 - progress);
			Vec3 point = start.lerp(end, progress).subtract(0.0, sag, 0.0);
			Vec3 step = point.subtract(previous);
			float stepLength = (float) step.length();
			if (stepLength > 1.0E-4F) {
				poseStack.pushPose();
				poseStack.translate(previous.x, previous.y, previous.z);
				poseStack.rotate(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) step.x, (float) step.y, (float) step.z));
				// Alternate the twist so neighbouring links look interlocked.
				poseStack.rotate(new Quaternionf().rotationY(i % 2 == 0 ? 0.0F : (float) (Math.PI / 4)));
				poseStack.scale(1.0F, stepLength, 1.0F);
				poseStack.translate(-0.5F, 0.0F, -0.5F);
				state.chainModel.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			previous = point;
		}
	}
}
