package dev.mioscoppergolems.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.jspecify.annotations.Nullable;

/**
 * Changes how a golem works, for upgrades from add-ons. Every registered modifier is asked for every golem; return the
 * neutral value for golems it does not apply to.
 */
public interface GolemModifier {
	/** Multiplies how fast the golem walks and works (1 = unchanged). */
	default float speed(CopperGolem golem) {
		return 1.0F;
	}

	/** True while the golem should not rust from work. */
	default boolean preventsRust(CopperGolem golem) {
		return false;
	}

	/** How many items the golem carries in its hand (sorting, courier trips); {@code base} is 16. */
	default int carryLimit(CopperGolem golem, int base) {
		return base;
	}

	/** Called whenever a working golem uses energy, for upgrades that wear off with use. */
	default void onSpend(CopperGolem golem, int energy) {
	}

	/** Drops whatever the upgrade gives back (on death, or when the golem turns into a statue). */
	default void dropItems(net.minecraft.server.level.ServerLevel level, CopperGolem golem) {
	}

	/** An extra part for the status message, or null. */
	default @Nullable Component status(CopperGolem golem) {
		return null;
	}
}
