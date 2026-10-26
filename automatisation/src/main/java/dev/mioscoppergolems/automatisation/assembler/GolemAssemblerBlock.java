package dev.mioscoppergolems.automatisation.assembler;

import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A workshop that builds copper golems: 4 copper sheets, 2 copper gears, a carved pumpkin and redstone in its slots,
 * then twenty seconds of cranking. The new golem steps out at the front.
 */
public class GolemAssemblerBlock extends MachineBlock<GolemAssemblerBlockEntity> {
	public GolemAssemblerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected BlockEntityType<GolemAssemblerBlockEntity> machineType() {
		return AutoBlockEntities.GOLEM_ASSEMBLER;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GolemAssemblerBlockEntity assembler) {
			player.openMenu(assembler);
		}
		return InteractionResult.SUCCESS;
	}
}
