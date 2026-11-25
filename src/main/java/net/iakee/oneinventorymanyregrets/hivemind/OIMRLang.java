package net.iakee.oneinventorymanyregrets.hivemind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.commands.CommandSourceStack;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads bundled mod translation tables so death, command, and system messages always carry
 * fallback text for vanilla LAN/multiplayer clients that do not have the mod installed.
 */
public final class OIMRLang {
    private static final String PREFIX_DEATH = "death.oimr.";
    private static final String PREFIX_OIMR = "oimr.";

    private static final Map<String, String> PREFIX_FALLBACKS = Map.ofEntries(
        Map.entry("ar", "ar_sa"),
        Map.entry("be", "be_by"),
        Map.entry("de", "de_de"),
        Map.entry("en", "en_us"),
        Map.entry("es", "es_es"),
        Map.entry("fil", "fil_ph"),
        Map.entry("fr", "fr_fr"),
        Map.entry("id", "id_id"),
        Map.entry("it", "it_it"),
        Map.entry("ja", "ja_jp"),
        Map.entry("ko", "ko_kr"),
        Map.entry("ms", "ms_my"),
        Map.entry("nb", "no_no"),
        Map.entry("nl", "nl_nl"),
        Map.entry("nn", "nn_no"),
        Map.entry("no", "no_no"),
        Map.entry("pl", "pl_pl"),
        Map.entry("pt", "pt_br"),
        Map.entry("ru", "ru_ru"),
        Map.entry("sr", "sr_sp"),
        Map.entry("sv", "sv_se"),
        Map.entry("tl", "tl_ph"),
        Map.entry("tr", "tr_tr"),
        Map.entry("uk", "uk_ua"),
        Map.entry("vi", "vi_vn"),
        Map.entry("zh", "zh_cn")
    );

    private static final ConcurrentMap<String, Map<String, String>> LANG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> EN_US = getOrLoadLang("en_us");

    private OIMRLang() {
    }

    private static Map<String, String> getOrLoadLang(String langCode) {
        return LANG_CACHE.computeIfAbsent(langCode, OIMRLang::loadLang);
    }

    private static Map<String, String> loadLang(String langCode) {
        String resourcePath = "/assets/" + OIMRMod.MOD_ID + "/lang/" + langCode + ".json";
        try (InputStream stream = OIMRLang.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                if ("en_us".equals(langCode)) {
                    OIMRMod.LOGGER.warn("Missing bundled base language file: {}", resourcePath);
                }
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

    private static @Nullable String normalizeLangCode(@Nullable String clientLanguage) {
        if (clientLanguage == null || clientLanguage.isBlank()) {
            return null;
        }
        String normalized = clientLanguage.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (!valid) {
                return null;
            }
        }
        return normalized;
    }

    public static @Nullable String getFallback(String key, @Nullable String clientLanguage) {
        String normalized = normalizeLangCode(clientLanguage);
        if (normalized != null) {
            String exact = getOrLoadLang(normalized).get(key);
            if (exact != null) {
                return exact;
            }
            int sep = normalized.indexOf('_');
            String prefix = sep > 0 ? normalized.substring(0, sep) : normalized;
            String fallbackLang = PREFIX_FALLBACKS.get(prefix);
            if (fallbackLang != null && !fallbackLang.equals(normalized)) {
                String prefixed = getOrLoadLang(fallbackLang).get(key);
                if (prefixed != null) {
                    return prefixed;
                }
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

    public static Component localizeForSource(Component component, CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return localizeForPlayer(component, player);
        }
        return component;
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
