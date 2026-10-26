package dev.mioscoppergolems.registry;

import dev.mioscoppergolems.MiosCoppergolems;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	/** Plain blocks a mining golem may use to patch floors and plug water or lava. */
	public static final TagKey<Block> FILLER = TagKey.create(Registries.BLOCK, MiosCoppergolems.id("filler"));

	private ModTags() {
	}
}
