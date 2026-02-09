package com.islandium.edit.math;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gere les overrides de rotation pour les blocs lors des flips/rotations.
 * Charge un fichier JSON qui definit des regles de transformation custom
 * par pattern de nom de bloc.
 *
 * Fichier: mods/islandium-edit/rotation-overrides.json
 */
public class RotationOverrides {

    private static final Logger LOGGER = Logger.getLogger("RotationOverrides");
    private static final Gson GSON = new Gson();

    private static volatile RotationOverrides instance;

    private final List<OverrideRule> rules = new ArrayList<>();
    private boolean useNativeFlip = true; // Utiliser BlockFlipType API au lieu des overrides manuels

    /**
     * Une regle d'override de rotation.
     */
    public static class OverrideRule {
        final String pattern;
        final String comment;
        final boolean replaceStandard; // true = REMPLACE le swap standard, false = ajoute en plus
        final List<int[]> flipZSwapYaw;
        final List<int[]> flipXSwapYaw;
        final List<int[]> flipYSwapPitch;

        OverrideRule(String pattern, String comment, boolean replaceStandard,
                     List<int[]> flipZSwapYaw, List<int[]> flipXSwapYaw,
                     List<int[]> flipYSwapPitch) {
            this.pattern = pattern;
            this.comment = comment;
            this.replaceStandard = replaceStandard;
            this.flipZSwapYaw = flipZSwapYaw;
            this.flipXSwapYaw = flipXSwapYaw;
            this.flipYSwapPitch = flipYSwapPitch;
        }

        public boolean matches(@NotNull String blockType) {
            return blockType.contains(pattern);
        }
    }

    private RotationOverrides() {}

    /**
     * Initialise et charge les overrides depuis le fichier JSON.
     * Si le fichier n'existe pas, copie le defaut depuis les resources.
     */
    public static void init(@NotNull Path modsDir) {
        instance = new RotationOverrides();
        Path configDir = modsDir.resolve("islandium-edit");
        Path configFile = configDir.resolve("rotation-overrides.json");

        try {
            // Creer le dossier si necessaire
            Files.createDirectories(configDir);

            // Copier le fichier par defaut si absent
            if (!Files.exists(configFile)) {
                try (InputStream in = RotationOverrides.class.getClassLoader()
                        .getResourceAsStream("rotation-overrides.json")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                        LOGGER.info("[RotationOverrides] Default config copied to " + configFile);
                    } else {
                        LOGGER.warning("[RotationOverrides] Default config not found in resources");
                        return;
                    }
                }
            }

            // Charger le fichier
            instance.load(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[RotationOverrides] Error loading config: " + e.getMessage(), e);
        }
    }

    /**
     * Charge les regles depuis un fichier JSON.
     */
    private void load(@NotNull Path configFile) throws IOException {
        rules.clear();
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        JsonObject root = GSON.fromJson(content, JsonObject.class);

        // Lire le toggle use_native_flip (true par defaut)
        if (root.has("use_native_flip")) {
            useNativeFlip = root.get("use_native_flip").getAsBoolean();
        }
        LOGGER.info("[RotationOverrides] use_native_flip = " + useNativeFlip);

        JsonArray overrides = root.getAsJsonArray("overrides");
        if (overrides == null) return;

        for (JsonElement elem : overrides) {
            JsonObject obj = elem.getAsJsonObject();
            String pattern = obj.get("pattern").getAsString();
            String comment = obj.has("comment") ? obj.get("comment").getAsString() : "";

            boolean replaceStandard = obj.has("replace_standard") && obj.get("replace_standard").getAsBoolean();
            List<int[]> flipZSwapYaw = parseSwapPairs(obj, "flipZ_swap_yaw");
            List<int[]> flipXSwapYaw = parseSwapPairs(obj, "flipX_swap_yaw");
            List<int[]> flipYSwapPitch = parseSwapPairs(obj, "flipY_swap_pitch");

            rules.add(new OverrideRule(pattern, comment, replaceStandard, flipZSwapYaw, flipXSwapYaw, flipYSwapPitch));
            LOGGER.info("[RotationOverrides] Loaded rule: '" + pattern + "' (" + comment + ")");
        }

        LOGGER.info("[RotationOverrides] Loaded " + rules.size() + " override rules");
    }

    private List<int[]> parseSwapPairs(@NotNull JsonObject obj, @NotNull String key) {
        List<int[]> pairs = new ArrayList<>();
        if (!obj.has(key)) return pairs;

        JsonArray arr = obj.getAsJsonArray(key);
        for (JsonElement elem : arr) {
            JsonArray pair = elem.getAsJsonArray();
            if (pair.size() == 2) {
                pairs.add(new int[]{pair.get(0).getAsInt(), pair.get(1).getAsInt()});
            }
        }
        return pairs;
    }

    /**
     * Obtient l'instance (peut etre null si non initialisee).
     */
    @Nullable
    public static RotationOverrides get() {
        return instance;
    }

    /**
     * Cherche une regle applicable pour un type de bloc.
     */
    @Nullable
    public OverrideRule findRule(@NotNull String blockType) {
        for (OverrideRule rule : rules) {
            if (rule.matches(blockType)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Verifie si un bloc a un override avec replace_standard=true pour flipZ.
     * Si oui, le swap standard (0<->2) doit etre IGNORE.
     */
    public boolean shouldReplaceStandardFlipZ(@NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        return rule != null && rule.replaceStandard && !rule.flipZSwapYaw.isEmpty();
    }

    /**
     * Verifie si un bloc a un override avec replace_standard=true pour flipX.
     * Si oui, le swap standard (1<->3) doit etre IGNORE.
     */
    public boolean shouldReplaceStandardFlipX(@NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        return rule != null && rule.replaceStandard && !rule.flipXSwapYaw.isEmpty();
    }

    /**
     * Verifie si un bloc a un override avec replace_standard=true pour flipY.
     * Si oui, le swap standard de pitch doit etre IGNORE.
     */
    public boolean shouldReplaceStandardFlipY(@NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        return rule != null && rule.replaceStandard && !rule.flipYSwapPitch.isEmpty();
    }

    /**
     * Applique les overrides de yaw pour un flip Z.
     * @return le yaw modifie, ou le yaw original si pas d'override
     */
    public int applyFlipZYaw(int yaw, @NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        if (rule == null) return yaw;
        return applySwaps(yaw, rule.flipZSwapYaw);
    }

    /**
     * Applique les overrides de yaw pour un flip X.
     * @return le yaw modifie, ou le yaw original si pas d'override
     */
    public int applyFlipXYaw(int yaw, @NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        if (rule == null) return yaw;
        return applySwaps(yaw, rule.flipXSwapYaw);
    }

    /**
     * Applique les overrides de pitch pour un flip Y.
     * @return le pitch modifie, ou le pitch original si pas d'override
     */
    public int applyFlipYPitch(int pitch, @NotNull String blockType) {
        OverrideRule rule = findRule(blockType);
        if (rule == null) return pitch;
        return applySwaps(pitch, rule.flipYSwapPitch);
    }

    private int applySwaps(int value, @NotNull List<int[]> pairs) {
        for (int[] pair : pairs) {
            if (value == pair[0]) return pair[1];
            if (value == pair[1]) return pair[0];
        }
        return value;
    }

    /**
     * Recharge les overrides depuis le fichier (pour reload a chaud).
     */
    public void reload(@NotNull Path modsDir) {
        Path configFile = modsDir.resolve("islandium-edit").resolve("rotation-overrides.json");
        try {
            load(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[RotationOverrides] Error reloading: " + e.getMessage(), e);
        }
    }

    /**
     * @return true si on utilise l'API native BlockFlipType.flipYaw() pour les flips.
     * Quand actif, les overrides manuels sont ignores sauf si l'API ne fournit pas de resultat.
     */
    public boolean isUseNativeFlip() {
        return useNativeFlip;
    }

    /**
     * Active/desactive l'utilisation de l'API native pour les flips.
     */
    public void setUseNativeFlip(boolean useNativeFlip) {
        this.useNativeFlip = useNativeFlip;
        LOGGER.info("[RotationOverrides] use_native_flip = " + useNativeFlip);
    }
}
