package dev.bettercoppers.repair;

import dev.bettercoppers.registry.ModAttachments;
import net.minecraft.world.entity.animal.golem.CopperGolem;

/** An errand (like repairing an iron golem) pauses whatever job the golem has until it is done. */
public final class Errands {
	private Errands() {
	}

	public static boolean isBusy(CopperGolem golem) {
		return Boolean.TRUE.equals(golem.getAttached(ModAttachments.ERRAND));
	}

	static void setBusy(CopperGolem golem, boolean busy) {
		if (busy) {
			golem.setAttached(ModAttachments.ERRAND, true);
		} else {
			golem.removeAttached(ModAttachments.ERRAND);
		}
	}
}
