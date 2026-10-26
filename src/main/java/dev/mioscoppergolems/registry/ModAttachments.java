package dev.mioscoppergolems.registry;

import com.mojang.serialization.Codec;
import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.courier.CourierRoute;
import dev.mioscoppergolems.farming.FarmerData;
import dev.mioscoppergolems.job.WorkerData;
import dev.mioscoppergolems.mining.MinerData;
import dev.mioscoppergolems.upkeep.GolemUpkeep;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

public final class ModAttachments {
	/** Plan, pouch and supplies of a copper golem that was handed a pickaxe. */
	public static final AttachmentType<MinerData> MINER = AttachmentRegistry.create(
		MiosCoppergolems.id("miner"), builder -> builder.persistent(MinerData.CODEC).initializer(MinerData::new)
	);

	/** Home chest, harvest pouch and seeds of a copper golem that was handed a hoe. */
	public static final AttachmentType<FarmerData> FARMER = AttachmentRegistry.create(
		MiosCoppergolems.id("farmer"), builder -> builder.persistent(FarmerData.CODEC).initializer(FarmerData::new)
	);

	/** Home chest, pouch and supplies of a forester, fisher, animal keeper or crafter golem. */
	public static final AttachmentType<WorkerData> WORKER = AttachmentRegistry.create(
		MiosCoppergolems.id("worker"), builder -> builder.persistent(WorkerData.CODEC).initializer(WorkerData::new)
	);
	/** Energy and wear of any working golem. */
	public static final AttachmentType<GolemUpkeep> UPKEEP = AttachmentRegistry.create(
		MiosCoppergolems.id("upkeep"), builder -> builder.persistent(GolemUpkeep.CODEC).initializer(GolemUpkeep::new)
	);

	/** The copper chest a golem belongs to, linked by the player. */
	public static final AttachmentType<BlockPos> HOME = AttachmentRegistry.create(
		MiosCoppergolems.id("home"), builder -> builder.persistent(BlockPos.CODEC)
	);

	/** The two chests a courier carries items between, linked by the player. */
	public static final AttachmentType<CourierRoute> ROUTE = AttachmentRegistry.create(
		MiosCoppergolems.id("route"), builder -> builder.persistent(CourierRoute.CODEC)
	);

	/** The armour trim a golem wears; every client needs it to draw the golem. */
	public static final AttachmentType<ArmorTrim> TRIM = AttachmentRegistry.create(
		MiosCoppergolems.id("trim"), builder -> builder.persistent(ArmorTrim.CODEC).syncWith(ArmorTrim.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	/** The smithing template behind the trim, given back when the trim comes off. */
	public static final AttachmentType<ItemStack> TRIM_TEMPLATE = AttachmentRegistry.create(
		MiosCoppergolems.id("trim_template"), builder -> builder.persistent(ItemStack.CODEC)
	);

	/** Iron ingots a golem took from a chest to repair an iron golem with. */
	public static final AttachmentType<Integer> CARRIED_IRON = AttachmentRegistry.create(
		MiosCoppergolems.id("carried_iron"), builder -> builder.persistent(Codec.INT)
	);

	/** Set while a golem is off on an errand (repairing an iron golem) that pauses its job. Not saved. */
	public static final AttachmentType<Boolean> ERRAND = AttachmentRegistry.create(MiosCoppergolems.id("errand"), builder -> {
	});

	private ModAttachments() {
	}

	public static void init() {
	}
}
