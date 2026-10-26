package dev.mioscoppergolems.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The float a fishing golem casts: it only shows the fishing. It floats where it was put, the client draws the line to
 * the golem's rod, and it dips under when a fish bites. The fisher keeps it alive while it fishes; left alone for a
 * second, it disappears. Never saved.
 */
public class GolemBobber extends Entity {
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GolemBobber.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_BITING = SynchedEntityData.defineId(GolemBobber.class, EntityDataSerializers.BOOLEAN);
	private static final int KEEP_ALIVE_TICKS = 20;

	private long keptUntil;

	public GolemBobber(EntityType<? extends GolemBobber> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setOwner(CopperGolem golem) {
		this.entityData.set(DATA_OWNER, golem.getId());
	}

	public @Nullable CopperGolem owner() {
		return this.level().getEntity(this.entityData.get(DATA_OWNER)) instanceof CopperGolem golem ? golem : null;
	}

	public void setBiting(boolean biting) {
		this.entityData.set(DATA_BITING, biting);
	}

	public boolean isBiting() {
		return this.entityData.get(DATA_BITING);
	}

	/** Called by the fisher every tick it keeps fishing. */
	public void keepAlive() {
		this.keptUntil = this.level().getGameTime() + KEEP_ALIVE_TICKS;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			CopperGolem owner = this.owner();
			if (owner == null || !owner.isAlive() || level.getGameTime() > this.keptUntil) {
				this.discard();
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_OWNER, 0);
		entityData.define(DATA_BITING, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}
}
