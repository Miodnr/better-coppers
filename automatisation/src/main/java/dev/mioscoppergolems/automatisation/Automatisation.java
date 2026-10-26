package dev.mioscoppergolems.automatisation;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.golem.OperatorBehavior;
import dev.mioscoppergolems.automatisation.golem.Operators;
import dev.mioscoppergolems.automatisation.registry.AutoAttachments;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.automatisation.registry.AutoBlocks;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mios Coppergolems Automatisation: belts, funnels, chutes, presses, saws and grabber arms. There is no power network:
 * every machine is worked by hand, by a copper golem holding a copper wrench.
 */
public class Automatisation implements ModInitializer {
	public static final String MOD_ID = "mioscoppergolems_automatisation";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		AutoBlocks.init();
		AutoBlockEntities.init();
		AutoItems.init();
		AutoAttachments.init();
		MiosCoppergolemsApi.registerJob(Operators.JOB);
		MiosCoppergolemsApi.registerBehavior(OperatorBehavior::new);
		MiosCoppergolemsApi.registerLinkTarget(Operators.LINK);
		UseEntityCallback.EVENT.register(Operators::onUseEntity);
		LOGGER.info("Mios Coppergolems Automatisation initialised");
	}
}
