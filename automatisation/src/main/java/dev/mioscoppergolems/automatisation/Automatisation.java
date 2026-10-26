package dev.mioscoppergolems.automatisation;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.charging.ChargingStationBlock;
import dev.mioscoppergolems.automatisation.golem.OperatorBehavior;
import dev.mioscoppergolems.automatisation.golem.Operators;
import dev.mioscoppergolems.automatisation.registry.AutoAttachments;
import dev.mioscoppergolems.automatisation.registry.AutoBlockEntities;
import dev.mioscoppergolems.automatisation.registry.AutoBlocks;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import dev.mioscoppergolems.automatisation.upgrade.Upgrades;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mios Coppergolems Automatisation: belts, funnels, chutes, presses, saws, grabber arms and a golem assembler. There is
 * no power network: every machine is worked by hand, by a copper golem holding a copper wrench. What the factory makes
 * goes back into the golems: upgrades, supplies for their jobs, bigger loads and new golems.
 */
public class Automatisation implements ModInitializer {
	public static final String MOD_ID = "mioscoppergolems_automatisation";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Golem Handbook chapters, in order; their texts are in the lang files. */
	private static final String[] HANDBOOK_CHAPTERS = {"machines", "belts", "press", "arm", "wear", "upgrades", "assembler"};

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
		MiosCoppergolemsApi.registerModifier(Upgrades.MODIFIER);
		MiosCoppergolemsApi.registerChargePad(ChargingStationBlock.PAD);
		UseEntityCallback.EVENT.register(Upgrades::onUseEntity);
		UseEntityCallback.EVENT.register(Operators::onUseEntity);
		for (String chapter : HANDBOOK_CHAPTERS) {
			MiosCoppergolemsApi.registerHandbookChapter(id(chapter));
		}
		LOGGER.info("Mios Coppergolems Automatisation initialised");
	}
}
