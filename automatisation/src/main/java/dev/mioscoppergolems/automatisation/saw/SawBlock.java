package dev.mioscoppergolems.automatisation.saw;

import dev.mioscoppergolems.automatisation.item.WrenchItem;
import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A saw hanging over a belt. Logs and wood become planks (half again as many as by hand); anything a stonecutter can
 * cut gets cut, into what the filter shows (right-click with an item to set it, empty hand to clear it).
 */
public class SawBlock extends MachineBlock<SawBlockEntity> {
	public SawBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected BlockEntityType<SawBlockEntity> machineType() {
		return AutoBlockEntities.SAW;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof WrenchItem || !(level.getBlockEntity(pos) instanceof SawBlockEntity saw)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			saw.setFilter(stack.copyWithCount(1));
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.filter_set", stack.getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level.getBlockEntity(pos) instanceof SawBlockEntity saw) || saw.filter().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			saw.setFilter(ItemStack.EMPTY);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.filter_cleared"));
		}
		return InteractionResult.SUCCESS;
	}
}
