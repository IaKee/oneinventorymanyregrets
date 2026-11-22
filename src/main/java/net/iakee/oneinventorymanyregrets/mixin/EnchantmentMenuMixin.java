package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveEnchantmentMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin implements HiveEnchantmentMenuAccess {
    @Shadow
    @Final
    private Container enchantSlots;

    @Shadow
    @Final
    private DataSlot enchantmentSeed;

    @Shadow
    public abstract void slotsChanged(Container container);

    @Unique
    private boolean oimr$inClick = false;

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
        at = @At("RETURN")
    )
    private void oimr$syncInitialSeed(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer serverPlayer && HiveMindManager.isAttached(serverPlayer)) {
            int canonicalSeed = HiveMindManager.getEnchantmentSeed(serverPlayer);
            if (canonicalSeed != 0) {
                this.enchantmentSeed.set(canonicalSeed);
            }
        }
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void oimr$guardConcurrentEnchant(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer && HiveMindManager.isAttached(serverPlayer)) {
            if (serverPlayer.isDeadOrDying()) {
                cir.setReturnValue(false);
                return;
            }
            int canonicalSeed = HiveMindManager.getEnchantmentSeed(serverPlayer);
            if (canonicalSeed != 0 && this.enchantmentSeed.get() != canonicalSeed) {
                this.oimr$refreshSeed(canonicalSeed);
                cir.setReturnValue(false);
                return;
            }
            this.oimr$inClick = true;
        }
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void oimr$afterClickMenuButton(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        this.oimr$inClick = false;
    }

    @Override
    public void oimr$refreshSeed(int seed) {
        this.enchantmentSeed.set(seed);
        this.slotsChanged(this.enchantSlots);
        ((EnchantmentMenu) (Object) this).broadcastChanges();
    }

    @Override
    public int oimr$getSeed() {
        return this.enchantmentSeed.get();
    }

    @Override
    public boolean oimr$isInClick() {
        return this.oimr$inClick;
    }
}
