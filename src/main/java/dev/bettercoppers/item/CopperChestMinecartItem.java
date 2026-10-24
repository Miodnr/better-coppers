package dev.bettercoppers.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Places like a normal minecart on rails, but can also be put down on any block, since golems tow it off rails.
 */
public class CopperChestMinecartItem extends MinecartItem {
	private final EntityType<? extends AbstractMinecart> type;

	public CopperChestMinecartItem(EntityType<? extends AbstractMinecart> type, Properties properties) {
		super(type, properties);
		this.type = type;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (level.getBlockState(pos).is(BlockTags.RAILS)) {
			return super.useOn(context);
		}

		BlockPos target = pos.relative(context.getClickedFace());
		Vec3 spawnPos = Vec3.atBottomCenterOf(target);
		AbstractMinecart cart = AbstractMinecart.createMinecart(
			level, spawnPos.x, spawnPos.y, spawnPos.z, this.type, EntitySpawnReason.DISPENSER, context.getItemInHand(), context.getPlayer()
		);
		if (cart == null || !level.noCollision(cart, cart.getBoundingBox())) {
			return InteractionResult.FAIL;
		}
		if (context.getPlayer() != null) {
			cart.setYRot(context.getPlayer().getYRot() + 90.0F);
		}

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.addFreshEntity(cart);
			serverLevel.gameEvent(GameEvent.ENTITY_PLACE, target, GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(target.below())));
		}

		context.getItemInHand().shrink(1);
		return InteractionResult.SUCCESS;
	}
}
