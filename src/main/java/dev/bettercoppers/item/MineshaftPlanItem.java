package dev.bettercoppers.item;

import dev.bettercoppers.mining.MinePlan;
import dev.bettercoppers.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Made at the survey table. Use it on the ground (or on a wall) where the mine should start; the tunnel heads the way
 * you are looking. Then hand it to a copper golem that has a pickaxe.
 */
public class MineshaftPlanItem extends Item {
	public MineshaftPlanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		MinePlan plan = stack.get(ModComponents.MINE_PLAN);
		Player player = context.getPlayer();
		if (plan == null || player == null) {
			return InteractionResult.PASS;
		}
		// Clicking the ground starts the tunnel above it; clicking a wall starts the tunnel in that wall.
		BlockPos entrance = context.getClickedFace() == Direction.UP ? context.getClickedPos().above() : context.getClickedPos();
		Direction heading = player.getDirection();
		if (!context.getLevel().isClientSide()) {
			stack.set(ModComponents.MINE_PLAN, plan.marked(entrance, heading));
			player.sendOverlayMessage(
				Component.translatable("message.bettercoppers.plan_marked", entrance.getX(), entrance.getY(), entrance.getZ(), directionName(heading))
			);
			context.getLevel().playSound(null, entrance, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		MinePlan plan = stack.get(ModComponents.MINE_PLAN);
		if (plan == null) {
			return;
		}
		builder.accept(plan.pattern().displayName().copy().withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.bettercoppers.length", plan.length()).withStyle(ChatFormatting.GRAY));
		if (plan.isMarked()) {
			BlockPos entrance = plan.entrance().orElseThrow();
			builder.accept(
				Component.translatable("tooltip.bettercoppers.entrance", entrance.getX(), entrance.getY(), entrance.getZ(), directionName(plan.heading().orElseThrow()))
					.withStyle(ChatFormatting.GRAY)
			);
		} else {
			builder.accept(Component.translatable("tooltip.bettercoppers.unmarked").withStyle(ChatFormatting.YELLOW));
		}
		if (plan.progress() > 0) {
			builder.accept(Component.translatable("tooltip.bettercoppers.progress", plan.progress()).withStyle(ChatFormatting.GRAY));
		}
	}

	public static Component directionName(Direction direction) {
		return Component.translatable("direction.bettercoppers." + direction.getSerializedName());
	}
}
