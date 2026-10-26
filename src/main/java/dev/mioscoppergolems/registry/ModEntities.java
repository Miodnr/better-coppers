package dev.mioscoppergolems.registry;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.entity.GolemBobber;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	// Same dimensions as the vanilla chest minecart.
	public static final EntityType<CopperChestMinecart> COPPER_CHEST_MINECART = register(
		"copper_chest_minecart",
		EntityType.Builder.of(CopperChestMinecart::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8)
	);

	public static final EntityType<GolemBobber> GOLEM_BOBBER = register(
		"golem_bobber", EntityType.Builder.<GolemBobber>of(GolemBobber::new, MobCategory.MISC).noLootTable().noSave().noSummon().sized(0.25F, 0.25F).clientTrackingRange(4)
	);

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MiosCoppergolems.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}
