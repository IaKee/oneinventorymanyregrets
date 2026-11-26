package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HiveServerPlayerGameModeAccess;
import net.iakee.oneinventorymanyregrets.hivemind.PersonalInventoryState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Handles shared inventory broadcasting, runtime slot-sync transition drops,
 * authoritative ammo reservation, and outbound held-item packet masking/deferral.
 */
public final class InventoryAndAmmoService {
    private final HiveMindContext ctx;

    public InventoryAndAmmoService(HiveMindContext ctx) {
        this.ctx = ctx;
    }

    public void prepareSlotSyncTransition(
        @Nullable ServerPlayer initiator,
        OIMRConfig.SlotSyncState prevState,
        OIMRConfig.SlotSyncState nextState
    ) {
        if (prevState.equals(nextState)) {
            return;
        }

        MinecraftServer server = this.ctx.server();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!this.ctx.isAttached(player)) {
                continue;
            }
            if (player.isUsingItem()) {
                InteractionHand usedHand = player.getUsedItemHand();
                boolean handSyncChanged;
                if (usedHand == InteractionHand.MAIN_HAND) {
                    int selectedSlot = player.getInventory().getSelectedSlot();
                    handSyncChanged = prevState.isHotbarSlotSynced(selectedSlot) != nextState.isHotbarSlotSynced(selectedSlot);
                } else {
                    handSyncChanged = prevState.isEquipmentSlotSynced(EquipmentSlot.OFFHAND) != nextState.isEquipmentSlotSynced(EquipmentSlot.OFFHAND);
                }
                if (handSyncChanged) {
                    player.stopUsingItem();
                }
            }
            this.ctx.deferredHeldItemSyncs().remove(player.getUUID());
            this.ctx.lastSentInventoryStacks().remove(player.getUUID());
        }

        List<Integer> unsyncingHotbarSlots = new ArrayList<>();
        for (int slot = 0; slot < PersonalInventoryState.HOTBAR_SIZE; slot++) {
            if (prevState.isHotbarSlotSynced(slot) && !nextState.isHotbarSlotSynced(slot)) {
                unsyncingHotbarSlots.add(slot);
            }
        }
        List<EquipmentSlot> unsyncingEquipSlots = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        }) {
            if (prevState.isEquipmentSlotSynced(slot) && !nextState.isEquipmentSlotSynced(slot)) {
                unsyncingEquipSlots.add(slot);
            }
        }

        if (!unsyncingHotbarSlots.isEmpty() || !unsyncingEquipSlots.isEmpty()) {
            HiveMindData data = this.ctx.data();
            for (HiveMind hive : data.getAll()) {
                List<ItemStack> toDrop = new ArrayList<>();
                for (int slot : unsyncingHotbarSlots) {
                    ItemStack stack = hive.getItems().get(slot);
                    if (!stack.isEmpty()) {
                        toDrop.add(stack.copy());
                        hive.getItems().set(slot, ItemStack.EMPTY);
                    }
                }
                for (EquipmentSlot equipSlot : unsyncingEquipSlots) {
                    ItemStack stack = hive.getEquipmentMap().remove(equipSlot);
                    if (stack != null && !stack.isEmpty()) {
                        toDrop.add(stack.copy());
                    }
                }
                if (!toDrop.isEmpty()) {
                    data.setDirty();
                    ServerPlayer dropTarget = resolveSlotSyncDropTarget(hive.getId(), initiator);
                    for (ItemStack stack : toDrop) {
                        if (dropTarget != null) {
                            dropTarget.drop(stack, false, Prediction.SERVER_ONLY);
                        } else {
                            hive.addPendingSyncDrop(stack);
                        }
                    }
                }
            }
        }

        this.ctx.setLastAppliedSlotSync(nextState);
    }

    private @Nullable ServerPlayer resolveSlotSyncDropTarget(String hiveId, @Nullable ServerPlayer initiator) {
        if (initiator != null
            && this.ctx.isAttached(initiator)
            && !initiator.isRemoved()
            && hiveId.equals(this.ctx.getAttachedHiveId(initiator.getUUID()))) {
            return initiator;
        }
        ServerPlayer best = null;
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member.isRemoved()) {
                continue;
            }
            if (best == null || compareDropPriority(member, best) < 0) {
                best = member;
            }
        }
        return best;
    }

    private static int compareDropPriority(ServerPlayer a, ServerPlayer b) {
        boolean aPreferred = !a.isDeadOrDying() && !a.isSpectator();
        boolean bPreferred = !b.isDeadOrDying() && !b.isSpectator();
        if (aPreferred != bPreferred) {
            return aPreferred ? -1 : 1;
        }
        return a.getUUID().compareTo(b.getUUID());
    }

    public void flushPendingSyncDrops(HiveMind hive, HiveMindData data, ServerPlayer player) {
        List<ItemStack> pending = hive.drainPendingSyncDrops();
        if (pending.isEmpty()) {
            return;
        }
        data.setDirty();
        for (ItemStack stack : pending) {
            if (!stack.isEmpty()) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
    }

    public void broadcastInventoryChanges(ServerPlayer source) {
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }
        this.ctx.data().setDirty();
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            member.containerMenu.broadcastChanges();
            ((HiveLivingEntityAccess) member).oimr$detectEquipmentUpdates();
        }
    }

    public void onPlayerStartUsingItem(ServerPlayer player, InteractionHand hand, ItemStack usedItem) {
        if (!this.ctx.isAttached(player)
            || player.isDeadOrDying()
            || player.hasInfiniteMaterials()
            || usedItem.isEmpty()
            || !(usedItem.getItem() instanceof ProjectileWeaponItem)
            || CrossbowItem.isCharged(usedItem)
            || this.ctx.activeAmmoReservations().containsKey(player.getUUID())) {
            return;
        }
        int sourceSlot = findProjectileSourceSlot(player, usedItem);
        if (sourceSlot == Inventory.NOT_FOUND_INDEX) {
            return;
        }
        ItemStack sourceStack = getSlotStack(player, sourceSlot);
        if (sourceStack.isEmpty()) {
            return;
        }
        ItemStack reserved = sourceStack.split(1);
        if (sourceStack.isEmpty()) {
            setSlotStack(player, sourceSlot, ItemStack.EMPTY);
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        this.ctx.activeAmmoReservations().put(
            player.getUUID(),
            new HiveMindContext.AmmoReservation(player.getUUID(), hiveId, hand, usedItem.copy(), reserved, sourceSlot)
        );
        broadcastInventoryChanges(player);
    }

    public void onPlayerStopUsingItem(ServerPlayer player) {
        boolean released = releaseAmmoReservation(player, null);
        if (released) {
            this.ctx.pendingStopUseSyncs().add(player.getUUID());
        }
    }

    public void onAfterPlayerStopUsingItem(ServerPlayer player) {
        if (this.ctx.pendingStopUseSyncs().remove(player.getUUID()) && this.ctx.isAttached(player)) {
            broadcastInventoryChanges(player);
            player.inventoryMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.sendAllDataToRemote();
            }
        }
    }

    public void onCrossbowAmmoLoaded(ServerPlayer player) {
        if (!this.ctx.isAttached(player)) {
            return;
        }
        if (releaseAmmoReservation(player, null)) {
            this.ctx.pendingStopUseSyncs().add(player.getUUID());
            broadcastInventoryChanges(player);
        }
    }

    public ItemStack getReservedProjectile(ServerPlayer player, ItemStack heldWeapon) {
        if (!this.ctx.isAttached(player) || !player.isUsingItem() || heldWeapon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        HiveMindContext.AmmoReservation reservation = this.ctx.activeAmmoReservations().get(player.getUUID());
        if (reservation == null || reservation.reservedStack().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack activeUse = player.getUseItem();
        if (!ItemStack.isSameItem(activeUse, heldWeapon) || !ItemStack.isSameItem(reservation.weaponSnapshot(), heldWeapon)) {
            return ItemStack.EMPTY;
        }
        return reservation.reservedStack();
    }

    public boolean shouldRejectStaleOrEmptyUseItem(ServerPlayer player, ItemStack itemStack, InteractionHand hand) {
        if (!this.ctx.isAttached(player)) {
            return false;
        }
        ItemStack currentHand = player.getItemInHand(hand);
        return itemStack.isEmpty() || currentHand.isEmpty();
    }

    public void onAfterServerPlayerUseItem(ServerPlayer player, InteractionHand hand, InteractionResult result) {
        if (!this.ctx.isAttached(player) || player.isUsingItem()) {
            return;
        }
        if (result.consumesAction() || player.getItemInHand(hand).isEmpty()) {
            broadcastInventoryChanges(player);
        }
    }

    public boolean releaseAmmoReservation(ServerPlayer player, @Nullable ServerPlayer dropOverride) {
        HiveMindContext.AmmoReservation reservation = this.ctx.activeAmmoReservations().remove(player.getUUID());
        if (reservation == null) {
            return false;
        }
        ItemStack leftover = reservation.reservedStack();
        if (!leftover.isEmpty()) {
            returnReservedAmmoToPlayer(player, leftover.copyAndClear(), reservation.sourceSlot(), dropOverride);
        }
        return true;
    }

    private static void returnReservedAmmoToPlayer(
        ServerPlayer player,
        ItemStack toReturn,
        int preferredSlot,
        @Nullable ServerPlayer dropOverride
    ) {
        if (toReturn.isEmpty()) {
            return;
        }
        if (preferredSlot == Inventory.SLOT_OFFHAND) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty()) {
                player.setItemSlot(EquipmentSlot.OFFHAND, toReturn.copyAndClear());
                return;
            }
            if (ItemStack.isSameItemSameComponents(offhand, toReturn) && offhand.getCount() < offhand.getMaxStackSize()) {
                int move = Math.min(offhand.getMaxStackSize() - offhand.getCount(), toReturn.getCount());
                offhand.grow(move);
                toReturn.shrink(move);
            }
        }
        if (!toReturn.isEmpty() && preferredSlot >= 0 && preferredSlot < Inventory.INVENTORY_SIZE) {
            ItemStack current = player.getInventory().getItem(preferredSlot);
            if (current.isEmpty()
                || (ItemStack.isSameItemSameComponents(current, toReturn)
                    && current.getCount() < current.getMaxStackSize())) {
                player.getInventory().add(preferredSlot, toReturn);
            }
        }
        if (!toReturn.isEmpty()) {
            player.getInventory().add(toReturn);
        }
        if (!toReturn.isEmpty()) {
            ServerPlayer dropTarget = dropOverride != null && !dropOverride.isRemoved() ? dropOverride : player;
            dropTarget.drop(toReturn.copyAndClear(), false, Prediction.SERVER_ONLY);
        }
    }

    private static int findProjectileSourceSlot(ServerPlayer player, ItemStack heldWeapon) {
        if (!(heldWeapon.getItem() instanceof ProjectileWeaponItem weaponItem)) {
            return Inventory.NOT_FOUND_INDEX;
        }
        Predicate<ItemStack> supportedHeld = weaponItem.getSupportedHeldProjectiles();
        if (supportedHeld.test(player.getOffhandItem())) {
            return Inventory.SLOT_OFFHAND;
        }
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (supportedHeld.test(player.getMainHandItem())) {
            return selectedSlot;
        }
        Predicate<ItemStack> allSupported = weaponItem.getAllSupportedProjectiles();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (allSupported.test(inventory.getItem(i))) {
                return i;
            }
        }
        return Inventory.NOT_FOUND_INDEX;
    }

    private static ItemStack getSlotStack(ServerPlayer player, int slot) {
        if (slot == Inventory.SLOT_OFFHAND) {
            return player.getOffhandItem();
        }
        return player.getInventory().getItem(slot);
    }

    private static void setSlotStack(ServerPlayer player, int slot, ItemStack stack) {
        if (slot == Inventory.SLOT_OFFHAND) {
            player.setItemSlot(EquipmentSlot.OFFHAND, stack);
        } else {
            player.getInventory().setItem(slot, stack);
        }
    }

    private ItemStack maskReservedAmmoOutboundStack(ServerPlayer player, int inventorySlot, ItemStack outboundStack) {
        HiveMindContext.AmmoReservation reservation = this.ctx.activeAmmoReservations().get(player.getUUID());
        if (reservation == null
            || reservation.reservedStack().isEmpty()
            || reservation.sourceSlot() != inventorySlot) {
            return outboundStack;
        }
        ItemStack reserved = reservation.reservedStack();
        if (outboundStack.isEmpty()) {
            return reserved.copy();
        }
        if (ItemStack.isSameItemSameComponents(outboundStack, reserved)) {
            int combined = Math.min(outboundStack.getMaxStackSize(), outboundStack.getCount() + reserved.getCount());
            return outboundStack.copyWithCount(combined);
        }
        return outboundStack;
    }

    public @Nullable Packet<?> filterOrDeferSharedHeldItemUpdate(ServerPlayer player, Packet<?> packet) {
        if (!this.ctx.isAttached(player) || player.isDeadOrDying()) {
            return packet;
        }

        if (packet instanceof ClientboundSetPlayerInventoryPacket inventoryPacket) {
            int inventorySlot = inventoryPacket.slot();
            ItemStack outboundStack = inventoryPacket.contents();
            if (shouldMaskOutboundSlot(player, inventorySlot, outboundStack)) {
                this.ctx.deferredHeldItemSyncs().add(player.getUUID());
                return null;
            }
            ItemStack adjustedStack = maskReservedAmmoOutboundStack(player, inventorySlot, outboundStack);
            recordLastSentSlotStack(player, inventorySlot, adjustedStack);
            if (adjustedStack != outboundStack) {
                return new ClientboundSetPlayerInventoryPacket(inventorySlot, adjustedStack);
            }
            return packet;
        }

        if (packet instanceof ClientboundContainerSetSlotPacket containerPacket) {
            int inventorySlot = resolvePlayerInventorySlot(player, containerPacket);
            if (inventorySlot == Inventory.NOT_FOUND_INDEX) {
                return packet;
            }
            ItemStack outboundStack = containerPacket.getItem();
            if (shouldMaskOutboundSlot(player, inventorySlot, outboundStack)) {
                this.ctx.deferredHeldItemSyncs().add(player.getUUID());
                return null;
            }
            ItemStack adjustedStack = maskReservedAmmoOutboundStack(player, inventorySlot, outboundStack);
            recordLastSentSlotStack(player, inventorySlot, adjustedStack);
            if (adjustedStack != outboundStack) {
                return new ClientboundContainerSetSlotPacket(
                    containerPacket.getContainerId(),
                    containerPacket.getStateId(),
                    containerPacket.getSlot(),
                    adjustedStack
                );
            }
            return packet;
        }

        if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            AbstractContainerMenu menu = resolveMenuForContainerId(player, contentPacket.containerId());
            if (menu == null) {
                return packet;
            }
            List<ItemStack> items = contentPacket.items();
            int limit = Math.min(items.size(), menu.slots.size());
            List<ItemStack> maskedItems = null;
            for (int i = 0; i < limit; i++) {
                Slot menuSlot = menu.getSlot(i);
                if (menuSlot.container != player.getInventory()) {
                    continue;
                }
                int inventorySlot = menuSlot.getContainerSlot();
                ItemStack outboundStack = items.get(i);
                if (shouldMaskOutboundSlot(player, inventorySlot, outboundStack)) {
                    if (maskedItems == null) {
                        maskedItems = new ArrayList<>(items);
                    }
                    ItemStack authoritativeStack = player.getInventory().getItem(inventorySlot);
                    maskedItems.set(i, getLastSentSlotStack(player, inventorySlot, authoritativeStack).copy());
                    this.ctx.deferredHeldItemSyncs().add(player.getUUID());
                } else {
                    ItemStack adjustedStack = maskReservedAmmoOutboundStack(player, inventorySlot, outboundStack);
                    if (adjustedStack != outboundStack) {
                        if (maskedItems == null) {
                            maskedItems = new ArrayList<>(items);
                        }
                        maskedItems.set(i, adjustedStack);
                    }
                    recordLastSentSlotStack(player, inventorySlot, adjustedStack);
                }
            }
            if (maskedItems != null) {
                return new ClientboundContainerSetContentPacket(
                    contentPacket.containerId(),
                    contentPacket.stateId(),
                    maskedItems,
                    contentPacket.carriedItem()
                );
            }
        }

        return packet;
    }

    private boolean shouldMaskOutboundSlot(ServerPlayer player, int inventorySlot, ItemStack outboundStack) {
        if (!isProtectedSharedHandSlot(player, inventorySlot)) {
            return false;
        }
        ItemStack authoritativeStack = player.getInventory().getItem(inventorySlot);
        ItemStack lastClientStack = getLastSentSlotStack(player, inventorySlot, authoritativeStack);
        if (!isAuthoritativelyUsable(authoritativeStack)
            || !isAuthoritativelyUsable(outboundStack)
            || !ItemStack.isSameItem(lastClientStack, authoritativeStack)) {
            this.ctx.deferredHeldItemSyncs().remove(player.getUUID());
            return false;
        }
        return true;
    }

    private static @Nullable AbstractContainerMenu resolveMenuForContainerId(ServerPlayer player, int containerId) {
        if (containerId == player.containerMenu.containerId) {
            return player.containerMenu;
        }
        if (containerId == player.inventoryMenu.containerId) {
            return player.inventoryMenu;
        }
        return null;
    }

    private void recordLastSentSlotStack(ServerPlayer player, int inventorySlot, ItemStack stack) {
        if (inventorySlot < 0) {
            return;
        }
        this.ctx.lastSentInventoryStacks()
            .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
            .put(inventorySlot, stack.copy());
    }

    private ItemStack getLastSentSlotStack(ServerPlayer player, int inventorySlot, ItemStack fallback) {
        Map<Integer, ItemStack> slots = this.ctx.lastSentInventoryStacks().get(player.getUUID());
        if (slots == null) {
            return fallback;
        }
        ItemStack recorded = slots.get(inventorySlot);
        return recorded != null ? recorded : fallback;
    }

    private static int resolvePlayerInventorySlot(
        ServerPlayer player,
        ClientboundContainerSetSlotPacket packet
    ) {
        AbstractContainerMenu menu = resolveMenuForContainerId(player, packet.getContainerId());
        if (menu == null || !menu.isValidSlotIndex(packet.getSlot())) {
            return Inventory.NOT_FOUND_INDEX;
        }
        Slot menuSlot = menu.getSlot(packet.getSlot());
        return menuSlot.container == player.getInventory()
            ? menuSlot.getContainerSlot()
            : Inventory.NOT_FOUND_INDEX;
    }

    private static boolean isProtectedSharedHandSlot(ServerPlayer player, int inventorySlot) {
        boolean mining = ((HiveServerPlayerGameModeAccess) player.gameMode).oimr$isDestroyingOrDelayedBlock();
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (mining && inventorySlot == selectedSlot && OIMRConfig.isHotbarSlotSynced(inventorySlot)) {
            return true;
        }
        if (!player.isUsingItem()) {
            return false;
        }
        if (player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return inventorySlot == selectedSlot && OIMRConfig.isHotbarSlotSynced(inventorySlot);
        }
        return inventorySlot == Inventory.SLOT_OFFHAND
            && OIMRConfig.isEquipmentSlotSynced(EquipmentSlot.OFFHAND);
    }

    private static boolean isAuthoritativelyUsable(ItemStack stack) {
        return !stack.isEmpty() && !stack.isBroken();
    }

    public void flushDeferredHeldItemSyncs() {
        if (this.ctx.deferredHeldItemSyncs().isEmpty()) {
            return;
        }
        MinecraftServer server = this.ctx.server();
        Iterator<UUID> iterator = this.ctx.deferredHeldItemSyncs().iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !this.ctx.isAttached(player)) {
                iterator.remove();
                continue;
            }
            if (player.isUsingItem()
                || ((HiveServerPlayerGameModeAccess) player.gameMode).oimr$isDestroyingOrDelayedBlock()) {
                continue;
            }

            iterator.remove();
            player.containerMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) {
                player.inventoryMenu.sendAllDataToRemote();
            }
        }
    }
}
