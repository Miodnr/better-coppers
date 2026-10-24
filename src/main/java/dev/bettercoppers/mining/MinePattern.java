package dev.bettercoppers.mining;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum MinePattern implements StringRepresentable {
	TUNNEL("tunnel", false),
	STAIRCASE("staircase", false),
	WIDE_TUNNEL("wide_tunnel", false),
	BRANCH_MINE("branch_mine", false),
	/** Vanilla-style corridor with wooden supports and rails. */
	MINESHAFT("mineshaft", true);

	public static final Codec<MinePattern> CODEC = StringRepresentable.fromEnum(MinePattern::values);
	public static final StreamCodec<ByteBuf, MinePattern> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], MinePattern::ordinal);

	private final String name;
	private final boolean needsWood;

	MinePattern(String name, boolean needsWood) {
		this.name = name;
		this.needsWood = needsWood;
	}

	/** Whether this pattern builds supports and rails. */
	public boolean needsWood() {
		return this.needsWood;
	}

	public Component displayName() {
		return Component.translatable("pattern.bettercoppers." + this.name);
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
