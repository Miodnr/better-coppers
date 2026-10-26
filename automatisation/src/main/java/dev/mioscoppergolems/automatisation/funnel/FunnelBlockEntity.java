package dev.mioscoppergolems.automatisation.funnel;

import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Moves items between the container behind the funnel and the space in front of its mouth. */
public class FunnelBlockEntity extends BlockEntity {
	private static final int PUSH_INTERVAL = 10;
	private static final int PUSH_AMOUNT = 16;

	private ItemStack filter = ItemStack.EMPTY;
	private int cooldown;

	public FunnelBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.FUNNEL, pos, state);
	}

	public ItemStack filter() {
		return this.filter;
	}

	public void setFilter(ItemStack filter) {
		this.filter = filter;
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public boolean accepts(ItemStack stack) {
		return this.filter.isEmpty() || ItemStack.isSameItem(this.filter, stack);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FunnelBlockEntity funnel) {
		Direction mouth = state.getValue(FunnelBlock.FACING);
		Container container = HopperBlockEntity.getContainerAt(level, pos.relative(mouth.getOpposite()));
		if (container == null) {
			return;
		}
		if (state.getValue(FunnelBlock.PUSHING)) {
			if (--funnel.cooldown <= 0) {
				funnel.cooldown = PUSH_INTERVAL;
				funnel.push(level, pos, mouth, container);
			}
		} else {
			funnel.pull(level, pos, container);
		}
	}

	/** Swallows any item that landed in the funnel. */
	private void pull(Level level, BlockPos pos, Container container) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos), item -> item.isAlive() && this.accepts(item.getItem()))) {
			HopperBlockEntity.addItem(container, item);
		}
	}

	/** Spits one stack out in front, unless something is still lying there. */
	private void push(Level level, BlockPos pos, Direction mouth, Container container) {
		BlockPos front = pos.relative(mouth);
		if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(front)).isEmpty()) {
			return;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !this.accepts(stack)) {
				continue;
			}
			ItemStack taken = container.removeItem(slot, Math.min(PUSH_AMOUNT, stack.getCount()));
			container.setChanged();
			Vec3 spot = Vec3.atCenterOf(pos).add(mouth.getStepX() * 0.7, mouth.getStepY() * 0.7, mouth.getStepZ() * 0.7);
			if (mouth.getAxis().isHorizontal()) {
				// Land on whatever is in front, a belt for example.
				spot = new Vec3(spot.x, pos.getY() + 0.3, spot.z);
			}
			ItemEntity item = new ItemEntity(level, spot.x, spot.y, spot.z, taken);
			item.setDeltaMovement(mouth.getStepX() * 0.05, 0.0, mouth.getStepZ() * 0.05);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
			return;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.filter = input.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.filter.isEmpty()) {
			output.store("filter", ItemStack.CODEC, this.filter);
		}
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
