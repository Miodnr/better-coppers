package dev.bettercoppers.courier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** A courier's route: everything in {@code from} goes to {@code to}, however far apart they are. */
public record CourierRoute(BlockPos from, BlockPos to) {
	public static final Codec<CourierRoute> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(BlockPos.CODEC.fieldOf("from").forGetter(CourierRoute::from), BlockPos.CODEC.fieldOf("to").forGetter(CourierRoute::to))
			.apply(instance, CourierRoute::new)
	);
}
