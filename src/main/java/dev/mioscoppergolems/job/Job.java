package dev.mioscoppergolems.job;

import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The jobs that share the home chest routine of {@link JobBehavior}, each picked by the tool the golem holds. */
public enum Job {
	FORESTER("forester", stack -> stack.is(Items.COPPER_AXE)),
	FISHER("fisher", stack -> stack.is(Items.FISHING_ROD)),
	KEEPER("keeper", stack -> stack.is(Items.BRUSH)),
	CRAFTER("crafter", stack -> stack.is(Items.CRAFTING_TABLE));

	private final String name;
	private final Predicate<ItemStack> tool;

	Job(String name, Predicate<ItemStack> tool) {
		this.name = name;
		this.tool = tool;
	}

	public boolean isTool(ItemStack stack) {
		return this.tool.test(stack);
	}

	public String id() {
		return this.name;
	}

	/** The job whose tool this is: the tool in hand, or the one that broke. */
	public static @Nullable Job forTool(Item item) {
		return forTool(new ItemStack(item));
	}

	public static @Nullable Job forTool(ItemStack stack) {
		for (Job job : values()) {
			if (job.isTool(stack)) {
				return job;
			}
		}
		return null;
	}
}
