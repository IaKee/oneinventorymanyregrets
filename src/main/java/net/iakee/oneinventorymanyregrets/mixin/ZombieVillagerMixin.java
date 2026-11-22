package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(ZombieVillager.class)
public class ZombieVillagerMixin {
    @Shadow
    private @Nullable UUID conversionStarter;

    @WrapOperation(
        method = "finishConversion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"
        )
    )
    private <T extends Mob> @Nullable T oimr$applyOfflineHiveCureReputation(
        ZombieVillager instance,
        EntityType<T> entityType,
        ConversionParams params,
        ConversionParams.AfterConversion<T> afterConversion,
        Operation<T> original,
        @Local(argsOnly = true) ServerLevel level
    ) {
        T converted = original.call(instance, entityType, params, afterConversion);
        if (converted instanceof Villager villager
            && this.conversionStarter != null
            && level.getPlayerByUUID(this.conversionStarter) == null) {
            HiveMindManager.applyOfflineZombieVillagerCure(level.getServer(), this.conversionStarter, villager);
        }
        return converted;
    }
}
