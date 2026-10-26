package dev.mioscoppergolems.guide;

import dev.mioscoppergolems.MiosCoppergolems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The guide advancements: two advancement tabs that lead a new player through the mod step by step. Most steps are
 * triggered from code (with an "impossible" criterion), since vanilla has no trigger for "linked a golem to a chest".
 * Things a golem does on its own count for every player who watches it, within {@link #WATCH_RANGE} blocks.
 */
public final class Guide {
	public static final String CRITERION = "done";
	private static final double WATCH_RANGE = 48.0;

	private Guide() {
	}

	public static Identifier step(String name) {
		return MiosCoppergolems.id("guide/" + name);
	}

	public static void award(Player player, String step) {
		award(player, step(step), CRITERION);
	}

	public static void award(Player player, Identifier advancement, String criterion) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		AdvancementHolder holder = serverPlayer.level().getServer().getAdvancements().get(advancement);
		if (holder != null) {
			serverPlayer.getAdvancements().award(holder, criterion);
		}
	}

	/** For something a golem did: every player close enough to see it gets the step. */
	public static void awardNearby(ServerLevel level, Vec3 pos, Identifier advancement) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().closerThan(pos, WATCH_RANGE)) {
				award(player, advancement, CRITERION);
			}
		}
	}

	public static void awardNearby(ServerLevel level, Vec3 pos, String step) {
		awardNearby(level, pos, step(step));
	}

	/** A golem got a job: the job's own step, plus its tick on "Full Employment". */
	public static void job(Player player, String job) {
		switch (job) {
			case "forester", "fisher", "keeper", "crafter" -> award(player, step("odd_jobs"), job);
			default -> award(player, job);
		}
		award(player, step("full_employment"), job);
	}
}
