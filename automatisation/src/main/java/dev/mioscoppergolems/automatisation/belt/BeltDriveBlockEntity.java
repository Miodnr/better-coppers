package dev.mioscoppergolems.automatisation.belt;

import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * While cranked, keeps every belt line it touches running. A line is every belt reachable by following belts that
 * run into each other, forwards and backwards, up to {@link #MAX_BELTS} segments.
 */
public class BeltDriveBlockEntity extends MachineBlockEntity {
	public static final int MAX_BELTS = 64;
	private static final int REFRESH_TICKS = 5;

	public BeltDriveBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.BELT_DRIVE, pos, state);
	}

	@Override
	public boolean wantsWork(ServerLevel level) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBlockEntity(this.worldPosition.relative(direction)) instanceof ConveyorBeltBlockEntity) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void tickRunning(ServerLevel level) {
		long now = level.getGameTime();
		if (now % REFRESH_TICKS != 0) {
			return;
		}
		for (BlockPos pos : connectedBelts(level, this.worldPosition)) {
			if (level.getBlockEntity(pos) instanceof ConveyorBeltBlockEntity belt) {
				belt.drive(now + REFRESH_TICKS * 2);
			}
		}
		if (now % 20 == 0) {
			level.playSound(null, this.worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.4F, 0.7F + level.getRandom().nextFloat() * 0.1F);
		}
	}

	/** Every belt in the lines next to the drive. */
	public static Set<BlockPos> connectedBelts(ServerLevel level, BlockPos drive) {
		Set<BlockPos> found = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos next = drive.relative(direction);
			if (isBelt(level, next)) {
				queue.add(next);
				found.add(next);
			}
		}
		while (!queue.isEmpty() && found.size() < MAX_BELTS) {
			BlockPos pos = queue.poll();
			Direction facing = level.getBlockState(pos).getValue(ConveyorBeltBlock.FACING);
			// Forwards: whatever belt this one runs into.
			BlockPos ahead = pos.relative(facing);
			if (isBelt(level, ahead) && found.add(ahead)) {
				queue.add(ahead);
			}
			// Backwards: every belt that runs into this one.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos behind = pos.relative(direction);
				if (isBelt(level, behind) && level.getBlockState(behind).getValue(ConveyorBeltBlock.FACING) == direction.getOpposite() && found.add(behind)) {
					queue.add(behind);
				}
			}
		}
		return found;
	}

	private static boolean isBelt(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ConveyorBeltBlock;
	}
}
