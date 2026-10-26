package dev.mioscoppergolems.automatisation.charging;

import dev.mioscoppergolems.api.ChargePad;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gilded charging pad: a golem standing on it recharges every tick, twice as fast as on a Charging Pad with the
 * fastest redstone clock, and it needs no clock at all.
 */
public class ChargingStationBlock extends Block {
	public static final int CHARGE_PER_TICK = 4;

	public static final ChargePad PAD = new ChargePad() {
		@Override
		public boolean isPad(BlockState state) {
			return state.getBlock() instanceof ChargingStationBlock;
		}

		@Override
		public int chargePerTick() {
			return CHARGE_PER_TICK;
		}
	};

	public ChargingStationBlock(Properties properties) {
		super(properties);
	}
}
