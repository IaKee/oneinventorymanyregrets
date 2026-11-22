package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HiveVillagerAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Predicate;

@Mixin(Villager.class)
public abstract class VillagerMixin implements HiveVillagerAccess {
    @Invoker("updateSpecialPrices")
    protected abstract void oimr$invokeUpdateSpecialPrices(Player player);

    @Override
    public void oimr$updateSpecialPrices(Player player) {
        this.oimr$invokeUpdateSpecialPrices(player);
    }

    @WrapOperation(
        method = "onReputationEventFrom",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/gossip/GossipContainer;add(Ljava/util/UUID;Lnet/minecraft/world/entity/ai/gossip/GossipType;I)V"
        )
    )
    private void oimr$routeReputationEventToHive(
        GossipContainer instance,
        UUID targetUuid,
        GossipType type,
        int amount,
        Operation<Void> original,
        @Local(argsOnly = true) Entity source
    ) {
        UUID resolvedUuid = HiveMindManager.resolveReputationTargetUuid(source, targetUuid);
        original.call(instance, resolvedUuid, type, amount);
    }

    @Inject(method = "onReputationEventFrom", at = @At("TAIL"))
    private void oimr$refreshTradingTeammatePrices(ReputationEventType type, Entity source, CallbackInfo ci) {
        Player tradingPlayer = ((Villager) (Object) this).getTradingPlayer();
        if (tradingPlayer != null
            && !tradingPlayer.getUUID().equals(source.getUUID())
            && HiveMindManager.areSameHive(tradingPlayer, source)) {
            this.oimr$invokeUpdateSpecialPrices(tradingPlayer);
        }
    }

    @WrapOperation(
        method = "getPlayerReputation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/gossip/GossipContainer;getReputation(Ljava/util/UUID;Ljava/util/function/Predicate;)I"
        )
    )
    private int oimr$readHiveReputation(
        GossipContainer instance,
        UUID targetUuid,
        Predicate<GossipType> types,
        Operation<Integer> original,
        @Local(argsOnly = true) Player player
    ) {
        UUID resolvedUuid = HiveMindManager.resolveReputationTargetUuid(player, targetUuid);
        return original.call(instance, resolvedUuid, types);
    }
}
