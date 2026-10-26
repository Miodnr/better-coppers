package dev.mioscoppergolems.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a mineshaft plan describes. The entrance and heading are set by using the plan on a block; progress counts
 * finished steps so a plan handed back and forth resumes where it stopped.
 */
public record MinePlan(MinePattern pattern, int length, Optional<BlockPos> entrance, Optional<Direction> heading, int progress) {
	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 128;

	public static final Codec<MinePlan> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				MinePattern.CODEC.fieldOf("pattern").forGetter(MinePlan::pattern),
				Codec.intRange(MIN_LENGTH, MAX_LENGTH).fieldOf("length").forGetter(MinePlan::length),
				BlockPos.CODEC.optionalFieldOf("entrance").forGetter(MinePlan::entrance),
				Direction.CODEC.optionalFieldOf("heading").forGetter(MinePlan::heading),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(MinePlan::progress)
			)
			.apply(instance, MinePlan::new)
	);
	public static final StreamCodec<ByteBuf, MinePlan> STREAM_CODEC = StreamCodec.composite(
		MinePattern.STREAM_CODEC, MinePlan::pattern,
		ByteBufCodecs.VAR_INT, MinePlan::length,
		ByteBufCodecs.optional(BlockPos.STREAM_CODEC), MinePlan::entrance,
		ByteBufCodecs.optional(Direction.STREAM_CODEC), MinePlan::heading,
		ByteBufCodecs.VAR_INT, MinePlan::progress,
		MinePlan::new
	);

	public static MinePlan blank(MinePattern pattern, int length) {
		return new MinePlan(pattern, length, Optional.empty(), Optional.empty(), 0);
	}

	public boolean isMarked() {
		return this.entrance.isPresent() && this.heading.isPresent();
	}

	public MinePlan marked(BlockPos entrance, Direction heading) {
		return new MinePlan(this.pattern, this.length, Optional.of(entrance), Optional.of(heading), 0);
	}

	public MinePlan withProgress(int progress) {
		return new MinePlan(this.pattern, this.length, this.entrance, this.heading, progress);
	}
}
