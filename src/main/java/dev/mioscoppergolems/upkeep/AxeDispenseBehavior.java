package dev.mioscoppergolems.upkeep;

import dev.mioscoppergolems.guide.Guide;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * An axe in a dispenser scrapes the copper golem standing in front of it when the dispenser gets a redstone pulse:
 * one stage of rust off, or the wax. The axe stays in the dispenser and loses durability; with no golem there
 * nothing happens.
 */
public class AxeDispenseBehavior extends OptionalDispenseItemBehavior {
	public static void register() {
		AxeDispenseBehavior behavior = new AxeDispenseBehavior();
		for (var axe : List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE)) {
			DispenserBlock.registerBehavior(axe, behavior);
		}
	}

	@Override
	protected ItemStack execute(BlockSource source, ItemStack stack) {
		ServerLevel level = source.level();
		BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		this.setSuccess(false);
		for (CopperGolem golem : level.getEntitiesOfClass(CopperGolem.class, new AABB(front))) {
			if (Upkeep.scrape(level, golem)) {
				stack.hurtAndBreak(1, level, null, item -> {});
				this.setSuccess(true);
				Guide.awardNearby(level, golem.position(), "scrape");
				break;
			}
		}
		return stack;
	}
}
