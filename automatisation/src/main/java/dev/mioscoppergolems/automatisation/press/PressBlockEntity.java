package dev.mioscoppergolems.automatisation.press;

import dev.mioscoppergolems.automatisation.machine.ProcessingBlockEntity;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The head comes down at {@link #HIT_TICK}, flattens the whole stack at once, and goes back up. */
public class PressBlockEntity extends ProcessingBlockEntity {
	public static final int CYCLE_TICKS = 40;
	public static final int HIT_TICK = 20;

	public PressBlockEntity(BlockPos pos, BlockState state) {
		super(AutoBlockEntities.PRESS, pos, state);
	}

	private static Map<Item, Item> recipes() {
		return Map.of(
			Items.COPPER_INGOT, AutoItems.COPPER_SHEET,
			Items.IRON_INGOT, AutoItems.IRON_SHEET,
			Items.GOLD_INGOT, AutoItems.GOLD_SHEET,
			Items.SUGAR_CANE, Items.PAPER
		);
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
		Item result = recipes().get(input.getItem());
		return result == null ? null : List.of(new ItemStack(result, input.getCount()));
	}

	@Override
	protected void onHit(ServerLevel level, Vec3 where) {
		level.playSound(null, where.x, where.y, where.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35F, 1.4F + level.getRandom().nextFloat() * 0.2F);
	}
}
