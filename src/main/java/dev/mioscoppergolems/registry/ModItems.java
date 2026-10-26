package dev.mioscoppergolems.registry;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.item.CopperChestMinecartItem;
import dev.mioscoppergolems.item.MineshaftPlanItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {
	public static final Item COPPER_CHEST_MINECART = register(
		"copper_chest_minecart", p -> new CopperChestMinecartItem(ModEntities.COPPER_CHEST_MINECART, p), new Item.Properties().stacksTo(1)
	);
	public static final Item MINESHAFT_PLAN = register("mineshaft_plan", MineshaftPlanItem::new, new Item.Properties().stacksTo(1));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MiosCoppergolems.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.insertAfter(Items.CHEST_MINECART, COPPER_CHEST_MINECART));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> output.insertAfter(Items.CARTOGRAPHY_TABLE, ModBlocks.SURVEY_TABLE));
	}
}
