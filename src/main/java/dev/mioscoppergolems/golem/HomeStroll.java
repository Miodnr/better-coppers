package dev.mioscoppergolems.golem;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.api.GolemJob;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.home.Homes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Idle strolling that stays near home. Vanilla's random stroll has no anchor, so golems with nothing to do drifted
 * further and further from their chest. A golem with a home (or an add-on workplace) now only strolls within
 * {@link #RADIUS} blocks of it, and walks straight back when it finds itself further away. Golems without a home
 * stroll like in vanilla.
 */
public class HomeStroll extends Behavior<CopperGolem> {
	private static final int RADIUS = 6;
	private static final int STEP = 4;
	private static final int ATTEMPTS = 6;
	private static final float SPEED = 1.0F;

	public HomeStroll() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		Vec3 target = target(golem);
		if (target != null) {
			golem.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 0));
		}
	}

	private static @Nullable Vec3 target(CopperGolem golem) {
		BlockPos anchor = anchor(golem);
		if (anchor == null) {
			return LandRandomPos.getPos(golem, STEP, 2);
		}
		Vec3 home = Vec3.atBottomCenterOf(anchor);
		if (horizontalDistance(golem.position(), home) > RADIUS) {
			// Too far out: head back towards home.
			Vec3 back = LandRandomPos.getPosTowards(golem, RADIUS, 3, home);
			return back != null ? back : home;
		}
		for (int i = 0; i < ATTEMPTS; i++) {
			Vec3 pos = LandRandomPos.getPos(golem, STEP, 2);
			if (pos != null && horizontalDistance(pos, home) <= RADIUS) {
				return pos;
			}
		}
		return null;
	}

	private static @Nullable BlockPos anchor(CopperGolem golem) {
		BlockPos home = Homes.get(golem);
		if (home != null) {
			return home;
		}
		GolemJob job = MiosCoppergolemsApi.jobOf(golem);
		return job == null ? null : job.workplace(golem);
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
