package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerAdvancementsAccess;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin implements HivePlayerAdvancementsAccess {
    @Shadow
    private ServerPlayer player;

    @Shadow
    private AdvancementTree tree;

    @Shadow
    @Final
    private Map<AdvancementHolder, AdvancementProgress> progress;

    @Shadow
    @Final
    private Set<AdvancementHolder> visible;

    @Shadow
    @Final
    private Set<AdvancementHolder> progressChanged;

    @Shadow
    @Final
    private Set<AdvancementNode> rootsToUpdate;

    @Shadow
    private boolean isFirstPacket;

    @Shadow
    public abstract void clearTriggers();

    @Invoker("startProgress")
    protected abstract void oimr$startProgress(AdvancementHolder holder, AdvancementProgress progress);

    @Invoker("markForVisibilityUpdate")
    protected abstract void oimr$markForVisibilityUpdate(AdvancementHolder advancement);

    @Invoker("checkForAutomaticTriggers")
    protected abstract void oimr$checkForAutomaticTriggers(ServerAdvancementManager manager);

    @Invoker("registerListeners")
    protected abstract void oimr$registerListeners(ServerAdvancementManager manager);

    @Shadow
    public abstract void flushDirty(ServerPlayer player, boolean showAdvancements);

    @Inject(method = "award", at = @At("RETURN"))
    private void oimr$onAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            HiveMindManager.onAdvancementAwarded(this.player, holder, criterion);
        }
    }

    @Inject(method = "revoke", at = @At("RETURN"))
    private void oimr$onRevoke(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            HiveMindManager.onAdvancementRevoked(this.player, holder, criterion);
        }
    }

    @WrapWithCondition(
        method = "award",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"
        )
    )
    private boolean oimr$skipDuplicateRewards(AdvancementRewards rewards, ServerPlayer player) {
        return !HiveMindManager.isSyncingAdvancements(this.player);
    }

    @WrapWithCondition(
        method = "award",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
        )
    )
    private boolean oimr$skipDuplicateAnnouncement(Optional<?> display, Consumer<?> action) {
        return !HiveMindManager.isSyncingAdvancements(this.player);
    }

    @Override
    public Map<Identifier, AdvancementProgress> oimr$exportProgress() {
        Map<Identifier, AdvancementProgress> exported = new LinkedHashMap<>();
        this.progress.forEach((holder, advProg) -> {
            if (advProg.hasProgress()) {
                AdvancementProgress copy = new AdvancementProgress();
                copy.update(holder.value().requirements());
                for (String criterion : advProg.getCompletedCriteria()) {
                    copy.grantProgress(criterion);
                }
                exported.put(holder.id(), copy);
            }
        });
        return exported;
    }

    @Override
    public void oimr$syncFromHive(ServerAdvancementManager manager, Map<Identifier, AdvancementProgress> hiveProgress) {
        this.clearTriggers();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.tree = manager.tree();

        hiveProgress.forEach((id, savedProg) -> {
            AdvancementHolder holder = manager.get(id);
            if (holder != null) {
                AdvancementProgress copy = new AdvancementProgress();
                copy.update(holder.value().requirements());
                for (String criterion : savedProg.getCompletedCriteria()) {
                    copy.grantProgress(criterion);
                }
                this.oimr$startProgress(holder, copy);
                this.progressChanged.add(holder);
                this.oimr$markForVisibilityUpdate(holder);
            }
        });

        this.oimr$checkForAutomaticTriggers(manager);
        this.oimr$registerListeners(manager);
        this.flushDirty(this.player, false);
    }
}
