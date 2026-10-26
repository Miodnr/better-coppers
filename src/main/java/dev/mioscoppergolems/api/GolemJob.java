package dev.mioscoppergolems.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.jspecify.annotations.Nullable;

/**
 * A job added by another mod. Registered jobs count as work everywhere in Mios Coppergolems: such golems do not sort,
 * show up in the status message, pay the running costs (energy, rust) and drop their job items when they die.
 */
public interface GolemJob {
	/** Whether this golem currently has this job. */
	boolean has(CopperGolem golem);

	/** The job's name for the status message. */
	Component name();

	/** Where the golem works, for the particle line shown while sneaking and the status message; null if nowhere yet. */
	default @Nullable BlockPos workplace(CopperGolem golem) {
		return null;
	}

	/** Drops whatever the golem carries for this job (on death, or when it turns into a statue). */
	default void dropItems(ServerLevel level, CopperGolem golem) {
	}
}
