package dev.mioscoppergolems.automatisation.registry;

import dev.mioscoppergolems.automatisation.Automatisation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;

public final class AutoAttachments {
	/** The machine an operator golem works, linked by the player. */
	public static final AttachmentType<BlockPos> MACHINE = AttachmentRegistry.create(
		Automatisation.id("machine"), builder -> builder.persistent(BlockPos.CODEC)
	);

	private AutoAttachments() {
	}

	public static void init() {
	}
}
