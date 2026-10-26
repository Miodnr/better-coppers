package dev.mioscoppergolems.golem;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Tells a slow golem that is still getting somewhere apart from one that is stuck: only ticks in which it did not get
 * any closer to its target count. A long walk back from the home chest is fine; standing in front of a wall is not.
 */
public final class TravelWatch {
	/** How much closer (in blocks) counts as progress. */
	private static final double PROGRESS = 0.5;

	private double best = Double.MAX_VALUE;
	private int stalled;

	public void reset() {
		this.best = Double.MAX_VALUE;
		this.stalled = 0;
	}

	/** Records one tick of walking towards {@code target} and returns how many ticks in a row it made no progress. */
	public int tick(Entity walker, Vec3 target) {
		double distance = walker.position().distanceTo(target);
		if (distance < this.best - PROGRESS) {
			this.best = distance;
			this.stalled = 0;
		} else {
			this.stalled++;
		}
		return this.stalled;
	}
}
