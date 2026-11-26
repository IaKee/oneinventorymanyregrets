package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerScoreboard.class)
public class ServerScoreboardMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "addPlayerToTeam", at = @At("HEAD"))
    private void oimr$beforeAddPlayerToTeam(String player, PlayerTeam team, CallbackInfoReturnable<Boolean> cir) {
        HiveMindManager.beginTeamMutation(this.server);
    }

    @Inject(method = "addPlayerToTeam", at = @At("RETURN"))
    private void oimr$afterAddPlayerToTeam(String player, PlayerTeam team, CallbackInfoReturnable<Boolean> cir) {
        HiveMindManager.endTeamMutation(this.server);
    }

    @Inject(method = "removePlayerFromTeam", at = @At("HEAD"))
    private void oimr$beforeRemovePlayerFromTeam(String player, PlayerTeam team, CallbackInfo ci) {
        HiveMindManager.beginTeamMutation(this.server);
    }

    @Inject(method = "removePlayerFromTeam", at = @At("RETURN"))
    private void oimr$afterRemovePlayerFromTeam(String player, PlayerTeam team, CallbackInfo ci) {
        HiveMindManager.endTeamMutation(this.server);
    }

    @Inject(method = "onTeamAdded", at = @At("RETURN"))
    private void oimr$afterTeamAdded(PlayerTeam team, CallbackInfo ci) {
        HiveMindManager.onTeamAdded(this.server, team);
    }

    @Inject(method = "onTeamRemoved", at = @At("RETURN"))
    private void oimr$afterTeamRemoved(PlayerTeam team, CallbackInfo ci) {
        HiveMindManager.onTeamRemoved(this.server, team);
    }
}
