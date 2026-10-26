package dev.mioscoppergolems.automatisation.registry;

import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.arm.GrabberArmBlockEntity;
import dev.mioscoppergolems.automatisation.assembler.GolemAssemblerBlockEntity;
import dev.mioscoppergolems.automatisation.belt.BeltDriveBlockEntity;
import dev.mioscoppergolems.automatisation.belt.ConveyorBeltBlockEntity;
import dev.mioscoppergolems.automatisation.chute.ChuteBlockEntity;
import dev.mioscoppergolems.automatisation.funnel.FunnelBlockEntity;
import dev.mioscoppergolems.automatisation.press.PressBlockEntity;
import dev.mioscoppergolems.automatisation.saw.SawBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class AutoBlockEntities {
	public static final BlockEntityType<ConveyorBeltBlockEntity> CONVEYOR_BELT = register("conveyor_belt", ConveyorBeltBlockEntity::new, AutoBlocks.CONVEYOR_BELT);
	public static final BlockEntityType<BeltDriveBlockEntity> BELT_DRIVE = register("belt_drive", BeltDriveBlockEntity::new, AutoBlocks.BELT_DRIVE);
	public static final BlockEntityType<FunnelBlockEntity> FUNNEL = register("funnel", FunnelBlockEntity::new, AutoBlocks.FUNNEL);
	public static final BlockEntityType<ChuteBlockEntity> CHUTE = register("chute", ChuteBlockEntity::new, AutoBlocks.CHUTE);
	public static final BlockEntityType<PressBlockEntity> PRESS = register("press", PressBlockEntity::new, AutoBlocks.PRESS);
	public static final BlockEntityType<SawBlockEntity> SAW = register("saw", SawBlockEntity::new, AutoBlocks.SAW);
	public static final BlockEntityType<GrabberArmBlockEntity> GRABBER_ARM = register("grabber_arm", GrabberArmBlockEntity::new, AutoBlocks.GRABBER_ARM);
	public static final BlockEntityType<GolemAssemblerBlockEntity> GOLEM_ASSEMBLER = register(
		"golem_assembler", GolemAssemblerBlockEntity::new, AutoBlocks.GOLEM_ASSEMBLER
	);

	private AutoBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Automatisation.id(name), new BlockEntityType<>(factory, Set.of(block)));
	}

	public static void init() {
	}
}
