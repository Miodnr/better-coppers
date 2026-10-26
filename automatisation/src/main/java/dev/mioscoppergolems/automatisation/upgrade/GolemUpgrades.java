package dev.mioscoppergolems.automatisation.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a golem has been fitted with. Plating soaks up damage, coating keeps rust away and gearwork speeds the golem
 * up; all three wear off (plating by hits, the other two by work). The satchel stays.
 *
 * @param plating damage the iron plating can still take
 * @param coating energy the golem can still spend without rusting
 * @param gearwork energy the golem can still spend at the faster pace
 */
public record GolemUpgrades(int plating, int coating, int gearwork, boolean satchel) {
	public static final GolemUpgrades NONE = new GolemUpgrades(0, 0, 0, false);
	public static final Codec<GolemUpgrades> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.INT.optionalFieldOf("plating", 0).forGetter(GolemUpgrades::plating),
				Codec.INT.optionalFieldOf("coating", 0).forGetter(GolemUpgrades::coating),
				Codec.INT.optionalFieldOf("gearwork", 0).forGetter(GolemUpgrades::gearwork),
				Codec.BOOL.optionalFieldOf("satchel", false).forGetter(GolemUpgrades::satchel)
			)
			.apply(instance, GolemUpgrades::new)
	);

	public GolemUpgrades withPlating(int value) {
		return new GolemUpgrades(Math.max(0, value), this.coating, this.gearwork, this.satchel);
	}

	public GolemUpgrades withCoating(int value) {
		return new GolemUpgrades(this.plating, Math.max(0, value), this.gearwork, this.satchel);
	}

	public GolemUpgrades withGearwork(int value) {
		return new GolemUpgrades(this.plating, this.coating, Math.max(0, value), this.satchel);
	}

	public GolemUpgrades withSatchel(boolean value) {
		return new GolemUpgrades(this.plating, this.coating, this.gearwork, value);
	}

	public boolean isEmpty() {
		return this.equals(NONE);
	}
}
