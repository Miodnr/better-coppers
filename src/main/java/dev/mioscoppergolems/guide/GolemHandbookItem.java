package dev.mioscoppergolems.guide;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Golem Handbook: everything about the mod (and its add-ons) as a book. Every player gets one when they first join
 * a world, from the guide's root advancement; it can also be crafted from a book and a copper ingot. The pages are
 * laid out on the client, from translations, so they follow the game language.
 */
public class GolemHandbookItem extends Item {
	/** Set by the client; opens the handbook screen. */
	public static Runnable opener = () -> {
	};

	public GolemHandbookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.run();
		} else {
			player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}
}
