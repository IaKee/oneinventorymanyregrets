package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.effect.InfestedMobEffect")
public class InfestedMobEffectMixin {
    @WrapOperation(
        method = "onMobHurt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F"
        )
    )
    private float oimr$scaleInfestedSpawnRoll(
        RandomSource instance,
        Operation<Float> original,
        @Local(argsOnly = true) LivingEntity mob
    ) {
        float roll = original.call(instance);
        double scale = HiveMindManager.getInfestedChanceScale(mob);
        if (scale >= 1.0D) {
            return roll;
        }
        if (scale <= 0.0D) {
            return 1.0F;
        }
        return (float) (roll / scale);
    }
}
