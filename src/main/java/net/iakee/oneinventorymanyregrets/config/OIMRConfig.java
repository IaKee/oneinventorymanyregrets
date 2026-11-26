package net.iakee.oneinventorymanyregrets.config;

import net.fabricmc.loader.api.FabricLoader;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Persistent configuration for One Inventory, Many Regrets.
 * Editable in-game only by the Singleplayer/LAN host or via dedicated server console.
 */
public final class OIMRConfig {
    private static final String FILE_NAME = OIMRMod.MOD_ID + ".properties";
    public static final String KEY_SHARED_KNOCKBACK = "sharedKnockback";
    public static final String KEY_SHARED_ENDER_PEARL = "sharedEnderPearl";
    public static final String KEY_SHARED_PORTALS = "sharedPortals";
    public static final String KEY_UNDERWATER_MUFFLE = "underwaterMuffle";
    public static final String KEY_SHARED_MOB_SOUNDS = "sharedMobSounds";
    public static final String KEY_SHARED_MOB_SOUND_FILTER = "sharedMobSoundFilter";
    public static final String KEY_SHARED_MOB_SOUND_VOLUME = "sharedMobSoundVolume";
    public static final String KEY_SHARED_CAVE_SOUNDS = "sharedCaveSounds";
    public static final String KEY_LAVA_DAMAGE_MODE = "lavaDamageMode";
    public static final String KEY_UNDERWATER_BREATH_MODE = "underwaterBreathMode";
    public static final String KEY_DOT_DAMAGE_MODE = "dotDamageMode";
    public static final String KEY_COMUNISM = "comunism";
    public static final String KEY_FRIENDLY_PETS = "friendlyPets";
    public static final String KEY_EXCLUDE_CREATIVE_FROM_DEATH = "excludeCreativeFromDeath";
    public static final String KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH = "wipeCreativeInventoryOnDeath";
    public static final String KEY_RANDOM_DEATH_LOOT = "randomDeathLoot";
    private static final String KEY_HUNGER_WEIGHT = "hungerWeight";
    private static final String KEY_XP_WEIGHT = "xpWeight";
    private static final String KEY_AFFINITY = "affinity";
    public static final String KEY_SYNC_EFFECTS = "syncEffects";
    public static final String KEY_SYNC_ADVANCEMENTS = "syncAdvancements";
    public static final String KEY_SYNC_HOTBAR = "syncHotbar";
    public static final String KEY_SYNC_HELMET = "syncHelmet";
    public static final String KEY_SYNC_CHESTPLATE = "syncChestplate";
    public static final String KEY_SYNC_LEGGINGS = "syncLeggings";
    public static final String KEY_SYNC_BOOTS = "syncBoots";
    public static final String KEY_SYNC_MAIN_HAND = "syncMainHand";
    public static final String KEY_SYNC_OFF_HAND = "syncOffHand";

    public static final float MIN_HUNGER_WEIGHT = 0.0F;
    public static final float MAX_HUNGER_WEIGHT = 100.0F;
    public static final float MIN_XP_WEIGHT = 0.0F;
    public static final float MAX_XP_WEIGHT = 100.0F;
    public static final int MIN_AFFINITY = 0;
    public static final int MAX_AFFINITY = 100;
    public static final int MIN_SHARED_MOB_SOUND_VOLUME = 0;
    public static final int MAX_SHARED_MOB_SOUND_VOLUME = 100;

    public enum MobSoundFilter {
        ALL("all", "oimr.settings.shared_mob_sound_filter.all"),
        HOSTILE("hostile", "oimr.settings.shared_mob_sound_filter.hostile"),
        NEUTRAL("neutral", "oimr.settings.shared_mob_sound_filter.neutral"),
        NONE("none", "oimr.settings.shared_mob_sound_filter.none");

        private final String configValue;
        private final String translationKey;

        MobSoundFilter(String configValue, String translationKey) {
            this.configValue = configValue;
            this.translationKey = translationKey;
        }

        public String configValue() {
            return this.configValue;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public boolean matches(SoundSource source) {
            return switch (this) {
                case ALL -> source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL;
                case HOSTILE -> source == SoundSource.HOSTILE;
                case NEUTRAL -> source == SoundSource.NEUTRAL;
                case NONE -> false;
            };
        }

        public static MobSoundFilter fromString(String raw) {
            if (raw == null) {
                return ALL;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            for (MobSoundFilter value : values()) {
                if (value.configValue.equals(normalized)) {
                    return value;
                }
            }
            return ALL;
        }

        private static MobSoundFilter parseConfigValue(String raw) {
            if (raw == null) {
                return ALL;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            for (MobSoundFilter value : values()) {
                if (value.configValue.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Invalid " + KEY_SHARED_MOB_SOUND_FILTER + ": " + raw);
        }
    }

    public enum MultiMemberScalingMode {
        CAP_1X("1x", "oimr.settings.scaling_mode.1x"),
        MULTIPLICATIVE("nx", "oimr.settings.scaling_mode.nx");

        private final String configValue;
        private final String translationKey;

        MultiMemberScalingMode(String configValue, String translationKey) {
            this.configValue = configValue;
            this.translationKey = translationKey;
        }

        public String configValue() {
            return this.configValue;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public boolean isCapped1x() {
            return this == CAP_1X;
        }

        public static MultiMemberScalingMode fromString(String raw) {
            if (raw == null) {
                return CAP_1X;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "nx", "multiplicative", "multi", "unbounded", "false" -> MULTIPLICATIVE;
                default -> CAP_1X;
            };
        }

        private static MultiMemberScalingMode parseConfigValue(String key, String raw) {
            if (raw == null) {
                return CAP_1X;
            }
            String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "1x", "cap_1x", "capped", "true" -> CAP_1X;
                case "nx", "multiplicative", "multi", "unbounded", "false" -> MULTIPLICATIVE;
                default -> throw new IllegalArgumentException("Invalid " + key + ": " + raw);
            };
        }
    }

    public enum ReloadStatus {
        LOADED,
        CREATED_DEFAULTS,
        FAILED_APPLIED_NOT_PERSISTED,
        FAILED_USING_DEFAULTS,
        FAILED_KEPT_PREVIOUS
    }

    public record ReloadResult(ReloadStatus status, @Nullable String errorMessage) {
        public boolean isSuccess() {
            return this.status == ReloadStatus.LOADED || this.status == ReloadStatus.CREATED_DEFAULTS;
        }
    }

    private record ConfigSnapshot(
        boolean sharedKnockback,
        boolean sharedEnderPearl,
        boolean sharedPortals,
        boolean underwaterMuffle,
        boolean sharedMobSounds,
        MobSoundFilter sharedMobSoundFilter,
        int sharedMobSoundVolume,
        boolean sharedCaveSounds,
        MultiMemberScalingMode lavaDamageMode,
        MultiMemberScalingMode underwaterBreathMode,
        MultiMemberScalingMode dotDamageMode,
        boolean comunism,
        boolean friendlyPets,
        boolean excludeCreativeFromDeath,
        boolean wipeCreativeInventoryOnDeath,
        boolean randomDeathLoot,
        float hungerWeight,
        float xpWeight,
        int affinity,
        boolean invalidAffinityResetPending,
        boolean resaveNeeded,
        boolean syncEffects,
        boolean syncAdvancements,
        boolean syncHotbar,
        boolean syncHelmet,
        boolean syncChestplate,
        boolean syncLeggings,
        boolean syncBoots,
        boolean syncMainHand,
        boolean syncOffHand
    ) {
        static ConfigSnapshot defaults() {
            return new ConfigSnapshot(
                true,
                true,
                true,
                true,
                true,
                MobSoundFilter.ALL,
                MAX_SHARED_MOB_SOUND_VOLUME,
                true,
                MultiMemberScalingMode.CAP_1X,
                MultiMemberScalingMode.CAP_1X,
                MultiMemberScalingMode.CAP_1X,
                false,
                true,
                true,
                true,
                true,
                1.0F,
                1.0F,
                MAX_AFFINITY,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
            );
        }

        static ConfigSnapshot parse(Properties props) {
            boolean nextSharedKnockback = parseBoolean(props, KEY_SHARED_KNOCKBACK, true);
            boolean nextSharedEnderPearl = parseBoolean(props, KEY_SHARED_ENDER_PEARL, true);
            boolean nextSharedPortals = parseBoolean(props, KEY_SHARED_PORTALS, true);
            boolean nextUnderwaterMuffle = parseBoolean(props, KEY_UNDERWATER_MUFFLE, true);
            boolean nextSharedMobSounds = parseBoolean(props, KEY_SHARED_MOB_SOUNDS, true);
            MobSoundFilter nextSharedMobSoundFilter = MobSoundFilter.parseConfigValue(props.getProperty(KEY_SHARED_MOB_SOUND_FILTER));
            int nextSharedMobSoundVolume = parsePercent(
                props.getProperty(KEY_SHARED_MOB_SOUND_VOLUME),
                MAX_SHARED_MOB_SOUND_VOLUME,
                MIN_SHARED_MOB_SOUND_VOLUME,
                MAX_SHARED_MOB_SOUND_VOLUME
            );
            boolean nextSharedCaveSounds = parseBoolean(props, KEY_SHARED_CAVE_SOUNDS, true);
            MultiMemberScalingMode nextLavaDamageMode = MultiMemberScalingMode.parseConfigValue(
                KEY_LAVA_DAMAGE_MODE,
                props.getProperty(KEY_LAVA_DAMAGE_MODE)
            );
            MultiMemberScalingMode nextUnderwaterBreathMode = MultiMemberScalingMode.parseConfigValue(
                KEY_UNDERWATER_BREATH_MODE,
                props.getProperty(KEY_UNDERWATER_BREATH_MODE)
            );
            MultiMemberScalingMode nextDotDamageMode = MultiMemberScalingMode.parseConfigValue(
                KEY_DOT_DAMAGE_MODE,
                props.getProperty(KEY_DOT_DAMAGE_MODE)
            );
            boolean nextComunism = parseBoolean(props, KEY_COMUNISM, false);
            boolean nextFriendlyPets = parseBoolean(props, KEY_FRIENDLY_PETS, true);
            boolean nextExcludeCreative = parseBoolean(props, KEY_EXCLUDE_CREATIVE_FROM_DEATH, true);
            boolean nextWipeCreative = parseBoolean(props, KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, true);
            boolean nextRandomDeathLoot = parseBoolean(props, KEY_RANDOM_DEATH_LOOT, true);
            float nextHungerWeight = parseWeight(props.getProperty(KEY_HUNGER_WEIGHT, "1.0"), MIN_HUNGER_WEIGHT, MAX_HUNGER_WEIGHT);
            float nextXpWeight = parseWeight(props.getProperty(KEY_XP_WEIGHT, "1.0"), MIN_XP_WEIGHT, MAX_XP_WEIGHT);
            String rawAffinity = props.getProperty(KEY_AFFINITY);
            int nextAffinity;
            boolean nextInvalidAffinityResetPending;
            boolean nextResaveNeeded = false;
            if (rawAffinity != null && !isValidAffinityRaw(rawAffinity)) {
                nextAffinity = MAX_AFFINITY;
                nextInvalidAffinityResetPending = true;
                nextResaveNeeded = true;
            } else {
                nextAffinity = parseAffinity(rawAffinity != null ? rawAffinity : "100");
                nextInvalidAffinityResetPending = false;
            }
            boolean nextSyncEffects = parseBoolean(props, KEY_SYNC_EFFECTS, true);
            boolean nextSyncAdvancements = parseBoolean(props, KEY_SYNC_ADVANCEMENTS, true);
            boolean nextSyncHotbar = parseBoolean(props, KEY_SYNC_HOTBAR, true);
            boolean nextSyncHelmet = parseBoolean(props, KEY_SYNC_HELMET, true);
            boolean nextSyncChestplate = parseBoolean(props, KEY_SYNC_CHESTPLATE, true);
            boolean nextSyncLeggings = parseBoolean(props, KEY_SYNC_LEGGINGS, true);
            boolean nextSyncBoots = parseBoolean(props, KEY_SYNC_BOOTS, true);
            boolean nextSyncMainHand = parseBoolean(props, KEY_SYNC_MAIN_HAND, true);
            boolean nextSyncOffHand = parseBoolean(props, KEY_SYNC_OFF_HAND, true);
            return new ConfigSnapshot(
                nextSharedKnockback,
                nextSharedEnderPearl,
                nextSharedPortals,
                nextUnderwaterMuffle,
                nextSharedMobSounds,
                nextSharedMobSoundFilter,
                nextSharedMobSoundVolume,
                nextSharedCaveSounds,
                nextLavaDamageMode,
                nextUnderwaterBreathMode,
                nextDotDamageMode,
                nextComunism,
                nextFriendlyPets,
                nextExcludeCreative,
                nextWipeCreative,
                nextRandomDeathLoot,
                nextHungerWeight,
                nextXpWeight,
                nextAffinity,
                nextInvalidAffinityResetPending,
                nextResaveNeeded,
                nextSyncEffects,
                nextSyncAdvancements,
                nextSyncHotbar,
                nextSyncHelmet,
                nextSyncChestplate,
                nextSyncLeggings,
                nextSyncBoots,
                nextSyncMainHand,
                nextSyncOffHand
            );
        }

        SlotSyncState slotSyncState() {
            return new SlotSyncState(
                this.syncHotbar,
                this.syncMainHand,
                this.syncOffHand,
                this.syncHelmet,
                this.syncChestplate,
                this.syncLeggings,
                this.syncBoots
            );
        }
    }

    private static volatile boolean loadedOnce = false;
    private static volatile @Nullable String lastSaveErrorMessage = null;
    private static volatile boolean sharedKnockback = true;
    private static volatile boolean sharedEnderPearl = true;
    private static volatile boolean sharedPortals = true;
    private static volatile boolean underwaterMuffle = true;
    private static volatile boolean sharedMobSounds = true;
    private static volatile MobSoundFilter sharedMobSoundFilter = MobSoundFilter.ALL;
    private static volatile int sharedMobSoundVolume = MAX_SHARED_MOB_SOUND_VOLUME;
    private static volatile boolean sharedCaveSounds = true;
    private static volatile MultiMemberScalingMode lavaDamageMode = MultiMemberScalingMode.CAP_1X;
    private static volatile MultiMemberScalingMode underwaterBreathMode = MultiMemberScalingMode.CAP_1X;
    private static volatile MultiMemberScalingMode dotDamageMode = MultiMemberScalingMode.CAP_1X;
    private static volatile boolean comunism = false;
    private static volatile boolean friendlyPets = true;
    private static volatile boolean excludeCreativeFromDeath = true;
    private static volatile boolean wipeCreativeInventoryOnDeath = true;
    private static volatile boolean randomDeathLoot = true;
    private static volatile float hungerWeight = 1.0F;
    private static volatile float xpWeight = 1.0F;
    private static volatile int affinity = MAX_AFFINITY;
    private static volatile boolean invalidAffinityResetPending = false;
    private static volatile boolean syncEffects = true;
    private static volatile boolean syncAdvancements = true;
    private static volatile boolean syncHotbar = true;
    private static volatile boolean syncHelmet = true;
    private static volatile boolean syncChestplate = true;
    private static volatile boolean syncLeggings = true;
    private static volatile boolean syncBoots = true;
    private static volatile boolean syncMainHand = true;
    private static volatile boolean syncOffHand = true;

    public record SlotSyncState(
        boolean syncHotbar,
        boolean syncMainHand,
        boolean syncOffHand,
        boolean syncHelmet,
        boolean syncChestplate,
        boolean syncLeggings,
        boolean syncBoots
    ) {
        public static final SlotSyncState ALL_SYNCED = new SlotSyncState(true, true, true, true, true, true, true);

        public boolean isHotbarSlotSynced(int slot) {
            if (slot < 0 || slot >= 9) {
                return true;
            }
            if (this.syncHotbar) {
                return true;
            }
            return this.syncMainHand && slot == 0;
        }

        public boolean isEquipmentSlotSynced(EquipmentSlot slot) {
            return switch (slot) {
                case HEAD -> this.syncHelmet;
                case CHEST -> this.syncChestplate;
                case LEGS -> this.syncLeggings;
                case FEET -> this.syncBoots;
                case OFFHAND -> this.syncOffHand;
                case MAINHAND -> this.syncHotbar || this.syncMainHand;
                default -> true;
            };
        }

        public SlotSyncState withSetting(String key, boolean enabled) {
            return switch (key) {
                case KEY_SYNC_HOTBAR -> new SlotSyncState(enabled, this.syncMainHand, this.syncOffHand, this.syncHelmet, this.syncChestplate, this.syncLeggings, this.syncBoots);
                case KEY_SYNC_MAIN_HAND -> new SlotSyncState(this.syncHotbar, enabled, this.syncOffHand, this.syncHelmet, this.syncChestplate, this.syncLeggings, this.syncBoots);
                case KEY_SYNC_OFF_HAND -> new SlotSyncState(this.syncHotbar, this.syncMainHand, enabled, this.syncHelmet, this.syncChestplate, this.syncLeggings, this.syncBoots);
                case KEY_SYNC_HELMET -> new SlotSyncState(this.syncHotbar, this.syncMainHand, this.syncOffHand, enabled, this.syncChestplate, this.syncLeggings, this.syncBoots);
                case KEY_SYNC_CHESTPLATE -> new SlotSyncState(this.syncHotbar, this.syncMainHand, this.syncOffHand, this.syncHelmet, enabled, this.syncLeggings, this.syncBoots);
                case KEY_SYNC_LEGGINGS -> new SlotSyncState(this.syncHotbar, this.syncMainHand, this.syncOffHand, this.syncHelmet, this.syncChestplate, enabled, this.syncBoots);
                case KEY_SYNC_BOOTS -> new SlotSyncState(this.syncHotbar, this.syncMainHand, this.syncOffHand, this.syncHelmet, this.syncChestplate, this.syncLeggings, enabled);
                default -> this;
            };
        }
    }

    private OIMRConfig() {
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static Path getBackupPath(Path configPath) {
        return configPath.resolveSibling(configPath.getFileName().toString() + ".bak");
    }

    public static synchronized void load() {
        loadFromPath(getConfigPath(), false, null);
    }

    public static synchronized void reloadWithSlotTransition(Consumer<SlotSyncState> beforeApply) {
        reloadWithResult(beforeApply);
    }

    public static synchronized ReloadResult reloadWithResult(@Nullable Consumer<SlotSyncState> beforeApply) {
        return loadFromPath(getConfigPath(), true, beforeApply);
    }

    public static synchronized ReloadResult loadFromPath(
        Path path,
        boolean isReload,
        @Nullable Consumer<SlotSyncState> beforeApply
    ) {
        if (!Files.exists(path)) {
            ConfigSnapshot defaults = ConfigSnapshot.defaults();
            if (beforeApply != null) {
                beforeApply.accept(defaults.slotSyncState());
            }
            applySnapshot(defaults);
            loadedOnce = true;
            if (!saveToPath(path)) {
                return new ReloadResult(ReloadStatus.FAILED_APPLIED_NOT_PERSISTED, lastSaveErrorMessage);
            }
            return new ReloadResult(ReloadStatus.CREATED_DEFAULTS, null);
        }

        Properties props = new Properties();
        ConfigSnapshot parsed;
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            parsed = ConfigSnapshot.parse(props);
        } catch (IOException | IllegalArgumentException e) {
            if (!isReload && !loadedOnce) {
                applySnapshot(ConfigSnapshot.defaults());
                loadedOnce = true;
                OIMRMod.LOGGER.warn("Failed to load config at {}; using defaults", path, e);
                return new ReloadResult(ReloadStatus.FAILED_USING_DEFAULTS, e.getMessage());
            }
            OIMRMod.LOGGER.warn("Failed to reload config at {}; keeping active in-memory settings", path, e);
            return new ReloadResult(ReloadStatus.FAILED_KEPT_PREVIOUS, e.getMessage());
        }

        if (beforeApply != null) {
            beforeApply.accept(parsed.slotSyncState());
        }
        applySnapshot(parsed);
        loadedOnce = true;
        if (parsed.resaveNeeded() && !saveToPath(path)) {
            return new ReloadResult(ReloadStatus.FAILED_APPLIED_NOT_PERSISTED, lastSaveErrorMessage);
        }
        return new ReloadResult(ReloadStatus.LOADED, null);
    }

    private static void applySnapshot(ConfigSnapshot snapshot) {
        sharedKnockback = snapshot.sharedKnockback();
        sharedEnderPearl = snapshot.sharedEnderPearl();
        sharedPortals = snapshot.sharedPortals();
        underwaterMuffle = snapshot.underwaterMuffle();
        sharedMobSounds = snapshot.sharedMobSounds();
        sharedMobSoundFilter = snapshot.sharedMobSoundFilter();
        sharedMobSoundVolume = snapshot.sharedMobSoundVolume();
        sharedCaveSounds = snapshot.sharedCaveSounds();
        lavaDamageMode = snapshot.lavaDamageMode();
        underwaterBreathMode = snapshot.underwaterBreathMode();
        dotDamageMode = snapshot.dotDamageMode();
        comunism = snapshot.comunism();
        friendlyPets = snapshot.friendlyPets();
        excludeCreativeFromDeath = snapshot.excludeCreativeFromDeath();
        wipeCreativeInventoryOnDeath = snapshot.wipeCreativeInventoryOnDeath();
        randomDeathLoot = snapshot.randomDeathLoot();
        hungerWeight = snapshot.hungerWeight();
        xpWeight = snapshot.xpWeight();
        affinity = snapshot.affinity();
        invalidAffinityResetPending = snapshot.invalidAffinityResetPending();
        syncEffects = snapshot.syncEffects();
        syncAdvancements = snapshot.syncAdvancements();
        syncHotbar = snapshot.syncHotbar();
        syncHelmet = snapshot.syncHelmet();
        syncChestplate = snapshot.syncChestplate();
        syncLeggings = snapshot.syncLeggings();
        syncBoots = snapshot.syncBoots();
        syncMainHand = snapshot.syncMainHand();
        syncOffHand = snapshot.syncOffHand();
    }

    private static void applyDefaults() {
        applySnapshot(ConfigSnapshot.defaults());
    }

    public static synchronized boolean resetToDefaults() {
        applyDefaults();
        return save();
    }

    public static synchronized void resetLoadedStateForTests() {
        applyDefaults();
        loadedOnce = false;
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean for " + key + ": " + raw);
    }

    private static float parseWeight(String raw, float min, float max) {
        try {
            float parsed = Float.parseFloat(raw.trim());
            if (!Float.isFinite(parsed) || parsed < min || parsed > max) {
                throw new IllegalArgumentException("Weight out of range " + min + ".." + max + ": " + raw);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid weight: " + raw, e);
        }
    }

    private static float sanitizeWeight(float weight, float min, float max) {
        if (!Float.isFinite(weight)) {
            return 1.0F;
        }
        return Math.max(min, Math.min(max, weight));
    }

    private static boolean isValidAffinityRaw(String raw) {
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed)) {
                return false;
            }
            return parsed >= MIN_AFFINITY && parsed <= MAX_AFFINITY;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parseAffinity(String raw) {
        return parsePercent(raw, MAX_AFFINITY, MIN_AFFINITY, MAX_AFFINITY);
    }

    private static int sanitizeAffinity(int value) {
        return sanitizePercent(value, MIN_AFFINITY, MAX_AFFINITY);
    }

    private static int parsePercent(String raw, int defaultValue, int min, int max) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
                throw new IllegalArgumentException("Percentage out of range " + min + ".." + max + ": " + raw);
            }
            return (int) Math.round(parsed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid percentage: " + raw, e);
        }
    }

    private static int sanitizePercent(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static synchronized boolean consumeInvalidAffinityResetPending() {
        boolean pending = invalidAffinityResetPending;
        invalidAffinityResetPending = false;
        return pending;
    }

    public static synchronized boolean save() {
        return saveToPath(getConfigPath());
    }

    public static @Nullable String getLastSaveErrorMessage() {
        return lastSaveErrorMessage;
    }

    public static synchronized boolean saveToPath(Path path) {
        Path normalized = path.toAbsolutePath();
        Path parent = normalized.getParent();
        Path tempFile = null;
        lastSaveErrorMessage = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties props = buildCurrentProperties();
            tempFile = parent != null
                ? Files.createTempFile(parent, normalized.getFileName().toString() + ".", ".tmp")
                : Files.createTempFile(normalized.getFileName().toString() + ".", ".tmp");

            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                OutputStream out = Channels.newOutputStream(channel);
                props.store(
                    out,
                    "One Inventory, Many Regrets settings\n"
                        + "Disabling sync for any inventory/equipment slot (syncHotbar, syncMainHand, syncOffHand, syncHelmet, syncChestplate, syncLeggings, syncBoots) drops currently shared items in-world before switching those slots to per-player storage.\n"
                        + "affinity must be between 0 and 100 (values outside 0..100 are unsupported and reset to 100)."
                );
                out.flush();
                channel.force(true);
            }

            if (Files.exists(normalized) && Files.isRegularFile(normalized)) {
                Files.copy(normalized, getBackupPath(normalized), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(tempFile, normalized, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | AccessDeniedException atomicFailure) {
                try {
                    Files.move(tempFile, normalized, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException fallbackFailure) {
                    fallbackFailure.addSuppressed(atomicFailure);
                    throw fallbackFailure;
                }
            }
            return true;
        } catch (IOException e) {
            lastSaveErrorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            OIMRMod.LOGGER.warn("Failed to save config at {}", normalized, e);
            return false;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Properties buildCurrentProperties() {
        Properties props = new Properties();
        props.setProperty(KEY_SHARED_KNOCKBACK, Boolean.toString(sharedKnockback));
        props.setProperty(KEY_SHARED_ENDER_PEARL, Boolean.toString(sharedEnderPearl));
        props.setProperty(KEY_SHARED_PORTALS, Boolean.toString(sharedPortals));
        props.setProperty(KEY_UNDERWATER_MUFFLE, Boolean.toString(underwaterMuffle));
        props.setProperty(KEY_SHARED_MOB_SOUNDS, Boolean.toString(sharedMobSounds));
        props.setProperty(KEY_SHARED_MOB_SOUND_FILTER, sharedMobSoundFilter.configValue());
        props.setProperty(KEY_SHARED_MOB_SOUND_VOLUME, Integer.toString(sharedMobSoundVolume));
        props.setProperty(KEY_SHARED_CAVE_SOUNDS, Boolean.toString(sharedCaveSounds));
        props.setProperty(KEY_LAVA_DAMAGE_MODE, lavaDamageMode.configValue());
        props.setProperty(KEY_UNDERWATER_BREATH_MODE, underwaterBreathMode.configValue());
        props.setProperty(KEY_DOT_DAMAGE_MODE, dotDamageMode.configValue());
        props.setProperty(KEY_COMUNISM, Boolean.toString(comunism));
        props.setProperty(KEY_FRIENDLY_PETS, Boolean.toString(friendlyPets));
        props.setProperty(KEY_EXCLUDE_CREATIVE_FROM_DEATH, Boolean.toString(excludeCreativeFromDeath));
        props.setProperty(KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, Boolean.toString(wipeCreativeInventoryOnDeath));
        props.setProperty(KEY_RANDOM_DEATH_LOOT, Boolean.toString(randomDeathLoot));
        props.setProperty(KEY_HUNGER_WEIGHT, Float.toString(hungerWeight));
        props.setProperty(KEY_XP_WEIGHT, Float.toString(xpWeight));
        props.setProperty(KEY_AFFINITY, Integer.toString(affinity));
        props.setProperty(KEY_SYNC_EFFECTS, Boolean.toString(syncEffects));
        props.setProperty(KEY_SYNC_ADVANCEMENTS, Boolean.toString(syncAdvancements));
        props.setProperty(KEY_SYNC_HOTBAR, Boolean.toString(syncHotbar));
        props.setProperty(KEY_SYNC_HELMET, Boolean.toString(syncHelmet));
        props.setProperty(KEY_SYNC_CHESTPLATE, Boolean.toString(syncChestplate));
        props.setProperty(KEY_SYNC_LEGGINGS, Boolean.toString(syncLeggings));
        props.setProperty(KEY_SYNC_BOOTS, Boolean.toString(syncBoots));
        props.setProperty(KEY_SYNC_MAIN_HAND, Boolean.toString(syncMainHand));
        props.setProperty(KEY_SYNC_OFF_HAND, Boolean.toString(syncOffHand));
        return props;
    }

    public static boolean isSharedKnockbackEnabled() {
        return sharedKnockback;
    }

    public static synchronized boolean setSharedKnockback(boolean enabled) {
        if (sharedKnockback != enabled || !Files.exists(getConfigPath())) {
            sharedKnockback = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSharedEnderPearlEnabled() {
        return sharedEnderPearl;
    }

    public static synchronized boolean setSharedEnderPearl(boolean enabled) {
        if (sharedEnderPearl != enabled || !Files.exists(getConfigPath())) {
            sharedEnderPearl = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSharedPortalsEnabled() {
        return sharedPortals;
    }

    public static synchronized boolean setSharedPortals(boolean enabled) {
        if (sharedPortals != enabled || !Files.exists(getConfigPath())) {
            sharedPortals = enabled;
            return save();
        }
        return true;
    }

    public static boolean isUnderwaterMuffleEnabled() {
        return underwaterMuffle;
    }

    public static synchronized boolean setUnderwaterMuffle(boolean enabled) {
        if (underwaterMuffle != enabled || !Files.exists(getConfigPath())) {
            underwaterMuffle = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSharedMobSoundsEnabled() {
        return sharedMobSounds;
    }

    public static synchronized boolean setSharedMobSounds(boolean enabled) {
        if (sharedMobSounds != enabled || !Files.exists(getConfigPath())) {
            sharedMobSounds = enabled;
            return save();
        }
        return true;
    }

    public static MobSoundFilter getSharedMobSoundFilter() {
        return sharedMobSoundFilter;
    }

    public static synchronized boolean setSharedMobSoundFilter(MobSoundFilter filter) {
        MobSoundFilter sanitized = filter != null ? filter : MobSoundFilter.ALL;
        if (sharedMobSoundFilter != sanitized || !Files.exists(getConfigPath())) {
            sharedMobSoundFilter = sanitized;
            return save();
        }
        return true;
    }

    public static int getSharedMobSoundVolumePercent() {
        return sharedMobSoundVolume;
    }

    public static float getSharedMobSoundVolumeFactor() {
        return sharedMobSoundVolume / 100.0F;
    }

    public static synchronized boolean setSharedMobSoundVolumePercent(int percent) {
        int sanitized = sanitizePercent(percent, MIN_SHARED_MOB_SOUND_VOLUME, MAX_SHARED_MOB_SOUND_VOLUME);
        if (sharedMobSoundVolume != sanitized || !Files.exists(getConfigPath())) {
            sharedMobSoundVolume = sanitized;
            return save();
        }
        return true;
    }

    public static boolean isSharedCaveSoundsEnabled() {
        return sharedCaveSounds;
    }

    public static synchronized boolean setSharedCaveSounds(boolean enabled) {
        if (sharedCaveSounds != enabled || !Files.exists(getConfigPath())) {
            sharedCaveSounds = enabled;
            return save();
        }
        return true;
    }

    public static MultiMemberScalingMode getLavaDamageMode() {
        return lavaDamageMode;
    }

    public static boolean isLavaDamageCapped1x() {
        return lavaDamageMode.isCapped1x();
    }

    public static synchronized boolean setLavaDamageMode(MultiMemberScalingMode mode) {
        MultiMemberScalingMode sanitized = mode != null ? mode : MultiMemberScalingMode.CAP_1X;
        if (lavaDamageMode != sanitized || !Files.exists(getConfigPath())) {
            lavaDamageMode = sanitized;
            return save();
        }
        return true;
    }

    public static MultiMemberScalingMode getUnderwaterBreathMode() {
        return underwaterBreathMode;
    }

    public static boolean isUnderwaterBreathCapped1x() {
        return underwaterBreathMode.isCapped1x();
    }

    public static synchronized boolean setUnderwaterBreathMode(MultiMemberScalingMode mode) {
        MultiMemberScalingMode sanitized = mode != null ? mode : MultiMemberScalingMode.CAP_1X;
        if (underwaterBreathMode != sanitized || !Files.exists(getConfigPath())) {
            underwaterBreathMode = sanitized;
            return save();
        }
        return true;
    }

    public static MultiMemberScalingMode getDotDamageMode() {
        return dotDamageMode;
    }

    public static boolean isDotDamageCapped1x() {
        return dotDamageMode.isCapped1x();
    }

    public static synchronized boolean setDotDamageMode(MultiMemberScalingMode mode) {
        MultiMemberScalingMode sanitized = mode != null ? mode : MultiMemberScalingMode.CAP_1X;
        if (dotDamageMode != sanitized || !Files.exists(getConfigPath())) {
            dotDamageMode = sanitized;
            return save();
        }
        return true;
    }

    public static boolean isComunismEnabled() {
        return comunism;
    }

    public static synchronized boolean setComunism(boolean enabled) {
        if (comunism != enabled || !Files.exists(getConfigPath())) {
            comunism = enabled;
            return save();
        }
        return true;
    }

    public static boolean isFriendlyPetsEnabled() {
        return friendlyPets;
    }

    public static synchronized boolean setFriendlyPets(boolean enabled) {
        if (friendlyPets != enabled || !Files.exists(getConfigPath())) {
            friendlyPets = enabled;
            return save();
        }
        return true;
    }

    public static boolean isExcludeCreativeFromDeathEnabled() {
        return excludeCreativeFromDeath;
    }

    public static synchronized boolean setExcludeCreativeFromDeath(boolean enabled) {
        if (excludeCreativeFromDeath != enabled || !Files.exists(getConfigPath())) {
            excludeCreativeFromDeath = enabled;
            return save();
        }
        return true;
    }

    public static boolean isWipeCreativeInventoryOnDeathEnabled() {
        return wipeCreativeInventoryOnDeath;
    }

    public static synchronized boolean setWipeCreativeInventoryOnDeath(boolean enabled) {
        if (wipeCreativeInventoryOnDeath != enabled || !Files.exists(getConfigPath())) {
            wipeCreativeInventoryOnDeath = enabled;
            return save();
        }
        return true;
    }

    public static boolean isRandomDeathLootEnabled() {
        return randomDeathLoot;
    }

    public static synchronized boolean setRandomDeathLoot(boolean enabled) {
        if (randomDeathLoot != enabled || !Files.exists(getConfigPath())) {
            randomDeathLoot = enabled;
            return save();
        }
        return true;
    }

    public static float getHungerWeight() {
        return hungerWeight;
    }

    public static synchronized boolean setHungerWeight(float weight) {
        float sanitized = sanitizeWeight(weight, MIN_HUNGER_WEIGHT, MAX_HUNGER_WEIGHT);
        if (Float.compare(hungerWeight, sanitized) != 0 || !Files.exists(getConfigPath())) {
            hungerWeight = sanitized;
            return save();
        }
        return true;
    }

    public static float getXpWeight() {
        return xpWeight;
    }

    public static synchronized boolean setXpWeight(float weight) {
        float sanitized = sanitizeWeight(weight, MIN_XP_WEIGHT, MAX_XP_WEIGHT);
        if (Float.compare(xpWeight, sanitized) != 0 || !Files.exists(getConfigPath())) {
            xpWeight = sanitized;
            return save();
        }
        return true;
    }

    public static int getAffinityPercent() {
        return affinity;
    }

    public static float getAffinityFactor() {
        return affinity / 100.0F;
    }

    public static double getAffinityFactorDouble() {
        return affinity / 100.0D;
    }

    public static boolean isFullAffinity() {
        return affinity >= MAX_AFFINITY;
    }

    public static synchronized boolean setAffinityPercent(int percent) {
        int sanitized = sanitizeAffinity(percent);
        if (affinity != sanitized || !Files.exists(getConfigPath())) {
            affinity = sanitized;
            return save();
        }
        return true;
    }

    public static boolean isSyncEffectsEnabled() {
        return syncEffects;
    }

    public static synchronized boolean setSyncEffects(boolean enabled) {
        if (syncEffects != enabled || !Files.exists(getConfigPath())) {
            syncEffects = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncAdvancementsEnabled() {
        return syncAdvancements;
    }

    public static synchronized boolean setSyncAdvancements(boolean enabled) {
        if (syncAdvancements != enabled || !Files.exists(getConfigPath())) {
            syncAdvancements = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncHotbarEnabled() {
        return syncHotbar;
    }

    public static synchronized boolean setSyncHotbar(boolean enabled) {
        if (syncHotbar != enabled || !Files.exists(getConfigPath())) {
            syncHotbar = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncHelmetEnabled() {
        return syncHelmet;
    }

    public static synchronized boolean setSyncHelmet(boolean enabled) {
        if (syncHelmet != enabled || !Files.exists(getConfigPath())) {
            syncHelmet = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncChestplateEnabled() {
        return syncChestplate;
    }

    public static synchronized boolean setSyncChestplate(boolean enabled) {
        if (syncChestplate != enabled || !Files.exists(getConfigPath())) {
            syncChestplate = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncLeggingsEnabled() {
        return syncLeggings;
    }

    public static synchronized boolean setSyncLeggings(boolean enabled) {
        if (syncLeggings != enabled || !Files.exists(getConfigPath())) {
            syncLeggings = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncBootsEnabled() {
        return syncBoots;
    }

    public static synchronized boolean setSyncBoots(boolean enabled) {
        if (syncBoots != enabled || !Files.exists(getConfigPath())) {
            syncBoots = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncMainHandEnabled() {
        return syncMainHand;
    }

    public static synchronized boolean setSyncMainHand(boolean enabled) {
        if (syncMainHand != enabled || !Files.exists(getConfigPath())) {
            syncMainHand = enabled;
            return save();
        }
        return true;
    }

    public static boolean isSyncOffHandEnabled() {
        return syncOffHand;
    }

    public static synchronized boolean setSyncOffHand(boolean enabled) {
        if (syncOffHand != enabled || !Files.exists(getConfigPath())) {
            syncOffHand = enabled;
            return save();
        }
        return true;
    }

    public static synchronized SlotSyncState getSlotSyncState() {
        return new SlotSyncState(
            syncHotbar,
            syncMainHand,
            syncOffHand,
            syncHelmet,
            syncChestplate,
            syncLeggings,
            syncBoots
        );
    }

    public static synchronized boolean setBooleanSettingWithTransition(
        String key,
        boolean enabled,
        Consumer<SlotSyncState> beforeCommit
    ) {
        SlotSyncState nextState = getSlotSyncState().withSetting(key, enabled);
        if (beforeCommit != null) {
            beforeCommit.accept(nextState);
        }
        return switch (key) {
            case KEY_SHARED_KNOCKBACK -> setSharedKnockback(enabled);
            case KEY_SHARED_ENDER_PEARL -> setSharedEnderPearl(enabled);
            case KEY_SHARED_PORTALS -> setSharedPortals(enabled);
            case KEY_UNDERWATER_MUFFLE -> setUnderwaterMuffle(enabled);
            case KEY_SHARED_MOB_SOUNDS -> setSharedMobSounds(enabled);
            case KEY_SHARED_CAVE_SOUNDS -> setSharedCaveSounds(enabled);
            case KEY_COMUNISM -> setComunism(enabled);
            case KEY_FRIENDLY_PETS -> setFriendlyPets(enabled);
            case KEY_EXCLUDE_CREATIVE_FROM_DEATH -> setExcludeCreativeFromDeath(enabled);
            case KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH -> setWipeCreativeInventoryOnDeath(enabled);
            case KEY_RANDOM_DEATH_LOOT -> setRandomDeathLoot(enabled);
            case KEY_SYNC_EFFECTS -> setSyncEffects(enabled);
            case KEY_SYNC_ADVANCEMENTS -> setSyncAdvancements(enabled);
            case KEY_SYNC_HOTBAR -> setSyncHotbar(enabled);
            case KEY_SYNC_HELMET -> setSyncHelmet(enabled);
            case KEY_SYNC_CHESTPLATE -> setSyncChestplate(enabled);
            case KEY_SYNC_LEGGINGS -> setSyncLeggings(enabled);
            case KEY_SYNC_BOOTS -> setSyncBoots(enabled);
            case KEY_SYNC_MAIN_HAND -> setSyncMainHand(enabled);
            case KEY_SYNC_OFF_HAND -> setSyncOffHand(enabled);
            default -> throw new IllegalArgumentException("Unknown boolean OIMR setting key: " + key);
        };
    }

    public static boolean isHotbarSlotSynced(int slot) {
        if (slot < 0 || slot >= 9) {
            return true;
        }
        if (syncHotbar) {
            return true;
        }
        return syncMainHand && slot == 0;
    }

    public static boolean isEquipmentSlotSynced(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> syncHelmet;
            case CHEST -> syncChestplate;
            case LEGS -> syncLeggings;
            case FEET -> syncBoots;
            case OFFHAND -> syncOffHand;
            case MAINHAND -> syncHotbar || syncMainHand;
            default -> true;
        };
    }
}
