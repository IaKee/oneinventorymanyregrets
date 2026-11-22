package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Collection;
import java.util.Set;

public interface HiveServerRecipeBookAccess {
    Set<ResourceKey<Recipe<?>>> oimr$exportKnown();

    Set<ResourceKey<Recipe<?>>> oimr$exportHighlight();

    void oimr$syncFromHive(
        Collection<ResourceKey<Recipe<?>>> known,
        Collection<ResourceKey<Recipe<?>>> highlight,
        ServerPlayer player
    );
}
