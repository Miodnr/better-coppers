package dev.bettercoppers.client.render;

import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;

/** Added to the copper golem render state by a mixin: the trim the golem wears, if any. */
public interface TrimmedRenderState {
	@Nullable ArmorTrim bettercoppers$getTrim();

	void bettercoppers$setTrim(@Nullable ArmorTrim trim);
}
