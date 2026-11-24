package net.iakee.oneinventorymanyregrets.hivemind;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.AbstractList;

/**
 * Routes hotbar slots (0..8) between the shared hive inventory and a player's personal hotbar
 * according to OIMRConfig, while keeping main inventory slots (9..35) always shared.
 */
public final class HiveRoutedNonNullList extends NonNullList<ItemStack> {
    private HiveRoutedNonNullList(RoutedDelegate delegate) {
        super(delegate, ItemStack.EMPTY);
    }

    public static HiveRoutedNonNullList create(NonNullList<ItemStack> hiveItems, NonNullList<ItemStack> personalHotbar) {
        return new HiveRoutedNonNullList(new RoutedDelegate(hiveItems, personalHotbar));
    }

    private static final class RoutedDelegate extends AbstractList<ItemStack> {
        private final NonNullList<ItemStack> hiveItems;
        private final NonNullList<ItemStack> personalHotbar;

        private RoutedDelegate(NonNullList<ItemStack> hiveItems, NonNullList<ItemStack> personalHotbar) {
            this.hiveItems = hiveItems;
            this.personalHotbar = personalHotbar;
        }

        private NonNullList<ItemStack> targetFor(int index) {
            if (index >= 0 && index < PersonalInventoryState.HOTBAR_SIZE && !OIMRConfig.isHotbarSlotSynced(index)) {
                return this.personalHotbar;
            }
            return this.hiveItems;
        }

        @Override
        public ItemStack get(int index) {
            return targetFor(index).get(index);
        }

        @Override
        public ItemStack set(int index, ItemStack element) {
            return targetFor(index).set(index, element);
        }

        @Override
        public int size() {
            return this.hiveItems.size();
        }
    }
}
