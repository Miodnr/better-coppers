package dev.mioscoppergolems.automatisation.assembler;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.machine.MachineBlock;
import dev.mioscoppergolems.automatisation.machine.MachineBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Builds a new copper golem from {@link #RECIPE} in its nine slots, over {@link #BUILD_TICKS} of cranking, and sets it
 * down in front. Fill it by hand, with hoppers, funnels or a grabber arm.
 */
public class GolemAssemblerBlockEntity extends MachineBlockEntity implements net.minecraft.world.Container, MenuProvider {
	public static final int BUILD_TICKS = 400;
	public static final Map<Item, Integer> RECIPE = Map.of(
		AutoItems.COPPER_SHEET, 4,
		AutoItems.COPPER_GEAR, 2,
		Items.CARVED_PUMPKIN, 1,
		Items.REDSTONE, 1
	);
	private static final Component TITLE = Component.translatable("container.mioscoppergolems_automatisation.golem_assembler");

	private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
	public int progress;

	public GolemAssemblerBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.GOLEM_ASSEMBLER, pos, state);
	}

	/** Eight golems per sheet of repairs. */
	@Override
	protected int maxWear() {
		return 8;
	}

	@Override
	public boolean wantsWork(ServerLevel level) {
		return this.hasParts() && this.hasRoomInFront(level);
	}

	@Override
	protected void tickRunning(ServerLevel level) {
		if (!this.wantsWork(level)) {
			return;
		}
		this.progress++;
		if (this.progress % 20 == 0) {
			level.playSound(null, this.worldPosition, SoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, 0.4F, 0.8F + level.getRandom().nextFloat() * 0.3F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.05, this.worldPosition.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.02);
		}
		if (this.progress >= BUILD_TICKS) {
			this.progress = 0;
			this.build(level);
		}
	}

	private void build(ServerLevel level) {
		CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return;
		}
		BlockPos front = this.worldPosition.relative(this.getBlockState().getValue(MachineBlock.FACING));
		golem.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		for (Map.Entry<Item, Integer> part : RECIPE.entrySet()) {
			int left = part.getValue();
			for (ItemStack stack : this.items) {
				if (left > 0 && stack.is(part.getKey())) {
					int used = Math.min(left, stack.getCount());
					stack.shrink(used);
					left -= used;
				}
			}
		}
		this.setChanged();
		level.addFreshEntity(golem);
		MiosCoppergolemsApi.awardGuideNearby(level, golem.position(), Automatisation.id("guide/assembler"));
		level.playSound(null, front, SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.WAX_OFF, golem.getX(), golem.getY() + 0.5, golem.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
		this.addWear(level, 1);
	}

	private boolean hasParts() {
		for (Map.Entry<Item, Integer> part : RECIPE.entrySet()) {
			int count = 0;
			for (ItemStack stack : this.items) {
				if (stack.is(part.getKey())) {
					count += stack.getCount();
				}
			}
			if (count < part.getValue()) {
				return false;
			}
		}
		return true;
	}

	private boolean hasRoomInFront(ServerLevel level) {
		BlockPos front = this.worldPosition.relative(this.getBlockState().getValue(MachineBlock.FACING));
		return level.getBlockState(front).getCollisionShape(level, front).isEmpty()
			&& level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty()
			&& level.getEntitiesOfClass(CopperGolem.class, new AABB(front)).isEmpty();
	}

	// --- Container and menu: a 3x3 like a dispenser ---

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
		this.setChanged();
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.setChanged();
	}

	/** Only parts of the golem go in; hoppers and funnels leave everything else alone. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return RECIPE.containsKey(stack.getItem());
	}

	@Override
	public boolean stillValid(Player player) {
		return net.minecraft.world.Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public Component getDisplayName() {
		return TITLE;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new DispenserMenu(containerId, inventory, this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items.clear();
		ContainerHelper.loadAllItems(input, this.items);
		this.progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("progress", this.progress);
	}
}
