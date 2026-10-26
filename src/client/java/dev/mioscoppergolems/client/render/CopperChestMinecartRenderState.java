package dev.mioscoppergolems.client.render;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CopperChestMinecartRenderState extends MinecartRenderState {
	/** Where the chain reaches the holder, relative to the cart's render position; null when not chained. */
	public @Nullable Vec3 chainEnd;
	public final BlockModelRenderState chainModel = new BlockModelRenderState();
}
