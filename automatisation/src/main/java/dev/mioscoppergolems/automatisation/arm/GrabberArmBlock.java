package dev.mioscoppergolems.automatisation.arm;

import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A copper arm on a turntable. Mark its spots with a wrench: right-click the arm, then each block within
 * {@link GrabberArmBlockEntity#RANGE}: once for an input, twice for an output, three times to forget it. Right-click
 * the arm again when done. Like every machine it only moves while a golem cranks it.
 */
public class GrabberArmBlock extends MachineBlock<GrabberArmBlockEntity> {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0);

	public GrabberArmBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockEntityType<GrabberArmBlockEntity> machineType() {
		return AutoBlockEntities.GRABBER_ARM;
	}
}
