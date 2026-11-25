package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {
    @WrapOperation(
        method = "onHit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"
        )
    )
    private ServerPlayer oimr$teleportHiveOnEnderPearl(
        ServerPlayer player,
        TeleportTransition transition,
        Operation<ServerPlayer> original
    ) {
        ServerPlayer teleported = original.call(player, transition);
        if (teleported != null) {
            HiveMindManager.onEnderPearlTeleport(teleported, transition.newLevel(), transition.position());
        }
        return teleported;
    }
}
