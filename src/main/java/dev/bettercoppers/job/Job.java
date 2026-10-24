package dev.bettercoppers.job;

import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The jobs that share the home chest routine of {@link JobBehavior}, each picked by the tool the golem holds. */
public enum Job {
	FORESTER("forester", stack -> stack.is(ItemTags.AXES)),
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

	public static @Nullable Job forTool(ItemStack stack) {
		for (Job job : values()) {
			if (job.isTool(stack)) {
				return job;
			}
		}
		return null;
	}
}
