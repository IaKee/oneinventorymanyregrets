package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEquipmentAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Objects;

@Mixin(EntityEquipment.class)
public class EntityEquipmentMixin implements HiveEquipmentAccess {
    @Mutable
    @Shadow
    @Final
    private EnumMap<EquipmentSlot, ItemStack> items;

    @Unique
    private @Nullable EnumMap<EquipmentSlot, ItemStack> oimr$hiveItems;

    @Unique
    private @Nullable EnumMap<EquipmentSlot, ItemStack> oimr$personalItems;

    @Override
    public EnumMap<EquipmentSlot, ItemStack> oimr$getEquipmentItems() {
        return this.items;
    }

    @Override
    public void oimr$setEquipmentItems(EnumMap<EquipmentSlot, ItemStack> items) {
        this.items = items;
    }

    @Override
    public void oimr$setRoutedMaps(
        @Nullable EnumMap<EquipmentSlot, ItemStack> hiveItems,
        @Nullable EnumMap<EquipmentSlot, ItemStack> personalItems
    ) {
        this.oimr$hiveItems = hiveItems;
        this.oimr$personalItems = personalItems;
    }

    @Unique
    private boolean oimr$isRouted() {
        return this.oimr$hiveItems != null && this.oimr$personalItems != null;
    }

    @Unique
    private boolean oimr$isPlayerEquipment() {
        return (Object) this instanceof PlayerEquipment;
    }

    @Unique
    private boolean oimr$isSkippedSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && oimr$isPlayerEquipment();
    }

    @Unique
    private EnumMap<EquipmentSlot, ItemStack> oimr$mapFor(EquipmentSlot slot) {
        return OIMRConfig.isEquipmentSlotSynced(slot) ? this.oimr$hiveItems : this.oimr$personalItems;
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void oimr$routedGet(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (oimr$isRouted() && !oimr$isSkippedSlot(slot)) {
            cir.setReturnValue(oimr$mapFor(slot).getOrDefault(slot, ItemStack.EMPTY));
        }
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void oimr$routedSet(EquipmentSlot slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (oimr$isRouted() && !oimr$isSkippedSlot(slot)) {
            ItemStack prev = oimr$mapFor(slot).put(slot, stack);
            cir.setReturnValue(Objects.requireNonNullElse(prev, ItemStack.EMPTY));
        }
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void oimr$routedIsEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (oimr$isRouted()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (oimr$isSkippedSlot(slot)) {
                    continue;
                }
                if (!oimr$mapFor(slot).getOrDefault(slot, ItemStack.EMPTY).isEmpty()) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void oimr$routedTick(Entity entity, CallbackInfo ci) {
        if (oimr$isRouted()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (oimr$isSkippedSlot(slot)) {
                    continue;
                }
                ItemStack stack = oimr$mapFor(slot).getOrDefault(slot, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    stack.inventoryTick(entity.level(), entity, slot);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "setAll", at = @At("HEAD"), cancellable = true)
    private void oimr$routedSetAll(EntityEquipment other, CallbackInfo ci) {
        if (oimr$isRouted()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (oimr$isSkippedSlot(slot)) {
                    continue;
                }
                ItemStack stack = other.get(slot);
                oimr$mapFor(slot).put(slot, stack);
            }
            ci.cancel();
        }
    }

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void oimr$routedDropAll(LivingEntity entity, CallbackInfo ci) {
        if (oimr$isRouted()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (oimr$isSkippedSlot(slot)) {
                    continue;
                }
                EnumMap<EquipmentSlot, ItemStack> map = oimr$mapFor(slot);
                ItemStack stack = map.getOrDefault(slot, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    ItemEntity drop = entity.createItemStackToDrop(stack, true, false);
                    if (drop != null) {
                        entity.level().addFreshEntity(drop);
                    }
                    map.put(slot, ItemStack.EMPTY);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void oimr$routedClear(CallbackInfo ci) {
        if (oimr$isRouted()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (oimr$isSkippedSlot(slot)) {
                    continue;
                }
                oimr$mapFor(slot).put(slot, ItemStack.EMPTY);
            }
            ci.cancel();
        }
    }
}
