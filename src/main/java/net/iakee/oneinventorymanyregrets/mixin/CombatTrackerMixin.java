package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CombatTracker.class)
public class CombatTrackerMixin {
    @Shadow
    @Final
    private LivingEntity mob;

    @Shadow
    @Final
    private List<CombatEntry> entries;

    @Unique
    private @Nullable Entity oimr$resolveSecondaryCombatant() {
        Entity secondary = this.mob.getKillCredit();
        if (secondary == null) {
            for (int i = this.entries.size() - 1; i >= 0; i--) {
                Entity candidate = this.entries.get(i).source().getEntity();
                if (candidate != null && candidate != this.mob) {
                    return candidate;
                }
            }
        }
        return secondary;
    }

    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void oimr$overrideHiveDeathMessage(CallbackInfoReturnable<Component> cir) {
        Component override = HiveMindManager.consumePendingDeathMessage(this.mob);
        if (override != null) {
            cir.setReturnValue(override);
            return;
        }

        DamageSource killingSource = !this.entries.isEmpty()
            ? this.entries.get(this.entries.size() - 1).source()
            : HiveMindManager.peekDyingDamageSource(this.mob);
        if (HiveMindManager.isAttachedServerPlayer(this.mob) && killingSource != null && killingSource.is(DamageTypes.STARVE)) {
            Entity secondary = this.oimr$resolveSecondaryCombatant();
            Component starveMsg = HiveMindManager.createAndCaptureCollectiveStarveMessage(this.mob, secondary);
            if (starveMsg != null) {
                cir.setReturnValue(starveMsg);
            }
        }
    }

    @Inject(method = "getDeathMessage", at = @At("RETURN"))
    private void oimr$captureVictimDeathMessage(CallbackInfoReturnable<Component> cir) {
        if (cir.isCancelled() || !HiveMindManager.isAttachedServerPlayer(this.mob)) {
            return;
        }
        HiveMindManager.captureOriginalDeathContext(this.mob, cir.getReturnValue(), this.oimr$resolveSecondaryCombatant());
    }
}
