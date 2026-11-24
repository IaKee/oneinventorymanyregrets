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

/**
 * Persistent configuration for One Inventory, Many Regrets.
 * Editable in-game only by the Singleplayer/LAN host or via dedicated server console.
 */
public final class OIMRConfig {
    private static final String FILE_NAME = OIMRMod.MOD_ID + ".properties";
    private static final String KEY_SHARED_KNOCKBACK = "sharedKnockback";
    private static final String KEY_COMUNISM = "comunism";
    private static final String KEY_FRIENDLY_PETS = "friendlyPets";
    private static final String KEY_EXCLUDE_CREATIVE_FROM_DEATH = "excludeCreativeFromDeath";
    private static final String KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH = "wipeCreativeInventoryOnDeath";
    private static final String KEY_HUNGER_WEIGHT = "hungerWeight";
    private static final String KEY_XP_WEIGHT = "xpWeight";
    private static final String KEY_AFFINITY = "affinity";
    private static final String KEY_SYNC_HOTBAR = "syncHotbar";
    private static final String KEY_SYNC_HELMET = "syncHelmet";
    private static final String KEY_SYNC_CHESTPLATE = "syncChestplate";
    private static final String KEY_SYNC_LEGGINGS = "syncLeggings";
    private static final String KEY_SYNC_BOOTS = "syncBoots";
    private static final String KEY_SYNC_MAIN_HAND = "syncMainHand";
    private static final String KEY_SYNC_OFF_HAND = "syncOffHand";

    public static final float MIN_HUNGER_WEIGHT = 0.0F;
    public static final float MAX_HUNGER_WEIGHT = 100.0F;
    public static final float MIN_XP_WEIGHT = 0.0F;
    public static final float MAX_XP_WEIGHT = 100.0F;
    public static final int MIN_AFFINITY = 0;
    public static final int MAX_AFFINITY = 100;

    private static volatile boolean sharedKnockback = true;
    private static volatile boolean comunism = false;
    private static volatile boolean friendlyPets = true;
    private static volatile boolean excludeCreativeFromDeath = true;
    private static volatile boolean wipeCreativeInventoryOnDeath = true;
    private static volatile float hungerWeight = 1.0F;
    private static volatile float xpWeight = 1.0F;
    private static volatile int affinity = MAX_AFFINITY;
    private static volatile boolean invalidAffinityResetPending = false;
    private static volatile boolean syncHotbar = true;
    private static volatile boolean syncHelmet = true;
    private static volatile boolean syncChestplate = true;
    private static volatile boolean syncLeggings = true;
    private static volatile boolean syncBoots = true;
    private static volatile boolean syncMainHand = true;
    private static volatile boolean syncOffHand = true;

    private OIMRConfig() {
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            applyDefaults();
            save();
            return;
        }

        Properties props = new Properties();
        boolean resaveNeeded = false;
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            sharedKnockback = parseBoolean(props, KEY_SHARED_KNOCKBACK, true);
            comunism = parseBoolean(props, KEY_COMUNISM, false);
            friendlyPets = parseBoolean(props, KEY_FRIENDLY_PETS, true);
            excludeCreativeFromDeath = parseBoolean(props, KEY_EXCLUDE_CREATIVE_FROM_DEATH, true);
            wipeCreativeInventoryOnDeath = parseBoolean(props, KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, true);
            hungerWeight = parseWeight(props.getProperty(KEY_HUNGER_WEIGHT, "1.0"), MIN_HUNGER_WEIGHT, MAX_HUNGER_WEIGHT);
            xpWeight = parseWeight(props.getProperty(KEY_XP_WEIGHT, "1.0"), MIN_XP_WEIGHT, MAX_XP_WEIGHT);
            String rawAffinity = props.getProperty(KEY_AFFINITY);
            if (rawAffinity != null && !isValidAffinityRaw(rawAffinity)) {
                affinity = MAX_AFFINITY;
                invalidAffinityResetPending = true;
                resaveNeeded = true;
            } else {
                affinity = parseAffinity(rawAffinity != null ? rawAffinity : "100");
                invalidAffinityResetPending = false;
            }
            syncHotbar = parseBoolean(props, KEY_SYNC_HOTBAR, true);
            syncHelmet = parseBoolean(props, KEY_SYNC_HELMET, true);
            syncChestplate = parseBoolean(props, KEY_SYNC_CHESTPLATE, true);
            syncLeggings = parseBoolean(props, KEY_SYNC_LEGGINGS, true);
            syncBoots = parseBoolean(props, KEY_SYNC_BOOTS, true);
            syncMainHand = parseBoolean(props, KEY_SYNC_MAIN_HAND, true);
            syncOffHand = parseBoolean(props, KEY_SYNC_OFF_HAND, true);
        } catch (IOException e) {
            OIMRMod.LOGGER.warn("Failed to load config at {}, using defaults", path, e);
        }
        if (resaveNeeded) {
            save();
        }
    }

    private static void applyDefaults() {
        sharedKnockback = true;
        comunism = false;
        friendlyPets = true;
        excludeCreativeFromDeath = true;
        wipeCreativeInventoryOnDeath = true;
        hungerWeight = 1.0F;
        xpWeight = 1.0F;
        affinity = MAX_AFFINITY;
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
            props.setProperty(KEY_COMUNISM, Boolean.toString(comunism));
            props.setProperty(KEY_FRIENDLY_PETS, Boolean.toString(friendlyPets));
            props.setProperty(KEY_EXCLUDE_CREATIVE_FROM_DEATH, Boolean.toString(excludeCreativeFromDeath));
            props.setProperty(KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, Boolean.toString(wipeCreativeInventoryOnDeath));
            props.setProperty(KEY_HUNGER_WEIGHT, Float.toString(hungerWeight));
            props.setProperty(KEY_XP_WEIGHT, Float.toString(xpWeight));
            props.setProperty(KEY_AFFINITY, Integer.toString(affinity));
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
                        + "WARNING: Disabling sync for any inventory/equipment slot (syncHotbar, syncMainHand, syncOffHand, syncHelmet, syncChestplate, syncLeggings, syncBoots) can actively cause item duplication. This is intended behavior.\n"
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
