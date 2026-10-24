package dev.bettercoppers;

import dev.bettercoppers.combat.GolemGuards;
import dev.bettercoppers.courier.Couriers;
import dev.bettercoppers.farming.FarmerInteractions;
import dev.bettercoppers.golem.CartChaining;
import dev.bettercoppers.golem.Sorter;
import dev.bettercoppers.home.HomeDisplay;
import dev.bettercoppers.home.HomeLinking;
import dev.bettercoppers.job.WorkerInteractions;
import dev.bettercoppers.mining.MinerInteractions;
import dev.bettercoppers.registry.ModAttachments;
import dev.bettercoppers.registry.ModBlocks;
import dev.bettercoppers.registry.ModComponents;
import dev.bettercoppers.registry.ModEntities;
import dev.bettercoppers.registry.ModItems;
import dev.bettercoppers.registry.ModMenus;
import dev.bettercoppers.trim.GolemTrims;
import dev.bettercoppers.upkeep.AxeDispenseBehavior;
import dev.bettercoppers.upkeep.Upkeep;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterCoppers implements ModInitializer {
	public static final String MOD_ID = "bettercoppers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModAttachments.init();
		ModEntities.init();
		ModBlocks.init();
		ModItems.init();
		ModMenus.init();
		Couriers.init();
		UseEntityCallback.EVENT.register(HomeLinking::onUseEntity);
		UseEntityCallback.EVENT.register(GolemTrims::onUseEntity);
		UseEntityCallback.EVENT.register(MinerInteractions::onUseEntity);
		UseEntityCallback.EVENT.register(FarmerInteractions::onUseEntity);
		UseEntityCallback.EVENT.register(WorkerInteractions::onUseEntity);
		UseEntityCallback.EVENT.register(CartChaining::onUseEntity);
		UseEntityCallback.EVENT.register(Sorter::onUseEntity);
		UseBlockCallback.EVENT.register(HomeLinking::onUseBlock);
		ServerTickEvents.END_LEVEL_TICK.register(HomeDisplay::tick);
		ServerEntityEvents.ENTITY_LOAD.register(GolemGuards::onEntityLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Couriers::onEntityLoad);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof CopperGolem golem) {
				Upkeep.dropJobItems(golem);
			}
		});
		AxeDispenseBehavior.register();
		LOGGER.info("Better Coppers initialised");
	}
}
