package dev.mioscoppergolems.api;

import dev.mioscoppergolems.golem.Eyes;
import dev.mioscoppergolems.guide.Guide;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hooks for add-ons. Register everything during mod initialisation, before any golem loads.
 * <ul>
 * <li>{@link #registerJob}: a new job (see {@link GolemJob}).</li>
 * <li>{@link #registerBehavior}: a behaviour added to every copper golem's brain, next to the built-in jobs. It
 * should only start for its own job and leave the golem alone otherwise.</li>
 * <li>{@link #registerLinkTarget}: blocks golems can be linked to.</li>
 * <li>{@link #registerModifier}: upgrades that change speed, rusting or how much a golem carries.</li>
 * <li>{@link #registerChargePad}: blocks golems recharge on.</li>
 * <li>{@link #registerHandbookChapter}: chapters for the Golem Handbook; {@link #awardGuide} unlocks guide
 * advancements.</li>
 * </ul>
 */
public final class MiosCoppergolemsApi {
	private static final List<GolemJob> JOBS = new ArrayList<>();
	private static final List<Supplier<? extends Behavior<CopperGolem>>> BEHAVIORS = new ArrayList<>();
	private static final List<GolemLinkTarget> LINK_TARGETS = new ArrayList<>();
	private static final List<GolemModifier> MODIFIERS = new ArrayList<>();
	private static final List<ChargePad> CHARGE_PADS = new ArrayList<>();
	private static final List<Identifier> HANDBOOK_CHAPTERS = new ArrayList<>();

	private MiosCoppergolemsApi() {
	}

	/**
	 * A chapter for the Golem Handbook, after the built-in ones. Its title and text come from the translation keys
	 * {@code handbook.<namespace>.<path>.title} and {@code .text}. In the text, a line starting with "# " is a heading
	 * and a blank line starts a new paragraph; pages are laid out automatically.
	 */
	public static void registerHandbookChapter(Identifier chapter) {
		HANDBOOK_CHAPTERS.add(chapter);
	}

	public static List<Identifier> handbookChapters() {
		return HANDBOOK_CHAPTERS;
	}

	/**
	 * Unlocks a step of an add-on's guide advancements: an advancement with an "impossible" criterion named
	 * {@code done}. Does nothing on the client.
	 */
	public static void awardGuide(Player player, Identifier advancement) {
		Guide.award(player, advancement, Guide.CRITERION);
	}

	/** The same, for every player within sight of something a golem or machine did. */
	public static void awardGuideNearby(ServerLevel level, Vec3 pos, Identifier advancement) {
		Guide.awardNearby(level, pos, advancement);
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

	public static void registerModifier(GolemModifier modifier) {
		MODIFIERS.add(modifier);
	}

	public static void registerChargePad(ChargePad pad) {
		CHARGE_PADS.add(pad);
	}

	public static List<GolemModifier> modifiers() {
		return MODIFIERS;
	}

	/** The pad this block is, or null for an ordinary block. */
	public static @Nullable ChargePad chargePad(net.minecraft.world.level.block.state.BlockState state) {
		for (ChargePad pad : CHARGE_PADS) {
			if (pad.isPad(state)) {
				return pad;
			}
		}
		return null;
	}

	/** A work radius for this golem, widened by its eye upgrade (1.5x iron, 2x gold, 3x diamond). */
	public static int radius(CopperGolem golem, int base) {
		return Eyes.radius(golem, base);
	}

	/** How many items this golem carries in its hand, upgrades included. */
	public static int carryLimit(CopperGolem golem, int base) {
		int limit = base;
		for (GolemModifier modifier : MODIFIERS) {
			limit = modifier.carryLimit(golem, limit);
		}
		return limit;
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
