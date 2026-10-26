package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import dev.mioscoppergolems.automatisation.saw.SawBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The blade hangs below the housing, in line with the machine's front, and reaches down into the belt. It shakes a
 * little while it cuts; its texture is the stonecutter's, which turns by itself.
 */
public class SawRenderer extends MachineRenderer<SawBlockEntity, SawRenderer.SawState> {
	public static class SawState extends MachineRenderer.State {
		public boolean cutting;
		public float time;
		public final BlockModelRenderState blade = new BlockModelRenderState();
	}

	public SawRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public SawState createRenderState() {
		return new SawState();
	}

	@Override
	protected Vec3 crankOffset() {
		return new Vec3(0.0, 0.3125, 0.0);
	}

	@Override
	public void extractRenderState(SawBlockEntity saw, SawState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		super.extractRenderState(saw, state, partialTicks, cameraPosition, breakProgress);
		state.cutting = saw.active && state.running;
		state.time = saw.spin;
		this.models.update(state.blade, part(MachinePartBlock.Part.SAW_BLADE), DISPLAY);
	}

	@Override
	public void submit(SawState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitCrank(state, poseStack, collector);
		poseStack.pushPose();
		float shake = state.cutting ? (float) Math.sin(state.time * 0.7F) * 0.01F : 0.0F;
		poseStack.translate(0.0F, -9.0F / 16.0F + shake, 0.0F);
		poseStack.translate(0.5F, 0.0F, 0.5F);
		// The blade model lies across north-south; turn it to run along the machine's front axis.
		poseStack.rotate(Axis.YP.rotationDegrees(state.facing.getAxis() == net.minecraft.core.Direction.Axis.Z ? 90.0F : 0.0F));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		submit(state.blade, state, poseStack, collector);
		poseStack.popPose();
	}
}
