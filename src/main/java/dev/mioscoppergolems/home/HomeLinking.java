package dev.mioscoppergolems.home;

import dev.mioscoppergolems.api.GolemLinkTarget;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.courier.CourierRoute;
import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Linking a golem to its home chest: sneak + right-click the golem with an empty hand (it glows and shows its status),
 * then right-click a copper chest within {@link #MAX_DISTANCE} blocks of it. The pick expires after half a minute.
 * <p>
 * Right-clicking a plain chest or barrel instead starts a courier route; the next chest or barrel clicked, at any
 * distance in the same dimension, ends it. There are {@link #ROUTE_TICKS ten minutes} to walk there.
 */
public final class HomeLinking {
	private static final int SELECTION_TICKS = 600;
	private static final int ROUTE_TICKS = 12000;
	private static final double MAX_DISTANCE = 32.0;
	/** A route this long earns the "Long Haul" guide step. */
	private static final int LONG_HAUL = 500;

	private record Selection(UUID golem, ResourceKey<Level> dimension, long until, @Nullable BlockPos routeStart) {
	}

	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

	private HomeLinking() {
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || hand != InteractionHand.MAIN_HAND || player.isSpectator()
			|| !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			SELECTIONS.put(player.getUUID(), new Selection(golem.getUUID(), serverLevel.dimension(), serverLevel.getGameTime() + SELECTION_TICKS, null));
			golem.addEffect(new MobEffectInstance(MobEffects.GLOWING, SELECTION_TICKS, 0, false, false));
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET, 0.6F, 1.4F);
			player.sendSystemMessage(Component.translatable("message.mioscoppergolems.selected", Upkeep.status(serverLevel, golem)));
		}
		return InteractionResult.SUCCESS;
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel serverLevel)) {
			// The client cannot know about the pick; the server answer corrects it.
			return InteractionResult.PASS;
		}
		Selection selection = SELECTIONS.get(player.getUUID());
		BlockPos pos = hitResult.getBlockPos();
		if (selection == null) {
			return InteractionResult.PASS;
		}
		if (serverLevel.getGameTime() > selection.until() || serverLevel.dimension() != selection.dimension()) {
			SELECTIONS.remove(player.getUUID());
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		if (selection.routeStart() == null && player instanceof ServerPlayer serverPlayer) {
			for (GolemLinkTarget target : MiosCoppergolemsApi.linkTargets()) {
				if (target.accepts(state)) {
					SELECTIONS.remove(player.getUUID());
					if (!(serverLevel.getEntity(selection.golem()) instanceof CopperGolem golem)) {
						return InteractionResult.PASS;
					}
					golem.removeEffect(MobEffects.GLOWING);
					return target.link(serverPlayer, serverLevel, golem, pos);
				}
			}
		}
		if (selection.routeStart() != null) {
			return Couriers.canEndRoute(state) ? endRoute(player, serverLevel, selection, pos) : InteractionResult.PASS;
		}
		if (Couriers.canStartRoute(state)) {
			SELECTIONS.put(player.getUUID(), new Selection(selection.golem(), selection.dimension(), serverLevel.getGameTime() + ROUTE_TICKS, pos));
			serverLevel.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.NEUTRAL, 1.0F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.route_start"));
			return InteractionResult.SUCCESS;
		}
		if (!state.is(BlockTags.COPPER_CHESTS)) {
			return InteractionResult.PASS;
		}
		SELECTIONS.remove(player.getUUID());
		if (!(serverLevel.getEntity(selection.golem()) instanceof CopperGolem golem)) {
			return InteractionResult.PASS;
		}
		if (golem.position().distanceTo(Vec3.atCenterOf(pos)) > MAX_DISTANCE) {
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.home_too_far"));
			return InteractionResult.FAIL;
		}
		Couriers.setRoute(golem, null);
		Homes.set(golem, pos);
		golem.removeEffect(MobEffects.GLOWING);
		serverLevel.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 1.0F, 1.2F);
		HomeDisplay.drawLine(serverLevel, golem, pos, null);
		player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.home_set", pos.getX(), pos.getY(), pos.getZ()));
		Guide.award(player, "home");
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult endRoute(Player player, ServerLevel level, Selection selection, BlockPos pos) {
		BlockPos start = selection.routeStart();
		if (sameContainer(level, start, pos)) {
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.route_same_chest"));
			return InteractionResult.FAIL;
		}
		SELECTIONS.remove(player.getUUID());
		Couriers.assign(level, selection.golem(), new CourierRoute(start, pos));
		if (level.getEntity(selection.golem()) instanceof CopperGolem golem) {
			golem.removeEffect(MobEffects.GLOWING);
		}
		level.playSound(null, pos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 1.0F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.route_set", (int) Math.sqrt(start.distSqr(pos))));
		Guide.job(player, "courier");
		if (start.distSqr(pos) >= LONG_HAUL * LONG_HAUL) {
			Guide.award(player, "long_haul");
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean sameContainer(Level level, BlockPos a, BlockPos b) {
		if (a.equals(b)) {
			return true;
		}
		BlockState state = level.getBlockState(a);
		return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE && ChestBlock.getConnectedBlockPos(a, state).equals(b);
	}
}
