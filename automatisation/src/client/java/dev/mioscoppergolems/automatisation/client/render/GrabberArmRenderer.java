package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mioscoppergolems.automatisation.arm.GrabberArmBlockEntity;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * A two-part copper arm on a turntable. The claw swings smoothly from the last spot to the next one while the arm is
 * cranked; the elbow position comes from simple two-bone inverse kinematics.
 */
public class GrabberArmRenderer extends MachineRenderer<GrabberArmBlockEntity, GrabberArmRenderer.ArmState> {
	private static final double UPPER = 2.3;
	private static final double LOWER = 2.3;
	private static final Vec3 SHOULDER = new Vec3(0.5, 0.45, 0.5);

	private final ItemModelResolver items;

	public static class ArmState extends MachineRenderer.State {
		/** Shoulder, elbow and claw tip, relative to the block. */
		public Vec3 elbow = Vec3.ZERO;
		public Vec3 tip = Vec3.ZERO;
		public float yaw;
		public final BlockModelRenderState turret = new BlockModelRenderState();
		public final BlockModelRenderState segment = new BlockModelRenderState();
		public final BlockModelRenderState claw = new BlockModelRenderState();
		public final ItemStackRenderState held = new ItemStackRenderState();
	}

	public GrabberArmRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		this.items = context.itemModelResolver();
	}

	@Override
	public ArmState createRenderState() {
		return new ArmState();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	protected Vec3 crankOffset() {
		return new Vec3(0.0, -0.25, 0.125);
	}

	@Override
	public void extractRenderState(GrabberArmBlockEntity arm, ArmState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		super.extractRenderState(arm, state, partialTicks, cameraPosition, breakProgress);
		Level level = arm.getLevel();
		BlockPos pos = arm.getBlockPos();
		Vec3 rest = SHOULDER.add(state.facing.getStepX() * 1.2, 1.6, state.facing.getStepZ() * 1.2);
		Vec3 from = arm.previous == null ? rest : spotPoint(level, pos, arm.previous);
		Vec3 goal;
		if (arm.target == null) {
			goal = from;
		} else {
			float t = Mth.clamp(Mth.lerp(partialTicks, arm.progressO, arm.progress) / GrabberArmBlockEntity.SWING_TICKS, 0.0F, 1.0F);
			// Ease in and out, like a heavy arm.
			t = t * t * (3.0F - 2.0F * t);
			goal = from.lerp(spotPoint(level, pos, arm.target), t);
		}
		this.solve(state, goal);
		this.models.update(state.turret, part(MachinePartBlock.Part.ARM_TURRET), DISPLAY);
		this.models.update(state.segment, part(MachinePartBlock.Part.ARM_SEGMENT), DISPLAY);
		this.models.update(state.claw, part(MachinePartBlock.Part.ARM_CLAW), DISPLAY);
		if (arm.held().isEmpty()) {
			state.held.clear();
		} else {
			this.items.updateForTopItem(state.held, arm.held(), ItemDisplayContext.FIXED, level, null, (int) pos.asLong());
		}
	}

	/** Just above the top of the spot's block, where the claw grabs or lets go. */
	private static Vec3 spotPoint(@Nullable Level level, BlockPos arm, BlockPos spot) {
		double top = 1.0;
		if (level != null) {
			double height = level.getBlockState(spot).getCollisionShape(level, spot).max(Direction.Axis.Y);
			top = height <= 0.0 ? 0.0 : height;
		}
		return new Vec3(spot.getX() - arm.getX() + 0.5, spot.getY() - arm.getY() + top + 0.35, spot.getZ() - arm.getZ() + 0.5);
	}

	/** Two-bone inverse kinematics with the elbow up. */
	private void solve(ArmState state, Vec3 goal) {
		Vec3 reach = goal.subtract(SHOULDER);
		double distance = Mth.clamp(reach.length(), 0.3, UPPER + LOWER - 0.01);
		Vec3 flat = new Vec3(reach.x, 0.0, reach.z);
		Vec3 horizontal = flat.lengthSqr() < 1.0E-6 ? new Vec3(state.facing.getStepX(), 0.0, state.facing.getStepZ()) : flat.normalize();
		double elevation = Math.atan2(reach.y, flat.length());
		double bend = Math.acos(Mth.clamp((UPPER * UPPER + distance * distance - LOWER * LOWER) / (2.0 * UPPER * distance), -1.0, 1.0));
		double shoulderAngle = elevation + bend;
		state.elbow = SHOULDER.add(horizontal.scale(Math.cos(shoulderAngle) * UPPER)).add(0.0, Math.sin(shoulderAngle) * UPPER, 0.0);
		state.tip = SHOULDER.add(reach.normalize().scale(distance));
		state.yaw = (float) Math.toDegrees(Math.atan2(horizontal.x, horizontal.z));
	}

	@Override
	public void submit(ArmState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitCrank(state, poseStack, collector);

		poseStack.pushPose();
		poseStack.translate(0.5F, 6.0F / 16.0F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(state.yaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		submit(state.turret, state, poseStack, collector);
		poseStack.popPose();

		this.beam(state, poseStack, collector, SHOULDER, state.elbow);
		this.beam(state, poseStack, collector, state.elbow, state.tip);

		poseStack.pushPose();
		poseStack.translate(state.tip.x - 0.5, state.tip.y - 0.3, state.tip.z - 0.5);
		submit(state.claw, state, poseStack, collector);
		poseStack.popPose();

		if (!state.held.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(state.tip.x, state.tip.y - 0.4, state.tip.z);
			poseStack.scale(0.5F, 0.5F, 0.5F);
			state.held.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	/** One arm segment: the segment model is a one block long beam along +y, stretched from {@code a} to {@code b}. */
	private void beam(ArmState state, PoseStack poseStack, SubmitNodeCollector collector, Vec3 a, Vec3 b) {
		Vec3 along = b.subtract(a);
		float length = (float) along.length();
		if (length < 1.0E-4F) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(a.x, a.y, a.z);
		poseStack.rotate(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) along.x, (float) along.y, (float) along.z));
		poseStack.scale(1.0F, length, 1.0F);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		submit(state.segment, state, poseStack, collector);
		poseStack.popPose();
	}
}
