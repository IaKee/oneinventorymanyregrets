package net.iakee.oneinventorymanyregrets.config;

import net.fabricmc.loader.api.FabricLoader;
import net.iakee.oneinventorymanyregrets.OIMRMod;

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

    public static final float MIN_HUNGER_WEIGHT = 0.0F;
    public static final float MAX_HUNGER_WEIGHT = 100.0F;

    private static volatile boolean sharedKnockback = true;
    private static volatile boolean comunism = false;
    private static volatile boolean friendlyPets = true;
    private static volatile boolean excludeCreativeFromDeath = true;
    private static volatile boolean wipeCreativeInventoryOnDeath = true;
    private static volatile float hungerWeight = 1.0F;

    private OIMRConfig() {
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            sharedKnockback = true;
            comunism = false;
            friendlyPets = true;
            excludeCreativeFromDeath = true;
            wipeCreativeInventoryOnDeath = true;
            hungerWeight = 1.0F;
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            sharedKnockback = !"false".equalsIgnoreCase(props.getProperty(KEY_SHARED_KNOCKBACK, "true").trim());
            comunism = "true".equalsIgnoreCase(props.getProperty(KEY_COMUNISM, "false").trim());
            friendlyPets = !"false".equalsIgnoreCase(props.getProperty(KEY_FRIENDLY_PETS, "true").trim());
            excludeCreativeFromDeath = !"false".equalsIgnoreCase(props.getProperty(KEY_EXCLUDE_CREATIVE_FROM_DEATH, "true").trim());
            wipeCreativeInventoryOnDeath = !"false".equalsIgnoreCase(props.getProperty(KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH, "true").trim());
            hungerWeight = parseHungerWeight(props.getProperty(KEY_HUNGER_WEIGHT, "1.0"));
        } catch (IOException e) {
            OIMRMod.LOGGER.warn("Failed to load config at {}, using defaults", path, e);
        }
    }

    private static float parseHungerWeight(String raw) {
        try {
            return sanitizeHungerWeight(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 1.0F;
        }
    }

    private static float sanitizeHungerWeight(float weight) {
        if (!Float.isFinite(weight)) {
            return 1.0F;
        }
        return Math.max(MIN_HUNGER_WEIGHT, Math.min(MAX_HUNGER_WEIGHT, weight));
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
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "One Inventory, Many Regrets settings");
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
        float sanitized = sanitizeHungerWeight(weight);
        if (Float.compare(hungerWeight, sanitized) != 0 || !Files.exists(getConfigPath())) {
            hungerWeight = sanitized;
            save();
        }
    }
}
