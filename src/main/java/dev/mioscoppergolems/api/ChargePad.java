package dev.mioscoppergolems.api;

import net.minecraft.world.level.block.state.BlockState;

/** A block golems recharge on every tick, besides the Charging Pad (which charges on redstone pulses). */
public interface ChargePad {
	boolean isPad(BlockState state);

	/** Energy per tick (full is 1000; a Charging Pad on the fastest clock gives about 2 per tick). */
	int chargePerTick();
}
