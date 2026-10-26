package dev.mioscoppergolems.automatisation.mixin;

import dev.mioscoppergolems.automatisation.upgrade.Upgrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Iron plating takes part of every hit a copper golem gets. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float mioscoppergolems_automatisation$plating(float damage, ServerLevel level, DamageSource source) {
		return (Object) this instanceof CopperGolem golem && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) ? Upgrades.absorb(golem, damage) : damage;
	}
}
