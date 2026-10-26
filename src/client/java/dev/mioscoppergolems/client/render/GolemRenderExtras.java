package dev.mioscoppergolems.client.render;

import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.Nullable;

/** Added to the copper golem render state by a mixin: the trim it wears, its eye upgrade and whether it is excited. */
public interface GolemRenderExtras {
	@Nullable ArmorTrim mioscoppergolems$getTrim();

	void mioscoppergolems$setTrim(@Nullable ArmorTrim trim);

	/** 0 for plain eyes, 1 iron, 2 gold, 3 diamond. */
	int mioscoppergolems$getEyes();

	void mioscoppergolems$setEyes(int eyes);

	boolean mioscoppergolems$isExcited();

	void mioscoppergolems$setExcited(boolean excited);
}
