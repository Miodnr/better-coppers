package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import dev.mioscoppergolems.automatisation.registry.AutoBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a machine's moving parts from the {@link MachinePartBlock} models. Every machine has a crank on its front
 * (the side that faced the player placing it), which turns while a golem works it.
 */
public abstract class MachineRenderer<T extends MachineBlockEntity, S extends MachineRenderer.State> implements BlockEntityRenderer<T, S> {
	protected static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
	protected final BlockModelResolver models;

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public boolean running;
		public float spin;
		public final BlockModelRenderState crank = new BlockModelRenderState();
	}

	protected MachineRenderer(BlockEntityRendererProvider.Context context) {
		this.models = context.blockModelResolver();
	}

	/** Where the crank's axle sits, as an offset from the middle of the front face (in blocks). */
	protected abstract Vec3 crankOffset();

	@Override
	public void extractRenderState(T machine, S state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(machine, state, partialTicks, cameraPosition, breakProgress);
		state.facing = machine.getBlockState().getValue(MachineBlock.FACING);
		state.running = machine.getBlockState().getValue(MachineBlock.RUNNING);
		state.spin = Mth.lerp(partialTicks, machine.spinO, machine.spin);
		this.models.update(state.crank, part(MachinePartBlock.Part.CRANK), DISPLAY);
	}

	protected static net.minecraft.world.level.block.state.BlockState part(MachinePartBlock.Part part) {
		return AutoBlocks.MACHINE_PART.defaultBlockState().setValue(MachinePartBlock.PART, part);
	}

	/** Turns the pose so that the model's north side faces the machine's front. */
	protected static void faceFront(PoseStack poseStack, Direction facing) {
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
	}

	protected void submitCrank(S state, PoseStack poseStack, SubmitNodeCollector collector) {
		Vec3 offset = this.crankOffset();
		poseStack.pushPose();
		faceFront(poseStack, state.facing);
		poseStack.translate(offset.x, offset.y, offset.z);
		// The crank model turns about the axis through the middle of its north face.
		poseStack.translate(0.5F, 0.5F, 0.0F);
		poseStack.rotate(Axis.ZP.rotationDegrees(state.spin));
		poseStack.translate(-0.5F, -0.5F, 0.0F);
		submit(state.crank, state, poseStack, collector);
		poseStack.popPose();
	}

	protected static void submit(BlockModelRenderState model, State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (!model.isEmpty()) {
			model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}
	}
}
