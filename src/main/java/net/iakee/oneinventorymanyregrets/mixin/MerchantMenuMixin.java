package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMerchantMenuAccess;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantMenu.class)
public class MerchantMenuMixin implements HiveMerchantMenuAccess {
    @Shadow
    @Final
    private Merchant trader;

    @Override
    public Merchant oimr$getTrader() {
        return this.trader;
    }
}
