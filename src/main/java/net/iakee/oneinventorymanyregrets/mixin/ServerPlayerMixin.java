package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @WrapWithCondition(
        method = "doTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;tick(Lnet/minecraft/server/level/ServerPlayer;)V"
        )
    )
    private boolean oimr$tickFoodOncePerHiveMind(FoodData instance, ServerPlayer player) {
        return HiveMindManager.shouldTickFood(player);
    }

    @Inject(method = "giveExperiencePoints", at = @At("TAIL"))
    private void oimr$onGiveExperiencePoints(int points, CallbackInfo ci) {
        HiveMindManager.onExperienceChanged((ServerPlayer) (Object) this);
    }

    @Inject(method = "giveExperienceLevels", at = @At("TAIL"))
    private void oimr$onGiveExperienceLevels(int levels, CallbackInfo ci) {
        HiveMindManager.onExperienceChanged((ServerPlayer) (Object) this);
    }

    @Inject(method = "setExperiencePoints", at = @At("TAIL"))
    private void oimr$onSetExperiencePoints(int points, CallbackInfo ci) {
        HiveMindManager.onExperienceChanged((ServerPlayer) (Object) this);
    }

    @Inject(method = "setExperienceLevels", at = @At("TAIL"))
    private void oimr$onSetExperienceLevels(int levels, CallbackInfo ci) {
        HiveMindManager.onExperienceChanged((ServerPlayer) (Object) this);
    }

    @Inject(method = "onEnchantmentPerformed", at = @At("TAIL"))
    private void oimr$onEnchantmentPerformed(ItemStack stack, int cost, CallbackInfo ci) {
        HiveMindManager.onExperienceChanged((ServerPlayer) (Object) this);
        HiveMindManager.onEnchantmentSeedChanged((ServerPlayer) (Object) this);
    }

    @Inject(method = "setRespawnPosition", at = @At("TAIL"))
    private void oimr$onSetRespawnPosition(ServerPlayer.RespawnConfig respawnConfig, boolean showMessage, CallbackInfo ci) {
        HiveMindManager.onRespawnPositionChanged((ServerPlayer) (Object) this, respawnConfig, showMessage);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void oimr$beforeDie(DamageSource source, CallbackInfo ci) {
        HiveMindManager.pushDyingDamageSource((ServerPlayer) (Object) this, source);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void oimr$afterDie(DamageSource source, CallbackInfo ci) {
        HiveMindManager.popDyingDamageSource((ServerPlayer) (Object) this);
    }

    @WrapWithCondition(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private boolean oimr$shouldBroadcastDeathAll(PlayerList instance, Component message, boolean bypassHiddenChat) {
        return !HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this);
    }

    @WrapWithCondition(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
        )
    )
    private boolean oimr$shouldBroadcastDeathTeam(PlayerList instance, Player player, Component message) {
        return !HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this);
    }

    @WrapWithCondition(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
        )
    )
    private boolean oimr$shouldBroadcastDeathExceptTeam(PlayerList instance, Player player, Component message) {
        return !HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this);
    }
}
