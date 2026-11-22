package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Map<Player, Vec3> hitPlayers;

    @Inject(method = "hurtEntities", at = @At("TAIL"))
    private void oimr$onHurtEntities(CallbackInfo ci) {
        HiveMindManager.onExplosionKnockback(this.level, this.hitPlayers);
    }
}
