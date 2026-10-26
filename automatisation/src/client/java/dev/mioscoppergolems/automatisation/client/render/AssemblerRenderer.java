package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mioscoppergolems.automatisation.assembler.GolemAssemblerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** Only the crank moves; the work happens inside the workshop. */
public class AssemblerRenderer extends MachineRenderer<GolemAssemblerBlockEntity, MachineRenderer.State> {
	public AssemblerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected Vec3 crankOffset() {
		return Vec3.ZERO;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitCrank(state, poseStack, collector);
	}
}
