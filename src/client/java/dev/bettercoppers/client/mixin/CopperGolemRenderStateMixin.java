package dev.bettercoppers.client.mixin;

import dev.bettercoppers.client.render.TrimmedRenderState;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CopperGolemRenderState.class)
public abstract class CopperGolemRenderStateMixin implements TrimmedRenderState {
	@Unique
	private @Nullable ArmorTrim bettercoppers$trim;

	@Override
	public @Nullable ArmorTrim bettercoppers$getTrim() {
		return this.bettercoppers$trim;
	}

	@Override
	public void bettercoppers$setTrim(@Nullable ArmorTrim trim) {
		this.bettercoppers$trim = trim;
	}
}
