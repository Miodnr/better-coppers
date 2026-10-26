package dev.mioscoppergolems.registry;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.menu.SurveyTableMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<SurveyTableMenu> SURVEY_TABLE = Registry.register(
		BuiltInRegistries.MENU, MiosCoppergolems.id("survey_table"), new MenuType<>(SurveyTableMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
