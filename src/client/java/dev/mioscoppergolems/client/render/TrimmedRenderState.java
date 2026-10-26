package dev.mioscoppergolems.client.render;

import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;

/** Added to the copper golem render state by a mixin: the trim the golem wears, if any. */
public interface TrimmedRenderState {
	@Nullable ArmorTrim mioscoppergolems$getTrim();

	void mioscoppergolems$setTrim(@Nullable ArmorTrim trim);
}
