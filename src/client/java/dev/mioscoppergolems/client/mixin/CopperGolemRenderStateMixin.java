package dev.mioscoppergolems.client.mixin;

import dev.mioscoppergolems.client.render.GolemRenderExtras;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CopperGolemRenderState.class)
public abstract class CopperGolemRenderStateMixin implements GolemRenderExtras {
	@Unique
	private @Nullable ArmorTrim mioscoppergolems$trim;
	@Unique
	private int mioscoppergolems$eyes;
	@Unique
	private boolean mioscoppergolems$excited;

	@Override
	public @Nullable ArmorTrim mioscoppergolems$getTrim() {
		return this.mioscoppergolems$trim;
	}

	@Override
	public void mioscoppergolems$setTrim(@Nullable ArmorTrim trim) {
		this.mioscoppergolems$trim = trim;
	}

	@Override
	public int mioscoppergolems$getEyes() {
		return this.mioscoppergolems$eyes;
	}

	@Override
	public void mioscoppergolems$setEyes(int eyes) {
		this.mioscoppergolems$eyes = eyes;
	}

	@Override
	public boolean mioscoppergolems$isExcited() {
		return this.mioscoppergolems$excited;
	}

	@Override
	public void mioscoppergolems$setExcited(boolean excited) {
		this.mioscoppergolems$excited = excited;
	}
}
