package dev.mioscoppergolems.automatisation.registry;

import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.arm.GrabberArmBlock;
import dev.mioscoppergolems.automatisation.belt.BeltDriveBlock;
import dev.mioscoppergolems.automatisation.belt.ConveyorBeltBlock;
import dev.mioscoppergolems.automatisation.chute.ChuteBlock;
import dev.mioscoppergolems.automatisation.funnel.FunnelBlock;
import dev.mioscoppergolems.automatisation.machine.MachinePartBlock;
import dev.mioscoppergolems.automatisation.press.PressBlock;
import dev.mioscoppergolems.automatisation.saw.SawBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class AutoBlocks {
	public static final Block CONVEYOR_BELT = register(
		"conveyor_belt", ConveyorBeltBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.WOOL).noOcclusion(), true
	);
	public static final Block BELT_DRIVE = register("belt_drive", BeltDriveBlock::new, machine(), true);
	public static final Block FUNNEL = register(
		"funnel", FunnelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER).noOcclusion(), true
	);
	public static final Block CHUTE = register(
		"chute", ChuteBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER_GRATE).noOcclusion(), true
	);
	public static final Block PRESS = register("press", PressBlock::new, machine(), true);
	public static final Block SAW = register("saw", SawBlock::new, machine(), true);
	public static final Block GRABBER_ARM = register("grabber_arm", GrabberArmBlock::new, machine(), true);
	/** Only holds models for the renderers; never placed, no item. */
	public static final Block MACHINE_PART = register("machine_part", MachinePartBlock::new, BlockBehaviour.Properties.of().noOcclusion().noLootTable(), false);

	private AutoBlocks() {
	}

	private static BlockBehaviour.Properties machine() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F).requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion();
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Automatisation.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		if (withItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Automatisation.id(name));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		}
		return block;
	}

	public static void init() {
	}
}
