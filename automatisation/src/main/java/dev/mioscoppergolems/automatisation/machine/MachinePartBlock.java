package dev.mioscoppergolems.automatisation.machine;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Never placed in the world: its block states only carry the models of moving machine parts (press head, saw blade,
 * crank, arm segments), which the block entity renderers draw, turned and shifted as the machines work.
 */
public class MachinePartBlock extends Block {
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	public MachinePartBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PART, Part.CRANK));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}

	public enum Part implements StringRepresentable {
		CRANK("crank"),
		GEAR("gear"),
		PRESS_HEAD("press_head"),
		SAW_BLADE("saw_blade"),
		ARM_TURRET("arm_turret"),
		ARM_SEGMENT("arm_segment"),
		ARM_CLAW("arm_claw");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
