package net.iakee.oneinventorymanyregrets.data;

import net.iakee.oneinventorymanyregrets.util.Server;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;

import net.iakee.oneinventorymanyregrets.mixin.NbtWriteViewMixin;

public final class OIMRData
{
    // creates an empty NbtWriteView
    public static NbtWriteView createNbtWriteView(
        ErrorReporter reporter,
        RegistryWrapper.WrapperLookup rm)
    {
        return NbtWriteView.create(reporter, rm);
    }

    // creates an NbtWriteView with the given NBT compound as the target
    public static NbtWriteView createNbtWriteView(
        ErrorReporter reporter,
        RegistryWrapper.WrapperLookup rm,
        NbtCompound target)
    {
        return NbtWriteViewMixin.bb$createNbtWriteView(reporter, rm.getOps(NbtOps.INSTANCE), target);
    }


    // create an NbtWriteView with the given NBT compound as the target
    public static NbtWriteView createNbtWriteView(NbtCompound target)
    {
        var reporter = new ErrorReporter.Impl();
        return NbtWriteViewMixin.bb$createNbtWriteView(
            reporter,
            Server.getDynamicRegistry().getOps(NbtOps.INSTANCE),
            target);
    }
}
