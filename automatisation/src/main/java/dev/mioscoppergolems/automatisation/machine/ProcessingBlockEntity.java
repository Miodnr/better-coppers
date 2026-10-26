package dev.mioscoppergolems.automatisation.machine;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A machine that works on items lying in the block below it, usually on a belt: it stops the stack, works on it for
 * {@link #cycleTicks()} (only while cranked), turns it into the result at {@link #hitTick()} and lets it go again.
 * Held items carry the {@link #HELD_TAG} so belts leave them alone.
 */
public abstract class ProcessingBlockEntity extends MachineBlockEntity {
	public static final String HELD_TAG = "mioscoppergolems_automatisation.held";

	private @Nullable UUID held;
	/** Ticks into the current cycle; the client counts along while the block shows as running. */
	public int progress;
	public int progressO;
	public boolean active;

	protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public abstract int cycleTicks();

	public abstract int hitTick();

	/** What the whole stack turns into, or null if this machine cannot work on it. */
	protected abstract @Nullable List<ItemStack> process(ServerLevel level, ItemStack input);

	/** Sound and anything else that happens at the moment of the hit. */
	protected abstract void onHit(ServerLevel level, Vec3 where);

	@Override
	public boolean wantsWork(ServerLevel level) {
		return this.active || this.findWork(level) != null;
	}

	/**
	 * Standing still, the machine still catches anything it can work on and holds it until its operator comes over:
	 * items pass under it in under a second, much faster than a golem walks.
	 */
	@Override
	protected void tickIdle(ServerLevel level) {
		ItemEntity item = this.catchItem(level);
		if (item != null) {
			this.hold(item);
		}
	}

	@Override
	protected void tickRunning(ServerLevel level) {
		ItemEntity item = this.catchItem(level);
		if (item == null) {
			return;
		}
		this.hold(item);
		this.progress++;
		if (this.progress == this.hitTick()) {
			List<ItemStack> results = this.process(level, item.getItem());
			if (results != null && !results.isEmpty()) {
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item.getItem().getItem()), item.getX(), item.getY() + 0.2, item.getZ(), 10, 0.15, 0.1, 0.15, 0.05);
				item.setItem(results.getFirst());
				for (ItemStack extra : results.subList(1, results.size())) {
					ItemEntity spawned = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), extra);
					spawned.setDeltaMovement(Vec3.ZERO);
					level.addFreshEntity(spawned);
				}
				this.onHit(level, item.position());
			}
		}
		if (this.progress >= this.cycleTicks()) {
			this.finish(level, item);
		}
	}

	/** The held item; or, with nothing held, a new one to work on (then held from now on). Null if there is none. */
	private @Nullable ItemEntity catchItem(ServerLevel level) {
		ItemEntity item = this.held(level);
		if (this.active) {
			if (item == null) {
				// Someone took it away.
				this.finish(level, null);
			}
			return item;
		}
		item = this.findWork(level);
		if (item != null) {
			this.held = item.getUUID();
			item.addTag(HELD_TAG);
			this.active = true;
			this.progress = 0;
			this.sync();
		}
		return item;
	}

	private void finish(ServerLevel level, @Nullable ItemEntity item) {
		if (item != null) {
			item.removeTag(HELD_TAG);
		}
		this.held = null;
		this.active = false;
		this.progress = 0;
		this.sync();
	}

	private @Nullable ItemEntity held(ServerLevel level) {
		if (this.held == null) {
			return null;
		}
		Entity entity = level.getEntity(this.held);
		return entity instanceof ItemEntity item && item.isAlive() && workArea(this.worldPosition).contains(item.position()) ? item : null;
	}

	/** An item in the block below that this machine can work on, and that no other machine holds. */
	private @Nullable ItemEntity findWork(Level level) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, workArea(this.worldPosition), ItemEntity::isAlive)) {
			if (!item.entityTags().contains(HELD_TAG) && this.process(serverLevel, item.getItem()) != null) {
				return item;
			}
		}
		return null;
	}

	/** Keeps the stack still in the middle, right under the tool. */
	private void hold(ItemEntity item) {
		BlockPos below = this.worldPosition.below();
		item.setPos(below.getX() + 0.5, item.getY(), below.getZ() + 0.5);
		item.setDeltaMovement(0.0, item.getDeltaMovement().y, 0.0);
	}

	private static AABB workArea(BlockPos machine) {
		return new AABB(machine.below());
	}

	/** Client side: the cycle keeps counting while the machine runs, for the animation. */
	@Override
	protected void clientTick() {
		super.clientTick();
		this.progressO = this.progress;
		if (this.active && this.isRunning() && this.progress < this.cycleTicks()) {
			this.progress++;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.progress = input.getIntOr("progress", 0);
		this.active = input.getBooleanOr("active", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("progress", this.progress);
		output.putBoolean("active", this.active);
	}
}
