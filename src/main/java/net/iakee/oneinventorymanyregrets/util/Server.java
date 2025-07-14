package net.iakee.oneinventorymanyregrets.util;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Server
{
    private static MinecraftServer MC_SERVER = null;

    private static final List<Consumer<MinecraftServer>> SERVER_STARTING_HANDLERS = new ArrayList<>();

    @Nullable
    public static MinecraftServer getServer()
    {
        return MC_SERVER;
    }

    @ApiStatus.Internal()
    public static void setServerWhenStarting(MinecraftServer sv)
    {
        MC_SERVER = sv;

        for (Consumer<MinecraftServer> handler : SERVER_STARTING_HANDLERS)
        {
            handler.accept(sv);
        }

        SERVER_STARTING_HANDLERS.clear();
    }

    public static DynamicRegistryManager getDynamicRegistry()
    {
        if(MC_SERVER != null)
        {
            return MC_SERVER.getRegistryManager();
        }

        // TODO: change this
        System.out.println("Returning an empty DynamicRegistryManager");

        return DynamicRegistryManager.EMPTY;
    }
}
