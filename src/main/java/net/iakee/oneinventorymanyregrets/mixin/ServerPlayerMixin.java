package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.datafixers.util.Either;
import io.netty.channel.ChannelFutureListener;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.OIMRLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
    private void oimr$guardPeacefulRegen(
        CallbackInfo ci,
        @Share("oimrRestoreSuppressCascade") LocalBooleanRef restoreSuppress
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!HiveMindManager.shouldRunPeacefulRegen(self)) {
            restoreSuppress.set(false);
            ci.cancel();
            return;
        }
        restoreSuppress.set(HiveMindManager.beginPeacefulRegenTick(self));
    }

    @WrapWithCondition(
        method = "tickRegeneration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;setSaturation(F)V"
        )
    )
    private boolean oimr$guardPeacefulSaturationRegen(FoodData instance, float saturation) {
        return HiveMindManager.shouldApplyPeacefulFoodRegen((ServerPlayer) (Object) this);
    }

    @WrapWithCondition(
        method = "tickRegeneration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"
        )
    )
    private boolean oimr$guardPeacefulFoodRegen(FoodData instance, int foodLevel) {
        return HiveMindManager.shouldApplyPeacefulFoodRegen((ServerPlayer) (Object) this);
    }

    @Inject(method = "tickRegeneration", at = @At("RETURN"))
    private void oimr$endPeacefulRegen(
        CallbackInfo ci,
        @Share("oimrRestoreSuppressCascade") LocalBooleanRef restoreSuppress
    ) {
        HiveMindManager.endPeacefulRegenTick((ServerPlayer) (Object) this, restoreSuppress.get());
    }
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

    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true)
    private int oimr$scaleExperiencePointsGain(int points) {
        if (points > 0) {
            return HiveMindManager.scaleExperiencePoints((ServerPlayer) (Object) this, points);
        }
        return points;
    }

    @ModifyVariable(method = "giveExperienceLevels", at = @At("HEAD"), argsOnly = true)
    private int oimr$scaleExperienceLevelsGain(int levels) {
        if (levels > 0) {
            return HiveMindManager.scaleExperienceLevels((ServerPlayer) (Object) this, levels);
        }
        return levels;
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
        HiveMindManager.onEnchantmentPerformed((ServerPlayer) (Object) this);
    }

    @Inject(method = "setRespawnPosition", at = @At("TAIL"))
    private void oimr$onSetRespawnPosition(ServerPlayer.RespawnConfig respawnConfig, boolean showMessage, CallbackInfo ci) {
        HiveMindManager.onRespawnPositionChanged((ServerPlayer) (Object) this, respawnConfig, showMessage);
    }

    @Inject(method = "startSleepInBed", at = @At("RETURN"))
    private void oimr$onStartSleepInBed(
        AbstractBedBlock bedBlock,
        BlockState bedBlockState,
        BedRule rule,
        BlockPos pos,
        CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir
    ) {
        if (cir.getReturnValue().right().isPresent()) {
            HiveMindManager.onPlayerStartedSleeping((ServerPlayer) (Object) this);
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void oimr$captureSleeperBeforeStop(
        boolean forcefulWakeUp,
        boolean updateLevelList,
        CallbackInfo ci,
        @Share("oimrWasSleeping") LocalBooleanRef wasSleeping
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        wasSleeping.set(self.isSleeping());
    }

    @Inject(method = "stopSleepInBed", at = @At("RETURN"))
    private void oimr$onStopSleepInBed(
        boolean forcefulWakeUp,
        boolean updateLevelList,
        CallbackInfo ci,
        @Share("oimrWasSleeping") LocalBooleanRef wasSleeping
    ) {
        if (wasSleeping.get()) {
            HiveMindManager.onPlayerStoppedSleeping((ServerPlayer) (Object) this, forcefulWakeUp, updateLevelList);
        }
    }

    @Inject(method = "showEndCredits", at = @At("TAIL"))
    private void oimr$onShowEndCredits(CallbackInfo ci) {
        HiveMindManager.onEndCreditsPortalTriggered((ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void oimr$beforeDie(DamageSource source, CallbackInfo ci) {
        HiveMindManager.onBeforePlayerDie((ServerPlayer) (Object) this, source);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void oimr$afterDie(DamageSource source, CallbackInfo ci) {
        HiveMindManager.onAfterPlayerDieReturn((ServerPlayer) (Object) this);
    }

    @WrapOperation(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"
        )
    )
    private void oimr$localizeCombatKillPacket(
        ServerGamePacketListenerImpl instance,
        Packet<?> packet,
        @Nullable ChannelFutureListener listener,
        Operation<Void> original
    ) {
        if (packet instanceof ClientboundPlayerCombatKillPacket killPacket && OIMRLang.containsOimrKey(killPacket.message())) {
            Component localized = OIMRLang.localizeForPlayer(killPacket.message(), (ServerPlayer) (Object) this);
            packet = new ClientboundPlayerCombatKillPacket(killPacket.playerId(), localized);
        }
        original.call(instance, packet, listener);
    }

    @WrapOperation(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void oimr$broadcastDeathAll(
        PlayerList instance,
        Component message,
        boolean bypassHiddenChat,
        Operation<Void> original
    ) {
        if (HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this)) {
            return;
        }
        if (OIMRLang.containsOimrKey(message)) {
            instance.broadcastSystemMessage(message, recipient -> OIMRLang.localizeForPlayer(message, recipient), bypassHiddenChat);
            return;
        }
        original.call(instance, message, bypassHiddenChat);
    }

    @WrapOperation(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
        )
    )
    private void oimr$broadcastDeathTeam(
        PlayerList instance,
        Player player,
        Component message,
        Operation<Void> original
    ) {
        if (HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this)) {
            return;
        }
        if (OIMRLang.containsOimrKey(message)) {
            PlayerTeam team = player.getTeam();
            if (team != null) {
                for (String name : team.getPlayers()) {
                    ServerPlayer recipient = instance.getPlayerByName(name);
                    if (recipient != null && recipient != player) {
                        recipient.sendSystemMessage(OIMRLang.localizeForPlayer(message, recipient));
                    }
                }
            }
            return;
        }
        original.call(instance, player, message);
    }

    @WrapOperation(
        method = "die",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
        )
    )
    private void oimr$broadcastDeathExceptTeam(
        PlayerList instance,
        Player player,
        Component message,
        Operation<Void> original
    ) {
        if (HiveMindManager.consumeSuppressDeathChat((ServerPlayer) (Object) this)) {
            return;
        }
        if (OIMRLang.containsOimrKey(message)) {
            PlayerTeam team = player.getTeam();
            if (team == null) {
                instance.broadcastSystemMessage(message, recipient -> OIMRLang.localizeForPlayer(message, recipient), false);
            } else {
                for (ServerPlayer recipient : instance.getPlayers()) {
                    if (recipient.getTeam() != team) {
                        recipient.sendSystemMessage(OIMRLang.localizeForPlayer(message, recipient));
                    }
                }
            }
            return;
        }
        original.call(instance, player, message);
    }
}
