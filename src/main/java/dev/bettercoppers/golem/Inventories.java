package dev.bettercoppers.golem;

import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Small container helpers shared by the sorting and mining golems. */
public final class Inventories {
	private Inventories() {
	}

	/** Takes up to {@code limit} items of the first non-empty slot that matches. */
	public static ItemStack takeFirst(Container container, int limit, Predicate<ItemStack> filter) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				ItemStack taken = container.removeItem(slot, Math.min(stack.getCount(), limit));
				container.setChanged();
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}

	public static boolean containsItem(Container container, ItemStack item) {
		return contains(container, stack -> ItemStack.isSameItem(stack, item));
	}

	public static boolean contains(Container container, Predicate<ItemStack> filter) {
		for (ItemStack stack : container) {
			if (!stack.isEmpty() && filter.test(stack)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasRoom(Container container, ItemStack item) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() ? container.canPlaceItem(slot, item)
				: ItemStack.isSameItemSameComponents(stack, item) && stack.getCount() < container.getMaxStackSize(stack)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasEmptySlot(Container container) {
		for (ItemStack stack : container) {
			if (stack.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Moves as much of {@code stack} as fits into the container, topping up existing stacks first. Returns the rest. */
	public static ItemStack insert(Container container, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, rest)) {
				int moved = Math.min(rest.getCount(), container.getMaxStackSize(existing) - existing.getCount());
				if (moved > 0) {
					existing.grow(moved);
					rest.shrink(moved);
					container.setItem(slot, existing);
				}
			}
		}
		for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
			if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, rest)) {
				int moved = Math.min(rest.getCount(), container.getMaxStackSize(rest));
				container.setItem(slot, rest.split(moved));
			}
		}
		container.setChanged();
		return rest;
	}
}
