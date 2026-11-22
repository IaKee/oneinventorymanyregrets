package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mob.class)
public class MobMixin {
    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private @Nullable LivingEntity oimr$preventFriendlyPetTarget(@Nullable LivingEntity target) {
        if (target != null && HiveMindManager.shouldPreventPetAttack((Mob) (Object) this, target)) {
            return null;
        }
        return target;
    }
}
