package dev.mioscoppergolems.automatisation.arm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.golem.Inventories;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Swings items from its input spots to its output spots, up to {@link #GRAB} at a time. A spot is a container, or any
 * other block where items lie or should be put down (a belt, a depot of bare floor). Each swing takes
 * {@link #SWING_TICKS} of cranking.
 */
public class GrabberArmBlockEntity extends MachineBlockEntity {
	public static final int RANGE = 4;
	public static final int SWING_TICKS = 20;
	private static final int GRAB = 16;
	private static final int MAX_LOOSE_ITEMS = 3;

	public record Spot(BlockPos pos, boolean input) {
		public static final Codec<Spot> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos), Codec.BOOL.fieldOf("input").forGetter(Spot::input))
				.apply(instance, Spot::new)
		);
	}

	private final List<Spot> spots = new ArrayList<>();
	private ItemStack held = ItemStack.EMPTY;
	/** Where the claw is heading and where it came from; the client swings between the two. */
	public @Nullable BlockPos target;
	public @Nullable BlockPos previous;
	public int progress;
	public int progressO;
	private int nextInput;

	public GrabberArmBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.GRABBER_ARM, pos, state);
	}

	public List<Spot> spots() {
		return this.spots;
	}

	public ItemStack held() {
		return this.held;
	}

	/** Cycles a spot: not used, input, output, not used. Returns the new role (null = removed). */
	public @Nullable Boolean toggle(BlockPos pos) {
		for (int i = 0; i < this.spots.size(); i++) {
			Spot spot = this.spots.get(i);
			if (spot.pos().equals(pos)) {
				if (spot.input()) {
					this.spots.set(i, new Spot(pos, false));
					this.sync();
					return false;
				}
				this.spots.remove(i);
				this.sync();
				return null;
			}
		}
		this.spots.add(new Spot(pos.immutable(), true));
		this.sync();
		return true;
	}

	public boolean inRange(BlockPos pos) {
		BlockPos offset = pos.subtract(this.worldPosition);
		return Math.abs(offset.getX()) <= RANGE && Math.abs(offset.getZ()) <= RANGE && Math.abs(offset.getY()) <= RANGE - 1;
	}

	@Override
	protected int maxWear() {
		return 256;
	}

	@Override
	public boolean wantsWork(ServerLevel level) {
		if (this.target != null || !this.held.isEmpty()) {
			return true;
		}
		for (Spot spot : this.spots) {
			if (spot.input() && !peek(level, spot.pos()).isEmpty() && this.outputFor(level, peek(level, spot.pos())) != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void tickRunning(ServerLevel level) {
		if (this.target == null) {
			this.chooseNext(level);
			return;
		}
		if (++this.progress < SWING_TICKS) {
			return;
		}
		if (this.held.isEmpty()) {
			this.grab(level, this.target);
		} else {
			this.drop(level, this.target);
		}
		this.addWear(level, 1);
		this.previous = this.target;
		this.target = null;
		this.progress = 0;
		this.chooseNext(level);
	}

	/** Sets off towards the next spot: an output while holding something, else the next input with work. */
	private void chooseNext(ServerLevel level) {
		BlockPos next = null;
		if (!this.held.isEmpty()) {
			next = this.outputFor(level, this.held);
		} else {
			List<Spot> inputs = this.spots.stream().filter(Spot::input).toList();
			for (int i = 0; i < inputs.size() && next == null; i++) {
				Spot spot = inputs.get((this.nextInput + i) % inputs.size());
				ItemStack waiting = peek(level, spot.pos());
				if (!waiting.isEmpty() && this.outputFor(level, waiting) != null) {
					next = spot.pos();
					this.nextInput = (this.nextInput + i + 1) % inputs.size();
				}
			}
		}
		if (next != null) {
			this.target = next;
			this.progress = 0;
			level.playSound(null, this.worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.15F, 1.8F);
		}
		this.sync();
	}

	private @Nullable BlockPos outputFor(ServerLevel level, ItemStack stack) {
		for (Spot spot : this.spots) {
			if (!spot.input() && canPut(level, spot.pos(), stack)) {
				return spot.pos();
			}
		}
		return null;
	}

	private void grab(ServerLevel level, BlockPos pos) {
		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container != null) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				if (!container.getItem(slot).isEmpty()) {
					this.held = container.removeItem(slot, GRAB);
					container.setChanged();
					break;
				}
			}
		} else {
			for (ItemEntity item : looseItems(level, pos)) {
				this.held = item.getItem().split(GRAB);
				if (item.getItem().isEmpty()) {
					item.discard();
				}
				break;
			}
		}
		if (!this.held.isEmpty()) {
			level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.BLOCKS, 0.5F, 1.3F);
		}
	}

	private void drop(ServerLevel level, BlockPos pos) {
		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container != null) {
			this.held = HopperBlockEntity.addItem(null, container, this.held, Direction.UP);
		} else if (looseItems(level, pos).size() < MAX_LOOSE_ITEMS) {
			double top = level.getBlockState(pos).getCollisionShape(level, pos).max(Direction.Axis.Y);
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + Math.max(0.0, top) + 0.05, pos.getZ() + 0.5, this.held);
			item.setDeltaMovement(0.0, 0.0, 0.0);
			level.addFreshEntity(item);
			this.held = ItemStack.EMPTY;
		}
		level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.BLOCKS, 0.5F, 1.3F);
	}

	/** What the arm would pick up at this spot. */
	private static ItemStack peek(Level level, BlockPos pos) {
		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container != null) {
			for (ItemStack stack : container) {
				if (!stack.isEmpty()) {
					return stack;
				}
			}
			return ItemStack.EMPTY;
		}
		List<ItemEntity> items = looseItems(level, pos);
		return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().getItem();
	}

	private static boolean canPut(Level level, BlockPos pos, ItemStack stack) {
		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container != null) {
			return Inventories.hasRoom(container, stack);
		}
		return level.getBlockState(pos).getCollisionShape(level, pos).max(Direction.Axis.Y) < 0.9 && looseItems(level, pos).size() < MAX_LOOSE_ITEMS;
	}

	private static List<ItemEntity> looseItems(Level level, BlockPos pos) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos), ItemEntity::isAlive);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null && !this.held.isEmpty()) {
			net.minecraft.world.Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), this.held);
		}
	}

	/** Client side: the swing keeps going while the arm shows as running. */
	@Override
	protected void clientTick() {
		super.clientTick();
		this.progressO = this.progress;
		if (this.target != null && this.isRunning() && this.progress < SWING_TICKS) {
			this.progress++;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.spots.clear();
		input.read("spots", Spot.CODEC.listOf()).ifPresent(this.spots::addAll);
		this.held = input.read("held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		this.target = input.read("target", BlockPos.CODEC).orElse(null);
		this.previous = input.read("previous", BlockPos.CODEC).orElse(null);
		this.progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("spots", Spot.CODEC.listOf(), this.spots);
		if (!this.held.isEmpty()) {
			output.store("held", ItemStack.CODEC, this.held);
		}
		output.storeNullable("target", BlockPos.CODEC, this.target);
		output.storeNullable("previous", BlockPos.CODEC, this.previous);
		output.putInt("progress", this.progress);
	}
}
