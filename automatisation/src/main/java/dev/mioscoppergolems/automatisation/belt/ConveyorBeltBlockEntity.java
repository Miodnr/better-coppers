package dev.mioscoppergolems.automatisation.belt;

import dev.mioscoppergolems.automatisation.machine.ProcessingBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps track of how long the belt has been driven for, and moves whatever lies on it while it runs. */
public class ConveyorBeltBlockEntity extends BlockEntity {
	/** About 1.6 blocks a second: a brisk walk, not a sprint. */
	public static final double SPEED = 0.08;
	private static final double CENTERING = 0.15;
	/** How close items queue up behind each other. */
	private static final double GAP = 0.55;

	private long drivenUntil;

	public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.CONVEYOR_BELT, pos, state);
	}

	/** Called by a cranked belt drive; keeps the belt running for a moment. */
	public void drive(long until) {
		this.drivenUntil = until;
		BlockState state = this.getBlockState();
		if (!state.getValue(ConveyorBeltBlock.RUNNING) && this.level != null) {
			this.level.setBlock(this.worldPosition, state.setValue(ConveyorBeltBlock.RUNNING, true), Block.UPDATE_ALL);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorBeltBlockEntity belt) {
		if (!state.getValue(ConveyorBeltBlock.RUNNING)) {
			return;
		}
		if (level.getGameTime() > belt.drivenUntil) {
			level.setBlock(pos, state.setValue(ConveyorBeltBlock.RUNNING, false), Block.UPDATE_ALL);
			return;
		}
		Direction facing = state.getValue(ConveyorBeltBlock.FACING);
		AABB surface = new AABB(pos.getX(), pos.getY() + 0.1, pos.getZ(), pos.getX() + 1, pos.getY() + 1.0, pos.getZ() + 1);
		for (Entity entity : level.getEntities((Entity) null, surface, ConveyorBeltBlockEntity::isCarried)) {
			if (entity instanceof ItemEntity item && isBlocked(level, item, facing)) {
				// Queue up behind whatever stands still ahead, like a stack under a press.
				item.setDeltaMovement(0.0, item.getDeltaMovement().y, 0.0);
			} else {
				carry(entity, pos, facing);
			}
		}
	}

	/** Another item lies still just ahead of this one, on the same line. */
	private static boolean isBlocked(Level level, ItemEntity item, Direction facing) {
		Vec3 front = item.position().add(facing.getStepX() * GAP, 0.0, facing.getStepZ() * GAP);
		AABB ahead = new AABB(item.position(), front).inflate(facing.getAxis() == Direction.Axis.X ? 0.0 : 0.3, 0.3, facing.getAxis() == Direction.Axis.Z ? 0.0 : 0.3);
		for (ItemEntity other : level.getEntitiesOfClass(ItemEntity.class, ahead, other -> other != item && other.isAlive())) {
			Vec3 offset = other.position().subtract(item.position());
			boolean inFront = offset.x * facing.getStepX() + offset.z * facing.getStepZ() > 0.05;
			if (inFront && other.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCarried(Entity entity) {
		if (entity.isSpectator() || entity.isPassenger() || entity.noPhysics || entity.entityTags().contains(ProcessingBlockEntity.HELD_TAG)) {
			// Items a press or saw is working on stay put.
			return false;
		}
		if (entity instanceof Player player) {
			// Sneaking players can stand still on a running belt, like on a slow escalator.
			return !player.isShiftKeyDown() && !player.getAbilities().flying;
		}
		// Copper golems work here: they walk across belts instead of getting carried off (and pinned against machines).
		return entity instanceof ItemEntity || entity instanceof LivingEntity && !(entity instanceof CopperGolem);
	}

	/** Pushes the entity along the belt and nudges it back towards the middle. */
	private static void carry(Entity entity, BlockPos pos, Direction facing) {
		Vec3 motion = entity.getDeltaMovement();
		double x = facing.getStepX() * SPEED;
		double z = facing.getStepZ() * SPEED;
		if (facing.getAxis() == Direction.Axis.X) {
			z = (pos.getZ() + 0.5 - entity.getZ()) * CENTERING;
		} else {
			x = (pos.getX() + 0.5 - entity.getX()) * CENTERING;
		}
		entity.setDeltaMovement(x, motion.y, z);
		if (entity instanceof Player) {
			entity.needsSync = true;
		}
	}
}
