package dev.mioscoppergolems.home;

import dev.mioscoppergolems.farming.FarmerData;
import dev.mioscoppergolems.job.WorkerData;
import dev.mioscoppergolems.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Every golem with a job belongs to exactly one copper chest, its home, which the player links by hand (see
 * {@link HomeLinking}). Nothing is guessed from distance any more: a golem without a home waits.
 */
public final class Homes {
	private Homes() {
	}

	/** The linked home chest, if any. Homes from before linking existed are taken over once. */
	public static @Nullable BlockPos get(CopperGolem golem) {
		BlockPos home = golem.getAttached(ModAttachments.HOME);
		if (home != null) {
			return home;
		}
		FarmerData farmer = FarmerData.get(golem);
		WorkerData worker = WorkerData.get(golem);
		BlockPos legacy = farmer != null && farmer.home() != null ? farmer.home() : worker != null ? worker.home() : null;
		if (legacy != null) {
			set(golem, legacy);
		}
		return legacy;
	}

	public static void set(CopperGolem golem, @Nullable BlockPos home) {
		if (home == null) {
			golem.removeAttached(ModAttachments.HOME);
		} else {
			golem.setAttached(ModAttachments.HOME, home);
		}
		// Clear the old per-job fields so they cannot come back.
		FarmerData farmer = FarmerData.get(golem);
		if (farmer != null) {
			farmer.setHome(null);
		}
		WorkerData worker = WorkerData.get(golem);
		if (worker != null) {
			worker.setHome(null);
		}
	}

	/** The home chest's inventory, or null if the home is gone or not loaded. */
	public static @Nullable Container container(Level level, @Nullable BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof ChestBlock chestBlock && state.is(BlockTags.COPPER_CHESTS)
			? ChestBlock.getContainer(chestBlock, state, level, pos, true)
			: null;
	}

	/** The golem's home if it still is a copper chest. */
	public static @Nullable BlockPos valid(ServerLevel level, CopperGolem golem) {
		BlockPos home = get(golem);
		return container(level, home) != null ? home : null;
	}
}
