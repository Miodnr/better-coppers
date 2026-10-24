package dev.bettercoppers.registry;

import dev.bettercoppers.BetterCoppers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	/** Plain blocks a mining golem may use to patch floors and plug water or lava. */
	public static final TagKey<Block> FILLER = TagKey.create(Registries.BLOCK, BetterCoppers.id("filler"));

	private ModTags() {
	}
}
