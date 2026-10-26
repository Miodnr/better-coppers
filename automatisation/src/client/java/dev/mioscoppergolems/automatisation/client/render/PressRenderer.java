package dev.mioscoppergolems.automatisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import dev.mioscoppergolems.automatisation.press.PressBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The press head drops onto the belt and slowly climbs back up. */
public class PressRenderer extends MachineRenderer<PressBlockEntity, PressRenderer.PressState> {
	/** How far below the press block the head reaches at the bottom of its stroke. */
	private static final float STROKE = 0.72F;

	public static class PressState extends MachineRenderer.State {
		public float stroke;
		public final BlockModelRenderState head = new BlockModelRenderState();
	}

	public PressRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public PressState createRenderState() {
		return new PressState();
	}

	@Override
	protected Vec3 crankOffset() {
		return new Vec3(0.0, 0.3125, 0.0);
	}

	@Override
	public void extractRenderState(PressBlockEntity press, PressState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		super.extractRenderState(press, state, partialTicks, cameraPosition, breakProgress);
		float progress = press.active ? Mth.lerp(partialTicks, press.progressO, press.progress) : 0.0F;
		float hit = PressBlockEntity.HIT_TICK;
		if (progress <= hit) {
			// Falls faster and faster, like a dropped weight.
			float t = progress / hit;
			state.stroke = t * t;
		} else {
			state.stroke = 1.0F - (progress - hit) / (PressBlockEntity.CYCLE_TICKS - hit);
		}
		this.models.update(state.head, part(MachinePartBlock.Part.PRESS_HEAD), DISPLAY);
	}

	@Override
	public void submit(PressState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitCrank(state, poseStack, collector);
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.375F - state.stroke * (STROKE + 0.375F), 0.0F);
		submit(state.head, state, poseStack, collector);
		poseStack.popPose();
	}
}
