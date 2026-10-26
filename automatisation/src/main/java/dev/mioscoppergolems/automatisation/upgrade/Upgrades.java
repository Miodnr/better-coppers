package dev.mioscoppergolems.automatisation.upgrade;

import dev.mioscoppergolems.api.GolemModifier;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.automatisation.Automatisation;
import dev.mioscoppergolems.automatisation.registry.AutoAttachments;
import dev.mioscoppergolems.automatisation.registry.AutoItems;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Fitting golems (and their carts) with what the factory makes. Right-click a copper golem with:
 * <ul>
 * <li><b>Iron Plating</b>: takes three quarters of every hit, {@link #PLATING} damage in all, then it is used up.</li>
 * <li><b>Copper Coating</b>: no rusting from work for {@link #COATING} energy, about three days of steady work.</li>
 * <li><b>Gold Gearwork</b>: walks and works {@link #GEARWORK_SPEED}× as fast for {@link #GEARWORK} energy.</li>
 * <li><b>Copper Satchel</b>: carries twice as much per trip (sorters and couriers), for good.</li>
 * </ul>
 * Each can be topped up to twice its amount. A Cart Extension on a copper chest minecart makes it a double chest.
 */
public final class Upgrades {
	public static final int PLATING = 40;
	public static final int COATING = 3000;
	public static final int GEARWORK = 2000;
	public static final float GEARWORK_SPEED = 1.3F;
	/** How much of a hit the plating takes. */
	public static final float PLATING_SHARE = 0.75F;

	public static final GolemModifier MODIFIER = new GolemModifier() {
		@Override
		public float speed(CopperGolem golem) {
			return get(golem).gearwork() > 0 ? GEARWORK_SPEED : 1.0F;
		}

		@Override
		public boolean preventsRust(CopperGolem golem) {
			return get(golem).coating() > 0;
		}

		@Override
		public int carryLimit(CopperGolem golem, int base) {
			return get(golem).satchel() ? base * 2 : base;
		}

		@Override
		public void onSpend(CopperGolem golem, int energy) {
			GolemUpgrades upgrades = get(golem);
			if (upgrades.coating() > 0 || upgrades.gearwork() > 0) {
				set(golem, upgrades.withCoating(upgrades.coating() - energy).withGearwork(upgrades.gearwork() - energy));
			}
		}

		@Override
		public void dropItems(ServerLevel level, CopperGolem golem) {
			Upgrades.dropItems(level, golem);
		}

		@Override
		public @Nullable Component status(CopperGolem golem) {
			GolemUpgrades upgrades = get(golem);
			List<Component> parts = new ArrayList<>();
			if (upgrades.plating() > 0) {
				parts.add(Component.translatable("status.mioscoppergolems_automatisation.plating", upgrades.plating() * 100 / PLATING));
			}
			if (upgrades.coating() > 0) {
				parts.add(Component.translatable("status.mioscoppergolems_automatisation.coating", upgrades.coating() * 100 / COATING));
			}
			if (upgrades.gearwork() > 0) {
				parts.add(Component.translatable("status.mioscoppergolems_automatisation.gearwork", upgrades.gearwork() * 100 / GEARWORK));
			}
			if (upgrades.satchel()) {
				parts.add(Component.translatable("status.mioscoppergolems_automatisation.satchel"));
			}
			if (parts.isEmpty()) {
				return null;
			}
			MutableComponent joined = Component.empty();
			for (int i = 0; i < parts.size(); i++) {
				joined.append(i == 0 ? Component.empty() : Component.literal(", ")).append(parts.get(i));
			}
			return joined;
		}
	};

	private Upgrades() {
	}

	public static GolemUpgrades get(CopperGolem golem) {
		GolemUpgrades upgrades = golem.getAttached(AutoAttachments.UPGRADES);
		return upgrades == null ? GolemUpgrades.NONE : upgrades;
	}

	public static void set(CopperGolem golem, GolemUpgrades upgrades) {
		if (upgrades.isEmpty()) {
			golem.removeAttached(AutoAttachments.UPGRADES);
		} else {
			golem.setAttached(AutoAttachments.UPGRADES, upgrades);
		}
	}

	/** Takes part of a hit on the plating. Returns the damage that gets through. */
	public static float absorb(CopperGolem golem, float damage) {
		GolemUpgrades upgrades = get(golem);
		if (upgrades.plating() <= 0 || damage <= 0.0F) {
			return damage;
		}
		float taken = Math.min(damage * PLATING_SHARE, upgrades.plating());
		int left = upgrades.plating() - (int) Math.ceil(taken);
		set(golem, upgrades.withPlating(left));
		golem.playSound(left > 0 ? SoundEvents.SHIELD_BLOCK.value() : SoundEvents.ITEM_BREAK.value(), 0.6F, 1.3F);
		return damage - taken;
	}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (entity instanceof CopperChestMinecart cart && held.is(AutoItems.CART_EXTENSION)) {
			if (cart.isExtended()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				cart.extend(held);
				held.consume(1, player);
				cart.playSound(SoundEvents.CHEST_LOCKED, 0.8F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.mioscoppergolems_automatisation.cart_extended"));
				MiosCoppergolemsApi.awardGuide(player, Automatisation.id("guide/cart_extension"));
			}
			return InteractionResult.SUCCESS;
		}
		if (!(entity instanceof CopperGolem golem)) {
			return InteractionResult.PASS;
		}
		GolemUpgrades upgrades = get(golem);
		GolemUpgrades fitted;
		SoundEvent sound;
		if (held.is(AutoItems.IRON_PLATING) && upgrades.plating() < PLATING) {
			fitted = upgrades.withPlating(upgrades.plating() + PLATING);
			sound = SoundEvents.ARMOR_EQUIP_IRON.value();
		} else if (held.is(AutoItems.COPPER_COATING) && upgrades.coating() < COATING) {
			fitted = upgrades.withCoating(upgrades.coating() + COATING);
			sound = SoundEvents.HONEYCOMB_WAX_ON;
		} else if (held.is(AutoItems.GOLD_GEARWORK) && upgrades.gearwork() < GEARWORK) {
			fitted = upgrades.withGearwork(upgrades.gearwork() + GEARWORK);
			sound = SoundEvents.CROSSBOW_LOADING_END.value();
		} else if (held.is(AutoItems.COPPER_SATCHEL) && !upgrades.satchel()) {
			fitted = upgrades.withSatchel(true);
			sound = SoundEvents.ARMOR_EQUIP_LEATHER.value();
		} else {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			set(golem, fitted);
			held.consume(1, player);
			MiosCoppergolemsApi.awardGuide(player, Automatisation.id("guide/upgrade"));
			if (fitted.plating() > 0 && fitted.coating() > 0 && fitted.gearwork() > 0 && fitted.satchel()) {
				MiosCoppergolemsApi.awardGuide(player, Automatisation.id("guide/fully_loaded"));
			}
			golem.playSound(sound, 1.0F, 1.0F);
			serverLevel.sendParticles(ParticleTypes.WAX_ON, golem.getX(), golem.getY() + 0.6, golem.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
		}
		return InteractionResult.SUCCESS;
	}

	/** The satchel is the only upgrade that comes back off: it drops when the golem dies or turns into a statue. */
	public static void dropItems(ServerLevel level, CopperGolem golem) {
		if (get(golem).satchel()) {
			golem.spawnAtLocation(level, new ItemStack(AutoItems.COPPER_SATCHEL));
		}
		golem.removeAttached(AutoAttachments.UPGRADES);
	}
}
