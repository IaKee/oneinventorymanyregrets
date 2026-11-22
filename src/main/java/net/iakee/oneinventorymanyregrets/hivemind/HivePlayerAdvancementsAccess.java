package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;

import java.util.Map;

public interface HivePlayerAdvancementsAccess {
    Map<Identifier, AdvancementProgress> oimr$exportProgress();

    void oimr$syncFromHive(ServerAdvancementManager manager, Map<Identifier, AdvancementProgress> hiveProgress);
}
