package dev.bettercoppers.registry;

import dev.bettercoppers.BetterCoppers;
import dev.bettercoppers.mining.MinePlan;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	public static final DataComponentType<MinePlan> MINE_PLAN = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		BetterCoppers.id("mine_plan"),
		DataComponentType.<MinePlan>builder().persistent(MinePlan.CODEC).networkSynchronized(MinePlan.STREAM_CODEC).build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
