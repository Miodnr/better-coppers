package dev.mioscoppergolems.automatisation.chute;

import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Holds the one stack currently falling through and hands it down every few ticks. */
public class ChuteBlockEntity extends BlockEntity implements Container {
	private static final int TRANSFER_TICKS = 4;

	private ItemStack stack = ItemStack.EMPTY;
	private int cooldown;

	public ChuteBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.CHUTE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ChuteBlockEntity chute) {
		if (chute.stack.isEmpty()) {
			chute.catchFalling(level, pos);
		}
		if (chute.stack.isEmpty() || --chute.cooldown > 0) {
			return;
		}
		chute.cooldown = TRANSFER_TICKS;
		chute.passDown(level, pos);
	}

	/** Takes in an item that fell into the shaft. */
	private void catchFalling(Level level, BlockPos pos) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).deflate(0.1, 0.0, 0.1), ItemEntity::isAlive)) {
			this.stack = item.getItem().copy();
			item.discard();
			this.cooldown = TRANSFER_TICKS;
			this.setChanged();
			return;
		}
	}

	private void passDown(Level level, BlockPos pos) {
		BlockPos below = pos.below();
		if (level.getBlockEntity(below) instanceof ChuteBlockEntity next) {
			if (next.stack.isEmpty()) {
				next.stack = this.stack;
				next.cooldown = TRANSFER_TICKS;
				next.setChanged();
				this.stack = ItemStack.EMPTY;
				this.setChanged();
			}
			return;
		}
		Container container = HopperBlockEntity.getContainerAt(level, below);
		if (container != null) {
			this.stack = HopperBlockEntity.addItem(this, container, this.stack, Direction.UP);
			this.setChanged();
			return;
		}
		if (level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y) < 0.9) {
			// Open bottom: drop it, straight down onto a belt or the floor.
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() - 0.4, pos.getZ() + 0.5, this.stack);
			item.setDeltaMovement(0.0, -0.1, 0.0);
			level.addFreshEntity(item);
			this.stack = ItemStack.EMPTY;
			this.setChanged();
		}
	}

	// --- Container: one slot, so hoppers, funnels and arms can feed the chute ---

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return this.stack.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? this.stack : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = slot == 0 ? this.stack.split(count) : ItemStack.EMPTY;
		this.setChanged();
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack taken = this.stack;
		this.stack = ItemStack.EMPTY;
		return taken;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot == 0) {
			this.stack = stack;
			this.setChanged();
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		this.stack = ItemStack.EMPTY;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.stack = input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.stack.isEmpty()) {
			output.store("item", ItemStack.CODEC, this.stack);
		}
	}
}
