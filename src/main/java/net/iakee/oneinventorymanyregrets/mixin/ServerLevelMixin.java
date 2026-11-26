package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(
        method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
        at = @At("TAIL")
    )
    private void oimr$replicatePositionalMobSound(
        @Nullable Entity except,
        double x,
        double y,
        double z,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed,
        CallbackInfo ci
    ) {
        Mob activeMob = HiveMindManager.peekActiveMobSoundSource((ServerLevel) (Object) this);
        if (activeMob != null && (source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL)) {
            HiveMindManager.onMobSoundEmitted(
                (ServerLevel) (Object) this,
                except,
                activeMob.position(),
                sound,
                source,
                volume,
                pitch,
                seed
            );
        } else if (activeMob == null && source == SoundSource.HOSTILE) {
            HiveMindManager.onMobSoundEmitted(
                (ServerLevel) (Object) this,
                except,
                new Vec3(x, y, z),
                sound,
                source,
                volume,
                pitch,
                seed
            );
        }
    }

    @Inject(
        method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
        at = @At("TAIL")
    )
    private void oimr$replicateEntityMobSound(
        @Nullable Entity except,
        Entity sourceEntity,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed,
        CallbackInfo ci
    ) {
        if (sourceEntity instanceof Mob && (source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL)) {
            HiveMindManager.onMobSoundEmitted(
                (ServerLevel) (Object) this,
                except,
                sourceEntity.position(),
                sound,
                source,
                volume,
                pitch,
                seed
            );
        }
    }
}
