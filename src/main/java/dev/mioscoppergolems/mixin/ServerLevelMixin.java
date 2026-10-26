package dev.mioscoppergolems.mixin;

import dev.mioscoppergolems.upkeep.ChargeBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sleeping through the night charges the golems that have a pad, as if they had spent the night on it. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
	private void mioscoppergolems$rechargeOvernight(CallbackInfo info) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (level.getGameRules().get(GameRules.ADVANCE_TIME) && level.dimensionType().defaultClock().isPresent()) {
			ChargeBehavior.rechargeOvernight(level);
		}
	}
}
