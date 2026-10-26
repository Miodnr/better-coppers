package dev.mioscoppergolems.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.jspecify.annotations.Nullable;

/**
 * Hooks for add-ons. Register everything during mod initialisation, before any golem loads.
 * <ul>
 * <li>{@link #registerJob}: a new job (see {@link GolemJob}).</li>
 * <li>{@link #registerBehavior}: a behaviour added to every copper golem's brain, next to the built-in jobs. It
 * should only start for its own job and leave the golem alone otherwise.</li>
 * <li>{@link #registerLinkTarget}: blocks golems can be linked to.</li>
 * </ul>
 */
public final class MiosCoppergolemsApi {
	private static final List<GolemJob> JOBS = new ArrayList<>();
	private static final List<Supplier<? extends Behavior<CopperGolem>>> BEHAVIORS = new ArrayList<>();
	private static final List<GolemLinkTarget> LINK_TARGETS = new ArrayList<>();

	private MiosCoppergolemsApi() {
	}

	public static void registerJob(GolemJob job) {
		JOBS.add(job);
	}

	/** A fresh behaviour for each golem: behaviours keep per-golem state. */
	public static void registerBehavior(Supplier<? extends Behavior<CopperGolem>> factory) {
		BEHAVIORS.add(factory);
	}

	public static void registerLinkTarget(GolemLinkTarget target) {
		LINK_TARGETS.add(target);
	}

	public static List<GolemJob> jobs() {
		return JOBS;
	}

	public static List<Supplier<? extends Behavior<CopperGolem>>> behaviors() {
		return BEHAVIORS;
	}

	public static List<GolemLinkTarget> linkTargets() {
		return LINK_TARGETS;
	}

	/** The registered job this golem has, if any. */
	public static @Nullable GolemJob jobOf(CopperGolem golem) {
		for (GolemJob job : JOBS) {
			if (job.has(golem)) {
				return job;
			}
		}
		return null;
	}
}
