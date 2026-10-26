package dev.mioscoppergolems.automatisation.machine;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.Automatisation;
import net.minecraft.core.BlockPos;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A machine a copper golem has to work by hand. It only runs for a moment after each turn of the crank, so it stops
 * as soon as its operator walks off, runs out of energy or goes to bed. While running, the block's
 * {@link MachineBlock#RUNNING} state is on, which the client uses to animate it.
 * <p>
 * Machines wear out: after {@link #maxWear()} pieces of work they jam and smoke until their operator repairs them
 * with a {@link #repairItem()} (an iron sheet for most) taken from a chest nearby.
 */
public abstract class MachineBlockEntity extends BlockEntity {
	/** How long one turn of the crank keeps the machine going. */
	private static final int RUN_TICKS = 10;

	/** Crank angle in degrees, client side only: it turns while the machine runs. */
	public float spin;
	public float spinO;
	private long runningUntil;
	private int wear;

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

	/** How much work until the machine jams. */
	protected abstract int maxWear();

	/** What the operator repairs it with. */
	public Item repairItem() {
		return AutoItems.IRON_SHEET;
	}

	public boolean isBroken() {
		return this.wear >= this.maxWear();
	}

	/** How worn the machine is, 0 to 100. */
	public int wearPercent() {
		return Math.min(100, this.wear * 100 / this.maxWear());
	}

	protected void addWear(ServerLevel level, int amount) {
		this.wear += amount;
		this.setChanged();
		if (this.isBroken()) {
			level.playSound(null, this.worldPosition, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.4F, 1.4F);
			this.sync();
		}
	}

	public void repair(ServerLevel level) {
		this.wear = 0;
		level.playSound(null, this.worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.3F);
		level.sendParticles(ParticleTypes.WAX_OFF, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.8, this.worldPosition.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
		this.sync();
	}

	/** Called by the operator every tick it turns the crank. A jammed machine does not move. */
	public void operate(ServerLevel level) {
		if (this.isBroken()) {
			return;
		}
		this.runningUntil = level.getGameTime() + RUN_TICKS;
		BlockState state = this.getBlockState();
		if (!state.getValue(MachineBlock.RUNNING)) {
			level.setBlock(this.worldPosition, state.setValue(MachineBlock.RUNNING, true), Block.UPDATE_ALL);
			String step = this.guideStep();
			if (step != null) {
				MiosCoppergolemsApi.awardGuideNearby(level, Vec3.atCenterOf(this.worldPosition), Automatisation.id("guide/" + step));
			}
		}
	}

	/** The guide advancement for seeing this machine run, if it has one. */
	protected @Nullable String guideStep() {
		return null;
	}

	public boolean isRunning() {
		return this.getBlockState().getValue(MachineBlock.RUNNING);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (machine.isBroken() && level.getGameTime() % 20 == 0) {
			serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
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
		this.wear = input.getIntOr("wear", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("running_until", this.runningUntil);
		output.putInt("wear", this.wear);
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
