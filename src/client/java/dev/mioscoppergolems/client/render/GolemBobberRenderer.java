package dev.mioscoppergolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mioscoppergolems.entity.GolemBobber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.phys.Vec3;

/**
 * The fishing golem's float, drawn like the vanilla fishing hook: a floating bobber that rocks on the water and dips
 * under when a fish bites, and a fishing line from the tip of the golem's rod.
 */
public class GolemBobberRenderer extends EntityRenderer<GolemBobber, GolemBobberRenderer.State> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE);
	private static final int LINE_STEPS = 16;

	public static class State extends FishingHookRenderState {
		public float bob;
	}

	public GolemBobberRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public boolean shouldRender(GolemBobber entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks) && entity.owner() != null;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GolemBobber entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float age = entity.tickCount + partialTicks;
		state.bob = entity.isBiting() ? -0.18F : Mth.sin(age * 0.12F) * 0.03F;
		CopperGolem owner = entity.owner();
		if (owner == null) {
			state.lineOriginOffset = Vec3.ZERO;
			return;
		}
		Vec3 hookPos = entity.getPosition(partialTicks).add(0.0, 0.25 + state.bob, 0.0);
		state.lineOriginOffset = rodTip(owner, partialTicks).subtract(hookPos);
	}

	/** Where the rod in the golem's right hand ends: a little to its right, in front of it and above its head. */
	private static Vec3 rodTip(CopperGolem golem, float partialTicks) {
		float yaw = Mth.lerp(partialTicks, golem.yBodyRotO, golem.yBodyRot) * (float) (Math.PI / 180.0);
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		double right = -0.3;
		double forward = 0.6;
		return golem.getPosition(partialTicks).add(-cos * right - sin * forward, 1.05, -sin * right + cos * forward);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, state.bob, 0.0F);
		poseStack.pushPose();
		poseStack.scale(0.5F, 0.5F, 0.5F);
		poseStack.rotate(camera.orientation);
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
			vertex(buffer, pose, state.lightCoords, 0.0F, 0, 0, 1);
			vertex(buffer, pose, state.lightCoords, 1.0F, 0, 1, 1);
			vertex(buffer, pose, state.lightCoords, 1.0F, 1, 1, 0);
			vertex(buffer, pose, state.lightCoords, 0.0F, 1, 0, 0);
		});
		poseStack.popPose();
		float xa = (float) state.lineOriginOffset.x;
		float ya = (float) state.lineOriginOffset.y;
		float za = (float) state.lineOriginOffset.z;
		float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
		submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			for (int i = 0; i < LINE_STEPS; i++) {
				float a0 = (float) i / LINE_STEPS;
				float a1 = (float) (i + 1) / LINE_STEPS;
				stringVertex(xa, ya, za, buffer, pose, a0, a1, width);
				stringVertex(xa, ya, za, buffer, pose, a1, a0, width);
			}
		});
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	private static void vertex(VertexConsumer builder, PoseStack.Pose pose, int lightCoords, float x, int y, int u, int v) {
		builder.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
			.setColor(-1)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(lightCoords)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** A sagging line, as in vanilla. */
	private static void stringVertex(float xa, float ya, float za, VertexConsumer buffer, PoseStack.Pose pose, float aa, float nexta, float width) {
		float x = xa * aa;
		float y = ya * (aa * aa + aa) * 0.5F + 0.25F;
		float z = za * aa;
		float nx = xa * nexta - x;
		float ny = ya * (nexta * nexta + nexta) * 0.5F + 0.25F - y;
		float nz = za * nexta - z;
		float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		buffer.addVertex(pose, x, y, z).setColor(-16777216).setNormal(pose, nx / length, ny / length, nz / length).setLineWidth(width);
	}

	@Override
	protected boolean affectedByCulling(GolemBobber entity) {
		return false;
	}
}
