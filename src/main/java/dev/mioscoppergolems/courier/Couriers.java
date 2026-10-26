package dev.mioscoppergolems.courier;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.home.Homes;
import dev.mioscoppergolems.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Couriers carry everything from one chest to another, however far apart they are. On the way they keep the chunks
 * around them loaded, like a thrown ender pearl, so a route works even when no player is near.
 */
public final class Couriers {
	/** Loads and ticks the chunks around a travelling courier; expires two seconds after the last refresh. */
	public static final TicketType TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		MiosCoppergolems.id("courier"),
		new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE)
	);
	/** Radius 3: the golem's chunk and every chunk next to it tick entities, so it never walks into a frozen one. */
	private static final int TICKET_RADIUS = 3;

	/** Routes linked while their golem was far away and unloaded; handed over once it loads again. */
	private static final Map<UUID, CourierRoute> PENDING = new HashMap<>();

	private Couriers() {
	}

	public static void init() {
	}

	public static @Nullable CourierRoute route(CopperGolem golem) {
		return golem.getAttached(ModAttachments.ROUTE);
	}

	public static boolean isCourier(CopperGolem golem) {
		return route(golem) != null;
	}

	/** Makes the golem a courier on this route. A courier has no home; linking a home again ends the route. */
	public static void setRoute(CopperGolem golem, @Nullable CourierRoute route) {
		if (route == null) {
			golem.removeAttached(ModAttachments.ROUTE);
		} else {
			golem.setAttached(ModAttachments.ROUTE, route);
			Homes.set(golem, null);
		}
	}

	/** Gives the route to the golem now if it is loaded, or as soon as it loads. */
	public static void assign(ServerLevel level, UUID golemId, CourierRoute route) {
		if (level.getEntity(golemId) instanceof CopperGolem golem) {
			setRoute(golem, route);
		} else {
			PENDING.put(golemId, route);
		}
	}

	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof CopperGolem golem) {
			CourierRoute route = PENDING.remove(golem.getUUID());
			if (route != null) {
				setRoute(golem, route);
			}
		}
	}

	/** Keeps the chunks around the golem loaded for the next two seconds. */
	public static void keepLoaded(ServerLevel level, Entity golem) {
		level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(golem.blockPosition()), TICKET_RADIUS);
	}

	/** Plain chests and barrels can start a route. */
	public static boolean canStartRoute(BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
	}

	/** Any chest (copper chests too) or barrel can end one. */
	public static boolean canEndRoute(BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.is(Blocks.BARREL);
	}

	/** The container at a route end, double chests combined; null if it is gone, locked or not loaded. */
	public static @Nullable Container container(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!canEndRoute(state)) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof BaseContainerBlockEntity base && base.isLocked()) {
			return null;
		}
		if (state.getBlock() instanceof ChestBlock chestBlock) {
			return ChestBlock.getContainer(chestBlock, state, level, pos, false);
		}
		return blockEntity instanceof Container container ? container : null;
	}
}
