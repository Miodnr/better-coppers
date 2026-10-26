package dev.mioscoppergolems.client.mixin;

import dev.mioscoppergolems.client.render.TrimmedRenderState;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CopperGolemRenderState.class)
public abstract class CopperGolemRenderStateMixin implements TrimmedRenderState {
	@Unique
	private @Nullable ArmorTrim mioscoppergolems$trim;

	@Override
	public @Nullable ArmorTrim mioscoppergolems$getTrim() {
		return this.mioscoppergolems$trim;
	}

	@Override
	public void mioscoppergolems$setTrim(@Nullable ArmorTrim trim) {
		this.mioscoppergolems$trim = trim;
	}
}
