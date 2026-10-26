package dev.mioscoppergolems.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block another mod lets players link golems to: after sneak + right-clicking a golem with an empty hand, a
 * right-click on such a block calls {@link #link}. Checked before copper chests and courier routes.
 */
public interface GolemLinkTarget {
	boolean accepts(BlockState state);

	InteractionResult link(ServerPlayer player, ServerLevel level, CopperGolem golem, BlockPos pos);
}
