package net.iakee.oneinventorymanyregrets.hivemind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * World-level persistent storage for all Hive Mind groups and per-player personal slot overrides.
 */
public final class HiveMindData extends SavedData {
    public static final String DEFAULT_HIVE_ID = "default";
    public static final String TEAM_HIVE_PREFIX = "team:";

    public static final Codec<HiveMindData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        HiveMind.CODEC.listOf().optionalFieldOf("hiveminds", List.of()).forGetter(data -> List.copyOf(data.hiveMinds.values())),
        Codec.unboundedMap(Codec.STRING, PersonalInventoryState.CODEC)
            .optionalFieldOf("personal_inventories", Map.of())
            .forGetter(data -> data.encodePersonalInventories())
    ).apply(instance, HiveMindData::new));

    public static final SavedDataType<HiveMindData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "hiveminds"),
        HiveMindData::new,
        CODEC,
        null
    );

    private final Map<String, HiveMind> hiveMinds = new HashMap<>();
    private final Map<UUID, PersonalInventoryState> personalInventories = new HashMap<>();

    public HiveMindData() {
    }

    private HiveMindData(List<HiveMind> loaded, Map<String, PersonalInventoryState> loadedPersonal) {
        for (HiveMind hive : loaded) {
            this.hiveMinds.put(hive.getId(), hive);
        }
        loadedPersonal.forEach((uuidStr, state) -> {
            try {
                this.personalInventories.put(UUID.fromString(uuidStr), state);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    private Map<String, PersonalInventoryState> encodePersonalInventories() {
        Map<String, PersonalInventoryState> encoded = new HashMap<>(this.personalInventories.size());
        this.personalInventories.forEach((uuid, state) -> encoded.put(uuid.toString(), state));
        return encoded;
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

    public boolean removeHive(String hiveId) {
        if (this.hiveMinds.remove(hiveId) != null) {
            this.setDirty();
            return true;
        }
        return false;
    }

    public @Nullable PersonalInventoryState getPersonalStateIfPresent(UUID playerId) {
        return this.personalInventories.get(playerId);
    }

    public Map<UUID, PersonalInventoryState> getPersonalInventoriesView() {
        return Collections.unmodifiableMap(this.personalInventories);
    }

    public PersonalInventoryState getOrCreatePersonalState(UUID playerId) {
        return this.personalInventories.computeIfAbsent(playerId, ignored -> {
            this.setDirty();
            return new PersonalInventoryState();
        });
    }

    public void removePersonalState(UUID playerId) {
        if (this.personalInventories.remove(playerId) != null) {
            this.setDirty();
        }
    }
}
