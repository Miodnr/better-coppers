package dev.mioscoppergolems.entity;

import dev.mioscoppergolems.registry.ModItems;
import java.util.ArrayDeque;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A chest minecart with a copper chest that can be towed on an iron or copper chain, off rails, by a player or a copper golem.
 */
public class CopperChestMinecart extends AbstractMinecartContainer {
	private static final EntityDataAccessor<Integer> DATA_HOLDER_ID = SynchedEntityData.defineId(CopperChestMinecart.class, EntityDataSerializers.INT);
	/** The chain the cart is towed with: an iron chain or any copper chain. Synced so it is drawn in its own colour. */
	private static final EntityDataAccessor<ItemStack> DATA_CHAIN = SynchedEntityData.defineId(CopperChestMinecart.class, EntityDataSerializers.ITEM_STACK);
	/** Distance the cart keeps behind whoever tows it. */
	private static final double GOLEM_TOW_DISTANCE = 1.6;
	private static final double PLAYER_TOW_DISTANCE = 2.5;
	/** Above this the cart catches up by teleporting, e.g. after the golem squeezed through a corner. */
	private static final double CATCH_UP_DISTANCE = 10.0;
	/** Above this, or in another dimension, the chain snaps and drops. */
	private static final double SNAP_DISTANCE = 24.0;
	private static final double MAX_TOW_STEP = 0.6;
	private static final double FOOTSTEP_SPACING = 0.5;
	private static final int MAX_FOOTSTEPS = 64;
	private static final int MAX_STUCK_TICKS = 20;

	/** The item that made this a double cart (from an add-on), dropped again when the cart breaks; empty if single. */
	private ItemStack extension = ItemStack.EMPTY;
	private @Nullable UUID holderUuid;
	private @Nullable Entity cachedHolder;
	/** Where the holder walked, oldest first. Not saved: after loading the cart starts a new trail. */
	private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
	private int stuckTicks;

	public CopperChestMinecart(EntityType<? extends CopperChestMinecart> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_HOLDER_ID, -1);
		entityData.define(DATA_CHAIN, new ItemStack(Items.IRON_CHAIN));
	}

	@Override
	protected Item getDropItem() {
		return ModItems.COPPER_CHEST_MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModItems.COPPER_CHEST_MINECART);
	}

	@Override
	public int getContainerSize() {
		return this.isExtended() ? 54 : 27;
	}

	public boolean isExtended() {
		return !this.extension.isEmpty();
	}

	/** Makes this a double cart with 54 slots, keeping what is inside. Returns false if it already is one. */
	public boolean extend(ItemStack extensionItem) {
		if (this.isExtended()) {
			return false;
		}
		java.util.List<ItemStack> contents = new java.util.ArrayList<>(this.getItemStacks());
		this.extension = extensionItem.copyWithCount(1);
		this.clearItemStacks();
		for (int slot = 0; slot < contents.size(); slot++) {
			this.getItemStacks().set(slot, contents.get(slot));
		}
		return true;
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return Blocks.COPPER_CHEST.weathering().unaffected().defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
	}

	@Override
	public int getDefaultDisplayOffset() {
		return 8;
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return this.isExtended() ? ChestMenu.sixRows(containerId, inventory, this) : ChestMenu.threeRows(containerId, inventory, this);
	}

	@Override
	public void stopOpen(ContainerUser containerUser) {
		this.level().gameEvent(GameEvent.CONTAINER_CLOSE, this.position(), GameEvent.Context.of(containerUser.getLivingEntity()));
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (player.isShiftKeyDown() && this.isChained()) {
			if (!this.level().isClientSide()) {
				this.unchain(true);
			}
			return InteractionResult.SUCCESS;
		}
		if (isChain(held) && this.getHolder() != player) {
			if (!this.level().isClientSide()) {
				// Re-chaining a cart that is already chained moves the existing chain, no new one is used up.
				if (!this.isChained()) {
					this.entityData.set(DATA_CHAIN, held.copyWithCount(1));
					held.consume(1, player);
				}
				this.chainTo(player);
			}
			return InteractionResult.SUCCESS;
		}
		InteractionResult result = this.interactWithContainerVehicle(player);
		if (result.consumesAction()) {
			this.gameEvent(GameEvent.CONTAINER_OPEN, player);
		}
		return result;
	}

	// --- chaining ---

	/** An iron chain, or a copper chain of any age, waxed or not. */
	public static boolean isChain(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ChainBlock;
	}

	/** The chain this cart is (or was last) towed with. */
	public ItemStack chain() {
		return this.entityData.get(DATA_CHAIN);
	}

	public boolean isChained() {
		return this.level().isClientSide() ? this.entityData.get(DATA_HOLDER_ID) != -1 : this.holderUuid != null;
	}

	public boolean isChainedTo(Entity entity) {
		return this.level().isClientSide() ? this.entityData.get(DATA_HOLDER_ID) == entity.getId() : entity.getUUID().equals(this.holderUuid);
	}

	public void chainTo(Entity holder) {
		this.holderUuid = holder.getUUID();
		this.cachedHolder = holder;
		this.trail.clear();
		this.entityData.set(DATA_HOLDER_ID, holder.getId());
		this.playChainSound();
	}

	/** Removes the chain; with {@code drop} the chain is given back as an item. */
	public void unchain(boolean drop) {
		if (this.holderUuid == null) {
			return;
		}
		this.holderUuid = null;
		this.cachedHolder = null;
		this.trail.clear();
		this.entityData.set(DATA_HOLDER_ID, -1);
		this.playChainSound();
		if (drop && this.level() instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
			this.spawnAtLocation(serverLevel, this.chain().copy());
		}
	}

	private void playChainSound() {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** The entity towing this cart, if it is loaded. Works on both sides. */
	public @Nullable Entity getHolder() {
		if (this.level().isClientSide()) {
			int id = this.entityData.get(DATA_HOLDER_ID);
			return id == -1 ? null : this.level().getEntity(id);
		}
		if (this.holderUuid == null) {
			return null;
		}
		// A holder that was killed or discarded (e.g. turned into a statue) stays cached so the chain drops;
		// one that was only unloaded is looked up again.
		boolean unloaded = this.cachedHolder != null && this.cachedHolder.isRemoved() && !this.cachedHolder.getRemovalReason().shouldDestroy();
		if (this.cachedHolder == null || unloaded) {
			this.cachedHolder = this.level().getEntity(this.holderUuid);
			this.entityData.set(DATA_HOLDER_ID, this.cachedHolder == null ? -1 : this.cachedHolder.getId());
		}
		return this.cachedHolder;
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel && this.holderUuid != null) {
			this.tickTowing();
		}
		super.tick();
	}

	private void tickTowing() {
		Entity holder = this.getHolder();
		if (holder == null) {
			// Holder is unloaded or offline: wait in place and keep the chain.
			return;
		}
		if (!holder.isAlive() || holder.level() != this.level()) {
			this.unchain(true);
			return;
		}
		Vec3 toHolder = holder.position().subtract(this.position());
		double horizontal = toHolder.horizontalDistance();
		if (horizontal > SNAP_DISTANCE) {
			this.unchain(true);
			return;
		}
		double towDistance = holder instanceof Player ? PLAYER_TOW_DISTANCE : GOLEM_TOW_DISTANCE;
		// Movement is driven by the chain instead of momentum, so the cart never rolls away on its own.
		Vec3 motion = this.getDeltaMovement();
		this.setDeltaMovement(0.0, motion.y, 0.0);

		this.recordFootstep(holder.position());
		if (horizontal < 1.0) {
			// The holder walked back past the cart (turning around in a dead end): start a fresh trail from here.
			this.trail.clear();
			this.stuckTicks = 0;
			return;
		}
		// Follow the holder's footsteps rather than a straight line, so the cart takes the same corners and
		// narrow entrances (like a one block wide staircase) instead of getting caught on them.
		while (this.trail.size() > 1 && this.position().subtract(this.trail.getFirst()).horizontalDistanceSqr() < 0.16) {
			this.trail.removeFirst();
		}
		double slack = this.trailLength() - towDistance;
		if (slack <= 0.0) {
			this.stuckTicks = 0;
			return;
		}
		if (horizontal > CATCH_UP_DISTANCE || this.stuckTicks > MAX_STUCK_TICKS) {
			this.hopAlongTrail(towDistance);
			return;
		}
		Vec3 toNext = this.trail.getFirst().subtract(this.position());
		double distance = toNext.horizontalDistance();
		if (distance < 1.0E-3) {
			return;
		}
		double step = Math.min(Math.min(slack, distance), MAX_TOW_STEP);
		Vec3 before = this.position();
		this.move(MoverType.SELF, new Vec3(toNext.x, 0.0, toNext.z).scale(step / distance));
		boolean moved = this.position().subtract(before).horizontalDistanceSqr() > step * step * 0.25;
		this.stuckTicks = moved ? 0 : this.stuckTicks + 1;
	}

	private void recordFootstep(Vec3 holderPos) {
		if (this.trail.isEmpty() || this.trail.getLast().distanceToSqr(holderPos) > FOOTSTEP_SPACING * FOOTSTEP_SPACING) {
			this.trail.addLast(holderPos);
			if (this.trail.size() > MAX_FOOTSTEPS) {
				this.trail.removeFirst();
			}
		}
	}

	/** Distance from the cart along the footsteps to the newest one. */
	private double trailLength() {
		double length = 0.0;
		Vec3 previous = this.position();
		for (Vec3 footstep : this.trail) {
			length += footstep.distanceTo(previous);
			previous = footstep;
		}
		return length;
	}

	/** Stuck or left behind: jump onto the footstep that is about the tow distance behind the holder. */
	private void hopAlongTrail(double towDistance) {
		double behind = 0.0;
		Vec3 target = this.trail.getLast();
		Vec3 previous = target;
		var iterator = this.trail.descendingIterator();
		while (iterator.hasNext()) {
			Vec3 footstep = iterator.next();
			behind += footstep.distanceTo(previous);
			previous = footstep;
			target = footstep;
			if (behind >= towDistance) {
				break;
			}
		}
		while (!this.trail.isEmpty() && this.trail.getFirst() != target) {
			this.trail.removeFirst();
		}
		this.snapTo(target.x, target.y, target.z, this.getYRot(), this.getXRot());
		this.stuckTicks = 0;
	}

	@Override
	public void push(Entity entity) {
		// The holder walks right past its own cart in narrow tunnels; neither should shove the other.
		if (!this.isChainedTo(entity)) {
			super.push(entity);
		}
	}

	@Override
	public float maxUpStep() {
		// Lets a towed cart follow up single blocks and stairs.
		return this.holderUuid != null ? 1.0F : super.maxUpStep();
	}

	@Override
	public void destroy(ServerLevel level, DamageSource source) {
		this.unchain(true);
		if (this.isExtended() && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			this.spawnAtLocation(level, this.extension);
		}
		super.destroy(level, source);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("chain_holder", UUIDUtil.CODEC, this.holderUuid);
		output.store("chain", ItemStack.CODEC, this.chain());
		if (this.isExtended()) {
			output.store("extension", ItemStack.CODEC, this.extension);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// Before the items: the size of the inventory depends on it.
		this.extension = input.read("extension", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		super.readAdditionalSaveData(input);
		this.holderUuid = input.read("chain_holder", UUIDUtil.CODEC).orElse(null);
		this.entityData.set(DATA_CHAIN, input.read("chain", ItemStack.CODEC).orElseGet(() -> new ItemStack(Items.IRON_CHAIN)));
		this.cachedHolder = null;
	}
}
