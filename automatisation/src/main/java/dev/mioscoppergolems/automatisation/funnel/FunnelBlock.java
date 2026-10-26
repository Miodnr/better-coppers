package dev.mioscoppergolems.automatisation.funnel;

import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A funnel sits on the side of a container, its mouth facing out. Pulling (the default), it swallows items that land
 * in its mouth, from a belt or thrown in. Pushing (toggle with a wrench), it spits stacks from the container out in
 * front of it, onto a belt for example. Right-click with any item to set a filter, with an empty hand to clear it.
 */
public class FunnelBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty PUSHING = BooleanProperty.create("pushing");
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		// A flat back plate against the container and a short rim around the mouth.
		for (Direction direction : Direction.values()) {
			SHAPES.put(direction, Shapes.or(plate(direction, 0, 4), rim(direction)));
		}
	}

	public FunnelBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PUSHING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PUSHING);
	}

	/** The mouth points away from the block it was placed against. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof dev.mioscoppergolems.automatisation.item.WrenchItem) {
			return InteractionResult.PASS;
		}
		if (level.getBlockEntity(pos) instanceof FunnelBlockEntity funnel) {
			if (!level.isClientSide()) {
				funnel.setFilter(stack.copyWithCount(1));
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.filter_set", stack.getHoverName()));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.getBlockEntity(pos) instanceof FunnelBlockEntity funnel && !funnel.filter().isEmpty()) {
			if (!level.isClientSide()) {
				funnel.setFilter(ItemStack.EMPTY);
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.filter_cleared"));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FunnelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, AutoBlockEntities.FUNNEL, FunnelBlockEntity::serverTick);
	}

	/** A slab {@code from}..{@code to} pixels deep, measured from the side facing away from {@code mouth}. */
	private static VoxelShape plate(Direction mouth, double from, double to) {
		return switch (mouth) {
			case NORTH -> Block.box(0, 0, 16 - to, 16, 16, 16 - from);
			case SOUTH -> Block.box(0, 0, from, 16, 16, to);
			case WEST -> Block.box(16 - to, 0, 0, 16 - from, 16, 16);
			case EAST -> Block.box(from, 0, 0, to, 16, 16);
			case DOWN -> Block.box(0, 16 - to, 0, 16, 16 - from, 16);
			case UP -> Block.box(0, from, 0, 16, to, 16);
		};
	}

	/** The rim: a frame two pixels wide reaching eight pixels out of the plate. */
	private static VoxelShape rim(Direction mouth) {
		VoxelShape slab = plate(mouth, 4, 8);
		VoxelShape hole = switch (mouth.getAxis()) {
			case X -> Block.box(0, 2, 2, 16, 14, 14);
			case Y -> Block.box(2, 0, 2, 14, 16, 14);
			case Z -> Block.box(2, 2, 0, 14, 14, 16);
		};
		return Shapes.join(slab, hole, net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
	}
}
