package net.iakee.oneinventorymanyregrets.init.template;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.Locale;

public enum StorageUnit implements OIMRContent.BlockInfo
{
    BUFFER(1, false);

    public final String name;
    public final Block block;

    // How many items it can hold
    public final int capacity;


    StorageUnit(int capacity, boolean upgradable)
    {
        name = this.toString().toLowerCase(Locale.ROOT);
        block = new StorageUnitBlock(this, name.equals("buffer") ? "storage_buffer" : name + "_storage_unit");
        this.capacity = capacity;

        if (name.equals("buffer"))
            InitUtils.setup(block, "storage_buffer");
        else
            InitUtils.setup(block, name + "_storage_unit");
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Block getBlock()
    {
        return block;
    }

    public Block asBlock()
    {
        return block;
    }

    @Override
    public Item asItem()
    {
        return block.asItem();
    }
}
