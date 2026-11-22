package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveEnchantmentMenuAccess;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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

    @Override
    public void oimr$refreshSeed(int seed) {
        this.enchantmentSeed.set(seed);
        this.slotsChanged(this.enchantSlots);
    }
}
