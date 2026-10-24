package dev.bettercoppers.upkeep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Energy and wear of a working golem. Energy is spent on every bit of work and recharged on a redstone-powered block;
 * wear adds up with the work done and makes the golem rust a stage further every so often.
 */
public final class GolemUpkeep {
	public static final int MAX_ENERGY = 1000;

	public static final Codec<GolemUpkeep> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.intRange(0, MAX_ENERGY).optionalFieldOf("energy", MAX_ENERGY).forGetter(upkeep -> upkeep.energy),
				Codec.INT.optionalFieldOf("wear", 0).forGetter(upkeep -> upkeep.wear)
			)
			.apply(instance, GolemUpkeep::new)
	);

	private int energy;
	private int wear;

	public GolemUpkeep() {
		this(MAX_ENERGY, 0);
	}

	private GolemUpkeep(int energy, int wear) {
		this.energy = energy;
		this.wear = wear;
	}

	public int energy() {
		return this.energy;
	}

	public void setEnergy(int energy) {
		this.energy = Math.clamp(energy, 0, MAX_ENERGY);
	}

	public int wear() {
		return this.wear;
	}

	public void setWear(int wear) {
		this.wear = Math.max(0, wear);
	}
}
