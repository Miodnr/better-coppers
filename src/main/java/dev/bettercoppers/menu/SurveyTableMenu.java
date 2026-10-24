package dev.bettercoppers.menu;

import dev.bettercoppers.mining.MinePattern;
import dev.bettercoppers.mining.MinePlan;
import dev.bettercoppers.registry.ModBlocks;
import dev.bettercoppers.registry.ModComponents;
import dev.bettercoppers.registry.ModItems;
import dev.bettercoppers.registry.ModMenus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paper in the left slot, a mineshaft plan of the chosen length in the right slot. The length buttons (see the ids
 * below) are sent by the screen as menu button clicks.
 */
public class SurveyTableMenu extends AbstractContainerMenu {
	public static final int BUTTON_SHORTER = 2;
	public static final int BUTTON_LONGER = 3;
	private static final int LENGTH_STEP = 8;
	private static final int DEFAULT_LENGTH = 32;

	private final ContainerLevelAccess access;
	private final Container input = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			SurveyTableMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();
	private final DataSlot length = DataSlot.standalone();
	private final Slot inputSlot;

	public SurveyTableMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public SurveyTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.SURVEY_TABLE, containerId);
		this.access = access;
		this.inputSlot = this.addSlot(new Slot(this.input, 0, 20, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.PAPER);
			}
		});
		this.addSlot(new Slot(this.result, 1, 143, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				SurveyTableMenu.this.inputSlot.remove(1);
				SurveyTableMenu.this.updateResult();
				access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F));
				super.onTake(player, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, 84);
		this.length.set(DEFAULT_LENGTH);
		this.addDataSlot(this.length);
	}

	/** Golems only dig mineshafts; the pattern stays in the plan so older plans still load. */
	public MinePattern getPattern() {
		return MinePattern.MINESHAFT;
	}

	public int getLength() {
		return this.length.get();
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		switch (buttonId) {
			case BUTTON_SHORTER -> this.length.set(Math.max(MinePlan.MIN_LENGTH, this.length.get() - LENGTH_STEP));
			case BUTTON_LONGER -> this.length.set(Math.min(MinePlan.MAX_LENGTH, this.length.get() + LENGTH_STEP));
			default -> {
				return false;
			}
		}
		this.updateResult();
		return true;
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		this.updateResult();
	}

	private void updateResult() {
		if (this.input.getItem(0).is(Items.PAPER)) {
			ItemStack plan = new ItemStack(ModItems.MINESHAFT_PLAN);
			plan.set(ModComponents.MINE_PLAN, MinePlan.blank(this.getPattern(), this.getLength()));
			this.result.setItem(1, plan);
		} else {
			this.result.setItem(1, ItemStack.EMPTY);
		}
		this.broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.SURVEY_TABLE);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (slotIndex == 1) {
			if (!this.moveItemStackTo(stack, 2, 38, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (slotIndex == 0) {
			if (!this.moveItemStackTo(stack, 2, 38, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Items.PAPER)) {
			if (!this.moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < 29) {
			if (!this.moveItemStackTo(stack, 29, 38, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 2, 29, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		}
		slot.setChanged();
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.result.removeItemNoUpdate(1);
		this.access.execute((level, pos) -> this.clearContainer(player, this.input));
	}
}
