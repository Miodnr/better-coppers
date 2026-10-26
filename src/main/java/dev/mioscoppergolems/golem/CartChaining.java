package dev.mioscoppergolems.golem;

import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.guide.Guide;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Handing a cart over to a golem: chain the cart to yourself with an iron or copper chain, then right-click a copper golem.
 */
public final class CartChaining {
	private static final double SEARCH_RADIUS = 16.0;

	private CartChaining() {
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof CopperGolem golem) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		List<CopperChestMinecart> carts = cartsChainedTo(player);
		if (carts.isEmpty() || findCart(golem) != null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			carts.getFirst().chainTo(golem);
			Guide.award(player, "cart");
		}
		return InteractionResult.SUCCESS;
	}

	/** The cart this golem is towing, if any. */
	public static @Nullable CopperChestMinecart findCart(Entity holder) {
		List<CopperChestMinecart> carts = cartsChainedTo(holder);
		return carts.isEmpty() ? null : carts.getFirst();
	}

	private static List<CopperChestMinecart> cartsChainedTo(Entity holder) {
		return holder.level().getEntitiesOfClass(
			CopperChestMinecart.class, holder.getBoundingBox().inflate(SEARCH_RADIUS), cart -> cart.isAlive() && cart.isChainedTo(holder)
		);
	}
}
