package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.iakee.oneinventorymanyregrets.network.OIMRCaveSoundPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
@Mixin(BiomeAmbientSoundsHandler.class)
public class BiomeAmbientSoundsHandlerMixin {
    @Shadow
    @Final
    private LocalPlayer player;

    @WrapOperation(
        method = "lambda$tick$1(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/attribute/AmbientMoodSettings;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;forAmbientMood(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/util/RandomSource;DDD)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"
        )
    )
    private SimpleSoundInstance oimr$propagateCaveMoodSound(
        SoundEvent sound,
        RandomSource random,
        double x,
        double y,
        double z,
        Operation<SimpleSoundInstance> original
    ) {
        SimpleSoundInstance instance = original.call(sound, random, x, y, z);
        if (!this.player.isSpectator()
            && !this.player.isDeadOrDying()
            && ClientPlayNetworking.canSend(OIMRCaveSoundPayload.TYPE)) {
            ClientPlayNetworking.send(new OIMRCaveSoundPayload(
                sound.location(),
                x - this.player.getX(),
                y - this.player.getEyeY(),
                z - this.player.getZ(),
                ThreadLocalRandom.current().nextLong()
            ));
        }
        return instance;
    }
}
