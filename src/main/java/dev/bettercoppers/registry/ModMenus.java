package dev.bettercoppers.registry;

import dev.bettercoppers.BetterCoppers;
import dev.bettercoppers.menu.SurveyTableMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<SurveyTableMenu> SURVEY_TABLE = Registry.register(
		BuiltInRegistries.MENU, BetterCoppers.id("survey_table"), new MenuType<>(SurveyTableMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
