package dev.bettercoppers.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bettercoppers.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Home chest, pouch and supplies of a forester, fisher, animal keeper or crafter golem, plus a few remembered places
 * (the forester's tree stumps waiting for a sapling). Stored on the golem as a Fabric attachment.
 */
public final class WorkerData {
	public static final Codec<WorkerData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				BlockPos.CODEC.optionalFieldOf("home").forGetter(data -> Optional.ofNullable(data.home)),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pouch", List.of()).forGetter(data -> data.pouch),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("supplies", List.of()).forGetter(data -> data.supplies),
				BlockPos.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(data -> data.marks)
			)
			.apply(instance, (home, pouch, supplies, marks) -> {
				WorkerData data = new WorkerData();
				data.home = home.orElse(null);
				data.pouch.addAll(pouch.stream().filter(stack -> !stack.isEmpty()).toList());
				data.supplies.addAll(supplies.stream().filter(stack -> !stack.isEmpty()).toList());
				data.marks.addAll(marks);
				return data;
			})
	);

	private @Nullable BlockPos home;
	private final List<ItemStack> pouch = new ArrayList<>();
	private final List<ItemStack> supplies = new ArrayList<>();
	private final List<BlockPos> marks = new ArrayList<>();

	public static @Nullable WorkerData get(CopperGolem golem) {
		return golem.getAttached(ModAttachments.WORKER);
	}

	public static WorkerData getOrCreate(CopperGolem golem) {
		return golem.getAttachedOrCreate(ModAttachments.WORKER, WorkerData::new);
	}

	/** The job of the tool in the golem's hand, if a player made it a worker. */
	public static @Nullable Job job(CopperGolem golem) {
		return get(golem) == null ? null : Job.forTool(golem.getMainHandItem());
	}

	/** Holds a job tool, or still has something in its pouch to bring home. Workers do not sort. */
	public static boolean isWorker(CopperGolem golem) {
		WorkerData data = get(golem);
		return data != null && (Job.forTool(golem.getMainHandItem()) != null || data.pouchCount() > 0);
	}

	public @Nullable BlockPos home() {
		return this.home;
	}

	public void setHome(@Nullable BlockPos home) {
		this.home = home;
	}

	public List<ItemStack> pouch() {
		return this.pouch;
	}

	public List<ItemStack> supplies() {
		return this.supplies;
	}

	public List<BlockPos> marks() {
		return this.marks;
	}

	public int pouchCount() {
		return this.pouch.stream().mapToInt(ItemStack::getCount).sum();
	}

	/** Adds to a pouch, merging with existing stacks. */
	public static void addTo(List<ItemStack> pouch, ItemStack stack) {
		ItemStack rest = stack.copy();
		for (ItemStack existing : pouch) {
			if (rest.isEmpty()) {
				return;
			}
			if (ItemStack.isSameItemSameComponents(existing, rest) && existing.getCount() < existing.getMaxStackSize()) {
				int moved = Math.min(rest.getCount(), existing.getMaxStackSize() - existing.getCount());
				existing.grow(moved);
				rest.shrink(moved);
			}
		}
		if (!rest.isEmpty()) {
			pouch.add(rest);
		}
	}

	/** Takes one matching item out of a pouch, or an empty stack. */
	public static ItemStack takeOne(List<ItemStack> pouch, Predicate<ItemStack> filter) {
		for (ItemStack stack : pouch) {
			if (!stack.isEmpty() && filter.test(stack)) {
				ItemStack one = stack.split(1);
				pouch.removeIf(ItemStack::isEmpty);
				return one;
			}
		}
		return ItemStack.EMPTY;
	}

	public static int countOf(List<ItemStack> pouch, Predicate<ItemStack> filter) {
		return pouch.stream().filter(filter).mapToInt(ItemStack::getCount).sum();
	}
}
