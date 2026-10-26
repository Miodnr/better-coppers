package dev.mioscoppergolems.automatisation.registry;

import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.item.WrenchItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class AutoItems {
	public static final Item COPPER_WRENCH = register("copper_wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
	public static final Item COPPER_SHEET = register("copper_sheet", Item::new, new Item.Properties());
	public static final Item IRON_SHEET = register("iron_sheet", Item::new, new Item.Properties());
	public static final Item GOLD_SHEET = register("gold_sheet", Item::new, new Item.Properties());
	public static final Item COPPER_GEAR = register("copper_gear", Item::new, new Item.Properties());
	public static final Item GOLD_GEAR = register("gold_gear", Item::new, new Item.Properties());
	public static final Item IRON_PLATING = register("iron_plating", Item::new, new Item.Properties().stacksTo(16));
	public static final Item COPPER_COATING = register("copper_coating", Item::new, new Item.Properties().stacksTo(16));
	public static final Item GOLD_GEARWORK = register("gold_gearwork", Item::new, new Item.Properties().stacksTo(16));
	public static final Item COPPER_SATCHEL = register("copper_satchel", Item::new, new Item.Properties().stacksTo(1));
	public static final Item CART_EXTENSION = register("cart_extension", Item::new, new Item.Properties().stacksTo(16));

	private AutoItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Automatisation.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.BRUSH, COPPER_WRENCH));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.insertAfter(Items.COPPER_INGOT, COPPER_SHEET);
			output.insertAfter(Items.IRON_INGOT, IRON_SHEET);
			output.insertAfter(Items.GOLD_INGOT, GOLD_SHEET);
			output.insertAfter(COPPER_SHEET, COPPER_GEAR);
			output.insertAfter(GOLD_SHEET, GOLD_GEAR);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(COPPER_WRENCH, IRON_PLATING);
			output.insertAfter(IRON_PLATING, COPPER_COATING);
			output.insertAfter(COPPER_COATING, GOLD_GEARWORK);
			output.insertAfter(GOLD_GEARWORK, COPPER_SATCHEL);
			output.insertAfter(COPPER_SATCHEL, CART_EXTENSION);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.insertAfter(Items.HOPPER, AutoBlocks.CONVEYOR_BELT);
			output.insertAfter(AutoBlocks.CONVEYOR_BELT, AutoBlocks.BELT_DRIVE);
			output.insertAfter(AutoBlocks.BELT_DRIVE, AutoBlocks.FUNNEL);
			output.insertAfter(AutoBlocks.FUNNEL, AutoBlocks.CHUTE);
			output.insertAfter(AutoBlocks.CHUTE, AutoBlocks.PRESS);
			output.insertAfter(AutoBlocks.PRESS, AutoBlocks.SAW);
			output.insertAfter(AutoBlocks.SAW, AutoBlocks.GRABBER_ARM);
			output.insertAfter(AutoBlocks.GRABBER_ARM, AutoBlocks.GOLEM_ASSEMBLER);
			output.insertAfter(AutoBlocks.GOLEM_ASSEMBLER, AutoBlocks.CHARGING_STATION);
		});
	}
}
