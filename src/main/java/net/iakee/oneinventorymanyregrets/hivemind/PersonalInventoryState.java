package net.iakee.oneinventorymanyregrets.hivemind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player personal storage for hotbar and equipment slots when sync for those slots is disabled.
 */
public final class PersonalInventoryState {
    public static final int HOTBAR_SIZE = 9;

    public static final Codec<PersonalInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStackWithSlot.CODEC.listOf().optionalFieldOf("hotbar", List.of()).forGetter(PersonalInventoryState::encodeHotbar),
        Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC)
            .optionalFieldOf("equipment", Map.of())
            .forGetter(PersonalInventoryState::encodeEquipment),
        Codec.LONG.optionalFieldOf("last_active_epoch", 0L).forGetter(PersonalInventoryState::getLastActiveEpoch),
        Codec.LONG.optionalFieldOf("last_active_game_time", 0L).forGetter(PersonalInventoryState::getLastActiveGameTime)
    ).apply(instance, PersonalInventoryState::fromCodec));

    private final NonNullList<ItemStack> hotbarItems = NonNullList.withSize(HOTBAR_SIZE, ItemStack.EMPTY);
    private final EnumMap<EquipmentSlot, ItemStack> equipmentMap = new EnumMap<>(EquipmentSlot.class);
    private long lastActiveEpoch;
    private long lastActiveGameTime;

    public PersonalInventoryState() {
    }

    private static PersonalInventoryState fromCodec(
        List<ItemStackWithSlot> hotbar,
        Map<EquipmentSlot, ItemStack> equipment,
        long lastActiveEpoch,
        long lastActiveGameTime
    ) {
        PersonalInventoryState state = new PersonalInventoryState();
        for (ItemStackWithSlot entry : hotbar) {
            if (entry.isValidInContainer(HOTBAR_SIZE)) {
                state.hotbarItems.set(entry.slot(), entry.stack().copy());
            }
        }
        equipment.forEach((slot, stack) -> {
            if (slot != EquipmentSlot.MAINHAND && !stack.isEmpty()) {
                state.equipmentMap.put(slot, stack.copy());
            }
        });
        state.lastActiveEpoch = Math.max(0L, lastActiveEpoch);
        state.lastActiveGameTime = Math.max(0L, lastActiveGameTime);
        return state;
    }

    private List<ItemStackWithSlot> encodeHotbar() {
        List<ItemStackWithSlot> list = new ArrayList<>();
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = this.hotbarItems.get(i);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(i, stack.copy()));
            }
        }
        return list;
    }

    private Map<EquipmentSlot, ItemStack> encodeEquipment() {
        EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        this.equipmentMap.forEach((slot, stack) -> {
            if (slot != EquipmentSlot.MAINHAND && !stack.isEmpty()) {
                copy.put(slot, stack.copy());
            }
        });
        return copy;
    }

    public NonNullList<ItemStack> getHotbarItems() {
        return this.hotbarItems;
    }

    public EnumMap<EquipmentSlot, ItemStack> getEquipmentMap() {
        return this.equipmentMap;
    }

    public boolean isEmpty() {
        for (ItemStack stack : this.hotbarItems) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        for (ItemStack stack : this.equipmentMap.values()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public long getLastActiveEpoch() {
        return this.lastActiveEpoch;
    }

    public void setLastActiveEpoch(long lastActiveEpoch) {
        this.lastActiveEpoch = Math.max(0L, lastActiveEpoch);
    }

    public long getLastActiveGameTime() {
        return this.lastActiveGameTime;
    }

    public void setLastActiveGameTime(long lastActiveGameTime) {
        this.lastActiveGameTime = Math.max(0L, lastActiveGameTime);
    }

    public void touchActivity(long epochSeconds, long gameTime) {
        this.lastActiveEpoch = Math.max(0L, epochSeconds);
        this.lastActiveGameTime = Math.max(0L, gameTime);
    }
}
