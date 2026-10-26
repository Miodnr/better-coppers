package dev.mioscoppergolems;

import dev.mioscoppergolems.combat.GolemGuards;
import dev.mioscoppergolems.courier.Couriers;
import dev.mioscoppergolems.farming.FarmerInteractions;
import dev.mioscoppergolems.golem.CartChaining;
import dev.mioscoppergolems.golem.Sorter;
import dev.mioscoppergolems.home.HomeDisplay;
import dev.mioscoppergolems.home.HomeLinking;
import dev.mioscoppergolems.job.WorkerInteractions;
import dev.mioscoppergolems.mining.MinerInteractions;
import dev.mioscoppergolems.registry.ModAttachments;
import dev.mioscoppergolems.registry.ModBlocks;
import dev.mioscoppergolems.registry.ModComponents;
import dev.mioscoppergolems.registry.ModEntities;
import dev.mioscoppergolems.registry.ModItems;
import dev.mioscoppergolems.registry.ModMenus;
import dev.mioscoppergolems.trim.GolemTrims;
import dev.mioscoppergolems.upkeep.AxeDispenseBehavior;
import dev.mioscoppergolems.upkeep.Upkeep;
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

public class MiosCoppergolems implements ModInitializer {
	public static final String MOD_ID = "mioscoppergolems";
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
		LOGGER.info("Mios Coppergolems initialised");
	}
}
