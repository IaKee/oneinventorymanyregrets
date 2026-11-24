package net.iakee.oneinventorymanyregrets.hivemind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads bundled mod translation tables so death and system messages always carry
 * fallback text for vanilla LAN/multiplayer clients that do not have the mod installed.
 */
public final class OIMRLang {
    private static final String PREFIX_DEATH = "death.oimr.";
    private static final String PREFIX_OIMR = "oimr.";

    private static final Map<String, String> EN_US = loadLang("en_us");
    private static final Map<String, String> PT_BR = loadLang("pt_br");

    private OIMRLang() {
    }

    private static Map<String, String> loadLang(String langCode) {
        String resourcePath = "/assets/" + OIMRMod.MOD_ID + "/lang/" + langCode + ".json";
        try (InputStream stream = OIMRLang.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                OIMRMod.LOGGER.warn("Missing bundled language file: {}", resourcePath);
                return Map.of();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> map = new HashMap<>(json.size());
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return Collections.unmodifiableMap(map);
            }
        } catch (Exception e) {
            OIMRMod.LOGGER.warn("Failed to load bundled language file {}", resourcePath, e);
            return Map.of();
        }
    }

    public static @Nullable String getFallback(String key, @Nullable String clientLanguage) {
        if (clientLanguage != null && clientLanguage.toLowerCase(Locale.ROOT).startsWith("pt")) {
            String pt = PT_BR.get(key);
            if (pt != null) {
                return pt;
            }
        }
        return EN_US.get(key);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatableWithFallback(key, EN_US.get(key), args);
    }

    public static boolean isOimrKey(String key) {
        return key.startsWith(PREFIX_DEATH) || key.startsWith(PREFIX_OIMR);
    }

    public static boolean containsOimrKey(@Nullable Component component) {
        if (component == null) {
            return false;
        }
        if (component.getContents() instanceof TranslatableContents tc) {
            if (isOimrKey(tc.getKey())) {
                return true;
            }
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component argComp && containsOimrKey(argComp)) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (containsOimrKey(sibling)) {
                return true;
            }
        }
        return false;
    }

    public static Component localizeForPlayer(Component component, ServerPlayer recipient) {
        String language = recipient.clientInformation().language();
        return localizeComponent(component, language);
    }

    private static Component localizeComponent(Component component, @Nullable String language) {
        MutableComponent copy;
        if (component.getContents() instanceof TranslatableContents tc) {
            Object[] origArgs = tc.getArgs();
            Object[] newArgs = new Object[origArgs.length];
            for (int i = 0; i < origArgs.length; i++) {
                if (origArgs[i] instanceof Component argComp) {
                    newArgs[i] = localizeComponent(argComp, language);
                } else {
                    newArgs[i] = origArgs[i];
                }
            }
            String key = tc.getKey();
            String fallback = isOimrKey(key) ? getFallback(key, language) : tc.getFallback();
            if (fallback == null && isOimrKey(key)) {
                fallback = tc.getFallback();
            }
            copy = fallback != null
                ? Component.translatableWithFallback(key, fallback, newArgs)
                : Component.translatable(key, newArgs);
            copy.setStyle(component.getStyle());
        } else {
            copy = component.plainCopy().setStyle(component.getStyle());
        }

        for (Component sibling : component.getSiblings()) {
            copy.append(localizeComponent(sibling, language));
        }
        return copy;
    }
}
