package dev.mioscoppergolems.automatisation.saw;

import dev.mioscoppergolems.automatisation.machine.ProcessingBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cuts the whole stack in one go: sheets into gears, logs into planks, planks into the fences, slabs or stairs the
 * filter shows, and stone-like blocks into whatever the filter asks for.
 */
public class SawBlockEntity extends ProcessingBlockEntity {
	public static final int CYCLE_TICKS = 50;
	public static final int HIT_TICK = 40;

	private ItemStack filter = ItemStack.EMPTY;

	public SawBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.SAW, pos, state);
	}

	public ItemStack filter() {
		return this.filter;
	}

	public void setFilter(ItemStack filter) {
		this.filter = filter;
		this.sync();
	}

	@Override
	protected int maxWear() {
		return 64;
	}

	@Override
	public int cycleTicks() {
		return CYCLE_TICKS;
	}

	@Override
	public int hitTick() {
		return HIT_TICK;
	}

	@Override
	protected @Nullable List<ItemStack> process(ServerLevel level, ItemStack input) {
		ItemStack perItem = gearFrom(input);
		if (perItem == null && !this.filter.isEmpty()) {
			perItem = woodworkFrom(input, this.filter);
		}
		if (perItem == null) {
			perItem = planksFrom(level, input);
		}
		if (perItem == null && !this.filter.isEmpty()) {
			perItem = cutInto(level, input, this.filter);
		}
		if (perItem == null) {
			return null;
		}
		List<ItemStack> results = new ArrayList<>();
		int total = perItem.getCount() * input.getCount();
		while (total > 0) {
			int count = Math.min(total, perItem.getMaxStackSize());
			results.add(perItem.copyWithCount(count));
			total -= count;
		}
		return results;
	}

	/** Copper and gold sheets are cut into gears. */
	private static @Nullable ItemStack gearFrom(ItemStack input) {
		if (input.is(AutoItems.COPPER_SHEET)) {
			return new ItemStack(AutoItems.COPPER_GEAR);
		}
		if (input.is(AutoItems.GOLD_SHEET)) {
			return new ItemStack(AutoItems.GOLD_GEAR);
		}
		return null;
	}

	/**
	 * Planks cut into the fence, slab or stairs of the same wood that the filter shows: one fence or stair, or two slabs,
	 * per plank. Woods are matched by name ({@code oak_planks} with {@code oak_fence}).
	 */
	private static @Nullable ItemStack woodworkFrom(ItemStack input, ItemStack filter) {
		Identifier planks = BuiltInRegistries.ITEM.getKey(input.getItem());
		Identifier wanted = BuiltInRegistries.ITEM.getKey(filter.getItem());
		if (!input.is(ItemTags.PLANKS) || !planks.getNamespace().equals(wanted.getNamespace()) || !planks.getPath().endsWith("_planks")) {
			return null;
		}
		String wood = planks.getPath().substring(0, planks.getPath().length() - "_planks".length());
		String shape = wanted.getPath();
		if (shape.equals(wood + "_fence") || shape.equals(wood + "_stairs")) {
			return filter.copyWithCount(1);
		}
		if (shape.equals(wood + "_slab")) {
			return filter.copyWithCount(2);
		}
		return null;
	}

	/** A log (or wood) cut into planks: half again as many as crafting gives. */
	private static @Nullable ItemStack planksFrom(ServerLevel level, ItemStack input) {
		CraftingInput craft = CraftingInput.of(1, 1, List.of(input.copyWithCount(1)));
		Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craft, level);
		if (recipe.isEmpty()) {
			return null;
		}
		ItemStack result = recipe.get().value().assemble(craft);
		return result.is(ItemTags.PLANKS) ? result.copyWithCount(result.getCount() * 3 / 2) : null;
	}

	/** The stonecutter recipe for this input that makes the filtered item. */
	private static @Nullable ItemStack cutInto(ServerLevel level, ItemStack input, ItemStack wanted) {
		SingleRecipeInput single = new SingleRecipeInput(input.copyWithCount(1));
		for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
			if (holder.value() instanceof StonecutterRecipe recipe && recipe.matches(single, level)) {
				ItemStack result = recipe.assemble(single);
				if (ItemStack.isSameItem(result, wanted)) {
					return result;
				}
			}
		}
		return null;
	}

	@Override
	protected void onHit(ServerLevel level, Vec3 where) {
		level.playSound(null, where.x, where.y, where.z, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 0.9F);
	}

	@Override
	protected void tickRunning(ServerLevel level) {
		super.tickRunning(level);
		if (this.active && level.getGameTime() % 8 == 0) {
			level.playSound(null, this.worldPosition, SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.BLOCKS, 0.25F, 0.6F);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.filter = input.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.filter.isEmpty()) {
			output.store("filter", ItemStack.CODEC, this.filter);
		}
	}
}
