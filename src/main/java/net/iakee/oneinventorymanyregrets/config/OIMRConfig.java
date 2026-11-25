package net.iakee.oneinventorymanyregrets.config;

import net.fabricmc.loader.api.FabricLoader;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.world.entity.EquipmentSlot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

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
    public static final String KEY_COMUNISM = "comunism";
    public static final String KEY_FRIENDLY_PETS = "friendlyPets";
    public static final String KEY_EXCLUDE_CREATIVE_FROM_DEATH = "excludeCreativeFromDeath";
    public static final String KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH = "wipeCreativeInventoryOnDeath";
    public static final String KEY_RANDOM_DEATH_LOOT = "randomDeathLoot";
    private static final String KEY_HUNGER_WEIGHT = "hungerWeight";
    private static final String KEY_XP_WEIGHT = "xpWeight";
    private static final String KEY_AFFINITY = "affinity";
    public static final String KEY_SYNC_EFFECTS = "syncEffects";
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

    private static volatile boolean sharedKnockback = true;
    private static volatile boolean sharedEnderPearl = true;
    private static volatile boolean sharedPortals = true;
    private static volatile boolean underwaterMuffle = true;
    private static volatile boolean sharedMobSounds = true;
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

    public static synchronized void load() {
        reloadWithSlotTransition(null);
    }

    public static synchronized void reloadWithSlotTransition(Consumer<SlotSyncState> beforeApply) {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            if (beforeApply != null) {
                beforeApply.accept(SlotSyncState.ALL_SYNCED);
            }
            applyDefaults();
            save();
            return;
        }

        Properties props = new Properties();
        boolean resaveNeeded = false;
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            boolean nextSharedKnockback = parseBoolean(props, KEY_SHARED_KNOCKBACK, true);
            boolean nextSharedEnderPearl = parseBoolean(props, KEY_SHARED_ENDER_PEARL, true);
            boolean nextSharedPortals = parseBoolean(props, KEY_SHARED_PORTALS, true);
            boolean nextUnderwaterMuffle = parseBoolean(props, KEY_UNDERWATER_MUFFLE, true);
            boolean nextSharedMobSounds = parseBoolean(props, KEY_SHARED_MOB_SOUNDS, true);
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
            if (rawAffinity != null && !isValidAffinityRaw(rawAffinity)) {
                nextAffinity = MAX_AFFINITY;
                nextInvalidAffinityResetPending = true;
                resaveNeeded = true;
            } else {
                nextAffinity = parseAffinity(rawAffinity != null ? rawAffinity : "100");
                nextInvalidAffinityResetPending = false;
            }
            boolean nextSyncEffects = parseBoolean(props, KEY_SYNC_EFFECTS, true);
            boolean nextSyncHotbar = parseBoolean(props, KEY_SYNC_HOTBAR, true);
            boolean nextSyncHelmet = parseBoolean(props, KEY_SYNC_HELMET, true);
            boolean nextSyncChestplate = parseBoolean(props, KEY_SYNC_CHESTPLATE, true);
            boolean nextSyncLeggings = parseBoolean(props, KEY_SYNC_LEGGINGS, true);
            boolean nextSyncBoots = parseBoolean(props, KEY_SYNC_BOOTS, true);
            boolean nextSyncMainHand = parseBoolean(props, KEY_SYNC_MAIN_HAND, true);
            boolean nextSyncOffHand = parseBoolean(props, KEY_SYNC_OFF_HAND, true);

            if (beforeApply != null) {
                beforeApply.accept(new SlotSyncState(
                    nextSyncHotbar,
                    nextSyncMainHand,
                    nextSyncOffHand,
                    nextSyncHelmet,
                    nextSyncChestplate,
                    nextSyncLeggings,
                    nextSyncBoots
                ));
            }

            sharedKnockback = nextSharedKnockback;
            sharedEnderPearl = nextSharedEnderPearl;
            sharedPortals = nextSharedPortals;
            underwaterMuffle = nextUnderwaterMuffle;
            sharedMobSounds = nextSharedMobSounds;
            comunism = nextComunism;
            friendlyPets = nextFriendlyPets;
            excludeCreativeFromDeath = nextExcludeCreative;
            wipeCreativeInventoryOnDeath = nextWipeCreative;
            randomDeathLoot = nextRandomDeathLoot;
            hungerWeight = nextHungerWeight;
            xpWeight = nextXpWeight;
            affinity = nextAffinity;
            invalidAffinityResetPending = nextInvalidAffinityResetPending;
            syncEffects = nextSyncEffects;
            syncHotbar = nextSyncHotbar;
            syncHelmet = nextSyncHelmet;
            syncChestplate = nextSyncChestplate;
            syncLeggings = nextSyncLeggings;
            syncBoots = nextSyncBoots;
            syncMainHand = nextSyncMainHand;
            syncOffHand = nextSyncOffHand;
        } catch (IOException e) {
            OIMRMod.LOGGER.warn("Failed to load config at {}, using defaults", path, e);
        }
        if (resaveNeeded) {
            save();
        }
    }

    private static void applyDefaults() {
        sharedKnockback = true;
        sharedEnderPearl = true;
        sharedPortals = true;
        underwaterMuffle = true;
        sharedMobSounds = true;
        comunism = false;
        friendlyPets = true;
        excludeCreativeFromDeath = true;
        wipeCreativeInventoryOnDeath = true;
        randomDeathLoot = true;
        hungerWeight = 1.0F;
        xpWeight = 1.0F;
        affinity = MAX_AFFINITY;
        syncEffects = true;
        syncHotbar = true;
        syncHelmet = true;
        syncChestplate = true;
        syncLeggings = true;
        syncBoots = true;
        syncMainHand = true;
        syncOffHand = true;
    }

    public static synchronized void resetToDefaults() {
        applyDefaults();
        save();
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String trimmed = raw.trim();
        if (defaultValue) {
            return !"false".equalsIgnoreCase(trimmed);
        }
        return "true".equalsIgnoreCase(trimmed);
    }

    private static float parseWeight(String raw, float min, float max) {
        try {
            return sanitizeWeight(Float.parseFloat(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return 1.0F;
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
        try {
            return sanitizeAffinity((int) Math.round(Double.parseDouble(raw.trim())));
        } catch (NumberFormatException ignored) {
            return MAX_AFFINITY;
        }
    }

    private static int sanitizeAffinity(int value) {
        return Math.max(MIN_AFFINITY, Math.min(MAX_AFFINITY, value));
    }

    public static synchronized boolean consumeInvalidAffinityResetPending() {
        boolean pending = invalidAffinityResetPending;
        invalidAffinityResetPending = false;
        return pending;
    }

    public static synchronized void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Properties props = new Properties();
            props.setProperty(KEY_SHARED_KNOCKBACK, Boolean.toString(sharedKnockback));
            props.setProperty(KEY_SHARED_ENDER_PEARL, Boolean.toString(sharedEnderPearl));
            props.setProperty(KEY_SHARED_PORTALS, Boolean.toString(sharedPortals));
            props.setProperty(KEY_UNDERWATER_MUFFLE, Boolean.toString(underwaterMuffle));
            props.setProperty(KEY_SHARED_MOB_SOUNDS, Boolean.toString(sharedMobSounds));
            props.setProperty(KEY_COMUNISM, Boolean.toString(comunism));
            props.setProperty(KEY_FRIENDLY_PETS, Boolean.toString(friendlyPets));
            props.setProperty(KEY_EXCLUDE_CREATIVE_FROM_DEATH, Boolean.toString(excludeCreativeFromDeath));
            props.setProperty(KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, Boolean.toString(wipeCreativeInventoryOnDeath));
            props.setProperty(KEY_RANDOM_DEATH_LOOT, Boolean.toString(randomDeathLoot));
            props.setProperty(KEY_HUNGER_WEIGHT, Float.toString(hungerWeight));
            props.setProperty(KEY_XP_WEIGHT, Float.toString(xpWeight));
            props.setProperty(KEY_AFFINITY, Integer.toString(affinity));
            props.setProperty(KEY_SYNC_EFFECTS, Boolean.toString(syncEffects));
            props.setProperty(KEY_SYNC_HOTBAR, Boolean.toString(syncHotbar));
            props.setProperty(KEY_SYNC_HELMET, Boolean.toString(syncHelmet));
            props.setProperty(KEY_SYNC_CHESTPLATE, Boolean.toString(syncChestplate));
            props.setProperty(KEY_SYNC_LEGGINGS, Boolean.toString(syncLeggings));
            props.setProperty(KEY_SYNC_BOOTS, Boolean.toString(syncBoots));
            props.setProperty(KEY_SYNC_MAIN_HAND, Boolean.toString(syncMainHand));
            props.setProperty(KEY_SYNC_OFF_HAND, Boolean.toString(syncOffHand));
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(
                    out,
                    "One Inventory, Many Regrets settings\n"
                        + "Disabling sync for any inventory/equipment slot (syncHotbar, syncMainHand, syncOffHand, syncHelmet, syncChestplate, syncLeggings, syncBoots) drops currently shared items in-world before switching those slots to per-player storage.\n"
                        + "affinity must be between 0 and 100 (values outside 0..100 are unsupported and reset to 100)."
                );
            }
        } catch (IOException e) {
            OIMRMod.LOGGER.warn("Failed to save config at {}", path, e);
        }
    }

    public static boolean isSharedKnockbackEnabled() {
        return sharedKnockback;
    }

    public static synchronized void setSharedKnockback(boolean enabled) {
        if (sharedKnockback != enabled || !Files.exists(getConfigPath())) {
            sharedKnockback = enabled;
            save();
        }
    }

    public static boolean isSharedEnderPearlEnabled() {
        return sharedEnderPearl;
    }

    public static synchronized void setSharedEnderPearl(boolean enabled) {
        if (sharedEnderPearl != enabled || !Files.exists(getConfigPath())) {
            sharedEnderPearl = enabled;
            save();
        }
    }

    public static boolean isSharedPortalsEnabled() {
        return sharedPortals;
    }

    public static synchronized void setSharedPortals(boolean enabled) {
        if (sharedPortals != enabled || !Files.exists(getConfigPath())) {
            sharedPortals = enabled;
            save();
        }
    }

    public static boolean isUnderwaterMuffleEnabled() {
        return underwaterMuffle;
    }

    public static synchronized void setUnderwaterMuffle(boolean enabled) {
        if (underwaterMuffle != enabled || !Files.exists(getConfigPath())) {
            underwaterMuffle = enabled;
            save();
        }
    }

    public static boolean isSharedMobSoundsEnabled() {
        return sharedMobSounds;
    }

    public static synchronized void setSharedMobSounds(boolean enabled) {
        if (sharedMobSounds != enabled || !Files.exists(getConfigPath())) {
            sharedMobSounds = enabled;
            save();
        }
    }

    public static boolean isComunismEnabled() {
        return comunism;
    }

    public static synchronized void setComunism(boolean enabled) {
        if (comunism != enabled || !Files.exists(getConfigPath())) {
            comunism = enabled;
            save();
        }
    }

    public static boolean isFriendlyPetsEnabled() {
        return friendlyPets;
    }

    public static synchronized void setFriendlyPets(boolean enabled) {
        if (friendlyPets != enabled || !Files.exists(getConfigPath())) {
            friendlyPets = enabled;
            save();
        }
    }

    public static boolean isExcludeCreativeFromDeathEnabled() {
        return excludeCreativeFromDeath;
    }

    public static synchronized void setExcludeCreativeFromDeath(boolean enabled) {
        if (excludeCreativeFromDeath != enabled || !Files.exists(getConfigPath())) {
            excludeCreativeFromDeath = enabled;
            save();
        }
    }

    public static boolean isWipeCreativeInventoryOnDeathEnabled() {
        return wipeCreativeInventoryOnDeath;
    }

    public static synchronized void setWipeCreativeInventoryOnDeath(boolean enabled) {
        if (wipeCreativeInventoryOnDeath != enabled || !Files.exists(getConfigPath())) {
            wipeCreativeInventoryOnDeath = enabled;
            save();
        }
    }

    public static boolean isRandomDeathLootEnabled() {
        return randomDeathLoot;
    }

    public static synchronized void setRandomDeathLoot(boolean enabled) {
        if (randomDeathLoot != enabled || !Files.exists(getConfigPath())) {
            randomDeathLoot = enabled;
            save();
        }
    }

    public static float getHungerWeight() {
        return hungerWeight;
    }

    public static synchronized void setHungerWeight(float weight) {
        float sanitized = sanitizeWeight(weight, MIN_HUNGER_WEIGHT, MAX_HUNGER_WEIGHT);
        if (Float.compare(hungerWeight, sanitized) != 0 || !Files.exists(getConfigPath())) {
            hungerWeight = sanitized;
            save();
        }
    }

    public static float getXpWeight() {
        return xpWeight;
    }

    public static synchronized void setXpWeight(float weight) {
        float sanitized = sanitizeWeight(weight, MIN_XP_WEIGHT, MAX_XP_WEIGHT);
        if (Float.compare(xpWeight, sanitized) != 0 || !Files.exists(getConfigPath())) {
            xpWeight = sanitized;
            save();
        }
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

    public static synchronized void setAffinityPercent(int percent) {
        int sanitized = sanitizeAffinity(percent);
        if (affinity != sanitized || !Files.exists(getConfigPath())) {
            affinity = sanitized;
            save();
        }
    }

    public static boolean isSyncEffectsEnabled() {
        return syncEffects;
    }

    public static synchronized void setSyncEffects(boolean enabled) {
        if (syncEffects != enabled || !Files.exists(getConfigPath())) {
            syncEffects = enabled;
            save();
        }
    }

    public static boolean isSyncHotbarEnabled() {
        return syncHotbar;
    }

    public static synchronized void setSyncHotbar(boolean enabled) {
        if (syncHotbar != enabled || !Files.exists(getConfigPath())) {
            syncHotbar = enabled;
            save();
        }
    }

    public static boolean isSyncHelmetEnabled() {
        return syncHelmet;
    }

    public static synchronized void setSyncHelmet(boolean enabled) {
        if (syncHelmet != enabled || !Files.exists(getConfigPath())) {
            syncHelmet = enabled;
            save();
        }
    }

    public static boolean isSyncChestplateEnabled() {
        return syncChestplate;
    }

    public static synchronized void setSyncChestplate(boolean enabled) {
        if (syncChestplate != enabled || !Files.exists(getConfigPath())) {
            syncChestplate = enabled;
            save();
        }
    }

    public static boolean isSyncLeggingsEnabled() {
        return syncLeggings;
    }

    public static synchronized void setSyncLeggings(boolean enabled) {
        if (syncLeggings != enabled || !Files.exists(getConfigPath())) {
            syncLeggings = enabled;
            save();
        }
    }

    public static boolean isSyncBootsEnabled() {
        return syncBoots;
    }

    public static synchronized void setSyncBoots(boolean enabled) {
        if (syncBoots != enabled || !Files.exists(getConfigPath())) {
            syncBoots = enabled;
            save();
        }
    }

    public static boolean isSyncMainHandEnabled() {
        return syncMainHand;
    }

    public static synchronized void setSyncMainHand(boolean enabled) {
        if (syncMainHand != enabled || !Files.exists(getConfigPath())) {
            syncMainHand = enabled;
            save();
        }
    }

    public static boolean isSyncOffHandEnabled() {
        return syncOffHand;
    }

    public static synchronized void setSyncOffHand(boolean enabled) {
        if (syncOffHand != enabled || !Files.exists(getConfigPath())) {
            syncOffHand = enabled;
            save();
        }
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

    public static synchronized void setBooleanSettingWithTransition(
        String key,
        boolean enabled,
        Consumer<SlotSyncState> beforeCommit
    ) {
        SlotSyncState nextState = getSlotSyncState().withSetting(key, enabled);
        if (beforeCommit != null) {
            beforeCommit.accept(nextState);
        }
        switch (key) {
            case KEY_SHARED_KNOCKBACK -> setSharedKnockback(enabled);
            case KEY_SHARED_ENDER_PEARL -> setSharedEnderPearl(enabled);
            case KEY_SHARED_PORTALS -> setSharedPortals(enabled);
            case KEY_UNDERWATER_MUFFLE -> setUnderwaterMuffle(enabled);
            case KEY_SHARED_MOB_SOUNDS -> setSharedMobSounds(enabled);
            case KEY_COMUNISM -> setComunism(enabled);
            case KEY_FRIENDLY_PETS -> setFriendlyPets(enabled);
            case KEY_EXCLUDE_CREATIVE_FROM_DEATH -> setExcludeCreativeFromDeath(enabled);
            case KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH -> setWipeCreativeInventoryOnDeath(enabled);
            case KEY_RANDOM_DEATH_LOOT -> setRandomDeathLoot(enabled);
            case KEY_SYNC_EFFECTS -> setSyncEffects(enabled);
            case KEY_SYNC_HOTBAR -> setSyncHotbar(enabled);
            case KEY_SYNC_HELMET -> setSyncHelmet(enabled);
            case KEY_SYNC_CHESTPLATE -> setSyncChestplate(enabled);
            case KEY_SYNC_LEGGINGS -> setSyncLeggings(enabled);
            case KEY_SYNC_BOOTS -> setSyncBoots(enabled);
            case KEY_SYNC_MAIN_HAND -> setSyncMainHand(enabled);
            case KEY_SYNC_OFF_HAND -> setSyncOffHand(enabled);
            default -> throw new IllegalArgumentException("Unknown boolean OIMR setting key: " + key);
        }
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
