package dev.mioscoppergolems.automatisation.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A machine a copper golem has to work by hand. It only runs for a moment after each turn of the crank, so it stops
 * as soon as its operator walks off, runs out of energy or goes to bed. While running, the block's
 * {@link MachineBlock#RUNNING} state is on, which the client uses to animate it.
 */
public abstract class MachineBlockEntity extends BlockEntity {
	/** How long one turn of the crank keeps the machine going. */
	private static final int RUN_TICKS = 10;

	/** Crank angle in degrees, client side only: it turns while the machine runs. */
	public float spin;
	public float spinO;
	private long runningUntil;

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Whether there is anything to do; an operator only cranks a machine that wants it. */
	public abstract boolean wantsWork(ServerLevel level);

	/** One tick of work; only called while the machine runs. */
	protected abstract void tickRunning(ServerLevel level);

	/** One tick while standing still. */
	protected void tickIdle(ServerLevel level) {
	}

	/** Called by the operator every tick it turns the crank. */
	public void operate(ServerLevel level) {
		this.runningUntil = level.getGameTime() + RUN_TICKS;
		BlockState state = this.getBlockState();
		if (!state.getValue(MachineBlock.RUNNING)) {
			level.setBlock(this.worldPosition, state.setValue(MachineBlock.RUNNING, true), Block.UPDATE_ALL);
		}
	}

	public boolean isRunning() {
		return this.getBlockState().getValue(MachineBlock.RUNNING);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (state.getValue(MachineBlock.RUNNING) && level.getGameTime() > machine.runningUntil) {
			level.setBlock(pos, state.setValue(MachineBlock.RUNNING, false), Block.UPDATE_ALL);
			machine.tickIdle(serverLevel);
			return;
		}
		if (machine.isRunning()) {
			machine.tickRunning(serverLevel);
		} else {
			machine.tickIdle(serverLevel);
		}
	}

	/** Client side, every tick: turns the crank while running. Machines with more to animate add to it. */
	protected void clientTick() {
		this.spinO = this.spin;
		if (this.isRunning()) {
			this.spin += 18.0F;
		}
	}

	public static void tickClient(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
		machine.clientTick();
	}

	/** Sends this block entity's data to nearby clients (for animations). */
	protected void sync() {
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.runningUntil = input.getLongOr("running_until", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("running_until", this.runningUntil);
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
