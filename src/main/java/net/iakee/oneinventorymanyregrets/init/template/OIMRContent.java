package net.iakee.oneinventorymanyregrets.init.template;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Locale;

public class OIMRContent
{
    public static final Marker DATAGEN = MarkerFactory.getMarker("datagen");

    // blocks
    public static Block TEAM_CHEST;

    // tools
    public static Item SABOTAGE_TOOL;

    public interface ItemInfo extends ItemConvertible
    {
        String getName();
    }

    public interface BlockInfo extends ItemConvertible
    {
        String getName();
        Block getBlock();
    }

   
}
