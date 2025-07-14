package net.iakee.oneinventorymanyregrets.inventory;

import com.llamalad7.mixinextras.sugar.Share;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.iakee.oneinventorymanyregrets.data.OIMRData;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentHolder;
import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Pair;
import net.minecraft.util.Uuids;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.function.Function;

import static net.iakee.oneinventorymanyregrets.data.OIMRData.createNbtWriteView;

public class SharedInventory
{
    // static map to the team's inventory
    public static final Map<UUID, SharedInventory> teamInvs = new HashMap<>();

    // actual shared inventory
    public final IPlayerInventory.SavedInventory shared;

    public final String name;
    public final Set<UUID> members = new HashSet<>();

    // mapping of equipment slot indices to EquipmentSlot enum values
    private static final List<Pair<Integer, EquipmentSlot>> EQUIPMENT_SLOT_MAPPING = List.of(
        new Pair<>(103, EquipmentSlot.HEAD),
        new Pair<>(102, EquipmentSlot.CHEST),
        new Pair<>(101, EquipmentSlot.LEGS),
        new Pair<>(100, EquipmentSlot.FEET),
        new Pair<>(150, EquipmentSlot.OFFHAND));

    public SharedInventory(String name) {
        this.name = name;

        // intializes a shared inventory with the same size as a player's inventory (36)
        this.shared = new IPlayerInventory.SavedInventory(36);
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup rm)
    {
        NbtCompound nbtC = new NbtCompound();
        nbtC.putString("name", this.name);

        // serializes members
        NbtList membersNbt = new NbtList();
        for (UUID memberId : this.members)
        {
            Uuids.CODEC.encodeStart(NbtOps.INSTANCE, memberId)
                .resultOrPartial(System.err::println)
                .ifPresent(membersNbt::add);
        }
        nbtC.put("members", membersNbt);

        NbtCompound mainInvNbt = new NbtCompound();
        WriteView writeView = OIMRData.createNbtWriteView(mainInvNbt);
        Inventories.writeData(writeView, this.shared.main());
        nbtC.put("main_inventory", mainInvNbt);

        // serializes equipment
        NbtCompound equipmentNbt = new NbtCompound();
        WriteView equipmentWriteView = OIMRData.createNbtWriteView(equipmentNbt);
        var nbtOps = rm.getOps(NbtOps.INSTANCE);
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack stack = this.shared.equipment().get(slot);

            // if there is any current equipment for this slot
            if(!stack.isEmpty())
            {
                DataResult<NbtElement> result = ItemStack.CODEC.encodeStart(nbtOps, stack);
                result.resultOrPartial(
                    // TODO: this
                    error -> System.err.println("Error serializing equipment: " + error))
                    .ifPresent(
                       nbtElement -> equipmentNbt.put(slot.getName(), nbtElement));
            }
        }
        nbtC.put("equipment", equipmentNbt);

        return nbtC;
    }
}
