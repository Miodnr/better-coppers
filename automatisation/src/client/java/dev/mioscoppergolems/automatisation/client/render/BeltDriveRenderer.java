package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mioscoppergolems.automatisation.belt.BeltDriveBlockEntity;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The big gear on top turns slowly with the crank. */
public class BeltDriveRenderer extends MachineRenderer<BeltDriveBlockEntity, BeltDriveRenderer.DriveState> {
	public static class DriveState extends MachineRenderer.State {
		public final BlockModelRenderState gear = new BlockModelRenderState();
	}

	public BeltDriveRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public DriveState createRenderState() {
		return new DriveState();
	}

	@Override
	protected Vec3 crankOffset() {
		return new Vec3(0.0, -0.125, 0.0);
	}

	@Override
	public void extractRenderState(BeltDriveBlockEntity machine, DriveState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		super.extractRenderState(machine, state, partialTicks, cameraPosition, breakProgress);
		this.models.update(state.gear, part(MachinePartBlock.Part.GEAR), DISPLAY);
	}

	@Override
	public void submit(DriveState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitCrank(state, poseStack, collector);
		poseStack.pushPose();
		poseStack.translate(0.5F, 12.0F / 16.0F, 0.5F);
		// Geared down: a full turn of the crank moves the big wheel by a quarter.
		poseStack.rotate(Axis.YP.rotationDegrees(state.spin * 0.25F));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		submit(state.gear, state, poseStack, collector);
		poseStack.popPose();
	}
}
