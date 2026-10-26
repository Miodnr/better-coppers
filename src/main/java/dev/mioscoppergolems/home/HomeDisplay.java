package dev.mioscoppergolems.home;

import dev.mioscoppergolems.api.GolemJob;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.courier.CourierRoute;
import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.upkeep.Upkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * While a player sneaks, a faint line of copper dust runs from every working golem nearby to its home chest, seen only
 * by that player. Couriers show lines to both ends of their route, when those are close enough to see. Working golems
 * without a home get a puff of smoke over their head instead.
 */
public final class HomeDisplay {
	private static final int INTERVAL = 10;
	private static final double RANGE = 24.0;
	private static final double SPACING = 0.6;
	private static final double ROUTE_LINE_RANGE = 40.0;
	private static final DustParticleOptions COPPER_DUST = new DustParticleOptions(0xD8743F, 0.7F);

	private HomeDisplay() {
	}

	public static void tick(ServerLevel level) {
		if (level.getGameTime() % INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isShiftKeyDown()) {
				continue;
			}
			for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, player.getBoundingBox().inflate(RANGE))) {
				CourierRoute route = Couriers.route(golem);
				BlockPos home = Homes.get(golem);
				GolemJob addonJob = MiosCoppergolemsApi.jobOf(golem);
				BlockPos workplace = addonJob == null ? null : addonJob.workplace(golem);
				if (workplace != null) {
					drawLine(level, golem, workplace, player);
				} else if (route != null) {
					for (BlockPos end : new BlockPos[] {route.from(), route.to()}) {
						if (end.closerToCenterThan(golem.position(), ROUTE_LINE_RANGE)) {
							drawLine(level, golem, end, player);
						}
					}
				} else if (home != null) {
					drawLine(level, golem, home, player);
				} else if (Upkeep.hasJob(golem)) {
					level.sendParticles(player, ParticleTypes.SMOKE, false, true, golem.getX(), golem.getY() + 1.2, golem.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
				}
			}
		}
	}

	/** A dotted line from the golem to the chest; to one player, or to everyone around when {@code player} is null. */
	public static void drawLine(ServerLevel level, CopperGolem golem, BlockPos home, @Nullable ServerPlayer player) {
		Vec3 from = golem.position().add(0.0, golem.getBbHeight() * 0.6, 0.0);
		Vec3 to = Vec3.atCenterOf(home).add(0.0, 0.4, 0.0);
		double length = from.distanceTo(to);
		int points = (int) Math.min(64, Math.ceil(length / SPACING));
		for (int i = 0; i <= points; i++) {
			Vec3 point = from.lerp(to, (double) i / Math.max(1, points));
			if (player != null) {
				level.sendParticles(player, COPPER_DUST, false, true, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			} else {
				level.sendParticles(COPPER_DUST, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
