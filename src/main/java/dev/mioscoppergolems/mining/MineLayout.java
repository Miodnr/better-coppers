package dev.mioscoppergolems.mining;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Turns a plan into the ordered list of steps a golem works through. Golems only build mineshafts like the vanilla
 * abandoned ones: 3 wide, 3 high, plank floor where there is none, and every few blocks a support of two fence posts
 * carrying a plank beam, with a torch hung on every other beam. Rails come last.
 * <p>
 * Cells are addressed relative to the entrance: {@code forward} along the heading, {@code side} to the right,
 * {@code up} above the tunnel floor. A slice is everything at one {@code forward} distance.
 */
public final class MineLayout {
	public enum Action {
		/** Dig out the block. */
		DIG,
		/** Make sure the block is solid to walk on. */
		FLOOR,
		FENCE,
		BEAM,
		RAIL,
		TORCH
	}

	/**
	 * {@code stand} is where the golem works from: the floor of the slice just behind, which is already dug out and
	 * connected to the entrance. Walking to the target block itself could lead the golem to the far side of a wall.
	 * {@code facing} is only used by torches: the direction the torch points, away from the block it hangs on.
	 */
	public record Step(Action action, BlockPos pos, BlockPos stand, Direction facing) {
	}

	private static final int SUPPORT_SPACING = 4;

	private final BlockPos entrance;
	private final Direction forward;
	private final Direction right;
	private final List<Step> steps = new ArrayList<>();
	private BlockPos stand;

	private MineLayout(BlockPos entrance, Direction forward) {
		this.entrance = entrance;
		this.forward = forward;
		this.right = forward.getClockWise();
		this.stand = entrance;
	}

	/** Old plans may still name another pattern; every plan is dug as a mineshaft now. */
	public static List<Step> build(MinePlan plan, int minY) {
		MineLayout layout = new MineLayout(plan.entrance().orElseThrow(), plan.heading().orElseThrow());
		int length = plan.length();
		for (int i = 0; i < length; i++) {
			layout.standBehind(i);
			layout.slice(i);
		}
		// Mobs cannot step onto rails (that is what keeps them inside vanilla mineshafts), so the rails go down last,
		// from the far end back to the entrance, each laid from the slice behind it.
		for (int i = length - 1; i >= 0; i--) {
			layout.standBehind(i);
			layout.add(Action.RAIL, layout.cell(i, 0, 0));
		}
		return layout.steps;
	}

	/** Which slice of the plan a block belongs to. */
	public static int sliceOf(MinePlan plan, BlockPos pos) {
		Direction heading = plan.heading().orElseThrow();
		BlockPos offset = pos.subtract(plan.entrance().orElseThrow());
		return offset.getX() * heading.getStepX() + offset.getZ() * heading.getStepZ();
	}

	/** Slices that get two fence posts and a plank beam. */
	public static boolean isSupportSlice(int slice) {
		return slice % SUPPORT_SPACING == 2;
	}

	private BlockPos cell(int forward, int side, int up) {
		return this.entrance.relative(this.forward, forward).relative(this.right, side).above(up);
	}

	/** Work on slice {@code i} from the middle of slice {@code i - 1}. */
	private void standBehind(int i) {
		this.stand = this.cell(i - 1, 0, 0);
	}

	private void add(Action action, BlockPos pos) {
		this.steps.add(new Step(action, pos, this.stand, Direction.UP));
	}

	private void slice(int i) {
		// Middle column first, then the sides: side blocks are then seen from the open middle instead of past the
		// fence posts beside the golem. Each column top down, like a player.
		for (int side : new int[] {0, -1, 1}) {
			for (int up = 2; up >= 0; up--) {
				this.add(Action.DIG, this.cell(i, side, up));
			}
		}
		for (int side = -1; side <= 1; side++) {
			this.add(Action.FLOOR, this.cell(i, side, -1));
		}
		if (isSupportSlice(i)) {
			for (int side : new int[] {-1, 1}) {
				this.add(Action.FENCE, this.cell(i, side, 0));
				this.add(Action.FENCE, this.cell(i, side, 1));
			}
			for (int side = -1; side <= 1; side++) {
				this.add(Action.BEAM, this.cell(i, side, 2));
			}
		}
		// Hangs on the far side of the previous beam, so it needs this slice dug out first.
		if (i % (SUPPORT_SPACING * 2) == 3) {
			this.steps.add(new Step(Action.TORCH, this.cell(i, 0, 2), this.stand, this.forward));
		}
	}
}
