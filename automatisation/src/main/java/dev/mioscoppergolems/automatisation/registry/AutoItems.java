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
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.insertAfter(Items.HOPPER, AutoBlocks.CONVEYOR_BELT);
			output.insertAfter(AutoBlocks.CONVEYOR_BELT, AutoBlocks.BELT_DRIVE);
			output.insertAfter(AutoBlocks.BELT_DRIVE, AutoBlocks.FUNNEL);
			output.insertAfter(AutoBlocks.FUNNEL, AutoBlocks.CHUTE);
			output.insertAfter(AutoBlocks.CHUTE, AutoBlocks.PRESS);
			output.insertAfter(AutoBlocks.PRESS, AutoBlocks.SAW);
			output.insertAfter(AutoBlocks.SAW, AutoBlocks.GRABBER_ARM);
		});
	}
}
