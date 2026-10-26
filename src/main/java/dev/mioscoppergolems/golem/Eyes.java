package dev.mioscoppergolems.golem;

import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.guide.Guide;
import dev.mioscoppergolems.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Eye upgrades: right-click a golem with a block of iron, gold or diamond and its eyes glow in that colour, and it
 * works further from home: 1.5, 2 or 3 times its job's normal radius (fields, forest, fishing water, animals, the
 * containers a sorter knows, how far a miner follows a vein). A new block replaces the old one, which is given back;
 * the block drops when the golem dies. Couriers have no radius, so they don't take one.
 */
public final class Eyes {
	private static final Item[] BLOCKS = {Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.DIAMOND_BLOCK};
	private static final float[] MULTIPLIERS = {1.0F, 1.5F, 2.0F, 3.0F};

	private Eyes() {
	}

	/** 0 for plain eyes, 1 iron, 2 gold, 3 diamond. */
	public static int tier(CopperGolem golem) {
		Integer tier = golem.getAttached(ModAttachments.EYES);
		return tier == null ? 0 : Math.clamp(tier, 0, BLOCKS.length);
	}

	public static float multiplier(CopperGolem golem) {
		return MULTIPLIERS[tier(golem)];
	}

	/** A job's radius for this golem. */
	public static int radius(CopperGolem golem, int base) {
		return Math.round(base * multiplier(golem));
	}

	private static int tierOf(ItemStack stack) {
		for (int i = 0; i < BLOCKS.length; i++) {
			if (stack.is(BLOCKS[i])) {
				return i + 1;
			}
		}
		return 0;
	}

	public static @Nullable ItemStack block(CopperGolem golem) {
		int tier = tier(golem);
		return tier == 0 ? null : new ItemStack(BLOCKS[tier - 1]);
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		int tier = tierOf(held);
		if (tier == 0 || tier == tier(golem)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			if (Couriers.isCourier(golem)) {
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.eyes_courier"));
				return InteractionResult.FAIL;
			}
			ItemStack old = block(golem);
			if (old != null && !player.getInventory().add(old)) {
				golem.spawnAtLocation(serverLevel, old);
			}
			golem.setAttached(ModAttachments.EYES, tier);
			held.consume(1, player);
			golem.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F + tier * 0.2F);
			serverLevel.sendParticles(ParticleTypes.GLOW, golem.getX(), golem.getY() + 0.75, golem.getZ(), 8, 0.2, 0.15, 0.2, 0.0);
			player.sendOverlayMessage(Component.translatable("message.mioscoppergolems.eyes_set", Math.round(multiplier(golem) * 100)));
			Guide.award(player, "eyes");
		}
		return InteractionResult.SUCCESS;
	}

	/** Drops the eye block (on death, or when the golem turns into a statue). */
	public static void drop(ServerLevel level, CopperGolem golem) {
		ItemStack block = block(golem);
		golem.removeAttached(ModAttachments.EYES);
		if (block != null) {
			golem.spawnAtLocation(level, block);
		}
	}
}
