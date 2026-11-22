package net.iakee.oneinventorymanyregrets.hivemind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World-level persistent storage for all Hive Mind groups.
 */
public final class HiveMindData extends SavedData {
    public static final String DEFAULT_HIVE_ID = "default";
    public static final String TEAM_HIVE_PREFIX = "team:";

    public static final Codec<HiveMindData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        HiveMind.CODEC.listOf().optionalFieldOf("hiveminds", List.of()).forGetter(data -> List.copyOf(data.hiveMinds.values()))
    ).apply(instance, HiveMindData::new));

    public static final SavedDataType<HiveMindData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "hiveminds"),
        HiveMindData::new,
        CODEC,
        null
    );

    private final Map<String, HiveMind> hiveMinds = new HashMap<>();

    public HiveMindData() {
    }

    private HiveMindData(List<HiveMind> loaded) {
        for (HiveMind hive : loaded) {
            this.hiveMinds.put(hive.getId(), hive);
        }
    }

    public static HiveMindData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public HiveMind getOrCreate(String hiveId) {
        return this.hiveMinds.computeIfAbsent(hiveId, id -> {
            this.setDirty();
            return new HiveMind(id);
        });
    }

    public HiveMind getIfPresent(String hiveId) {
        return this.hiveMinds.get(hiveId);
    }

    public Collection<HiveMind> getAll() {
        return this.hiveMinds.values();
    }
}
