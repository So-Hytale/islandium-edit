package com.islandium.edit.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Représente un pattern de blocs avec pourcentages.
 * Supporte les formats:
 * - "stone" (100% stone)
 * - "20%grass,80%stone" (20% grass, 80% stone)
 * - "50%dirt,25%stone,25%gravel" (multi-blocs)
 * - "hand" ou "*" (bloc en main)
 */
public class BlockPattern {

    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+)%([^,]+)");
    private static final Random RANDOM = new Random();

    private final List<WeightedBlock> blocks;
    private final int totalWeight;

    private BlockPattern(List<WeightedBlock> blocks) {
        this.blocks = blocks;
        this.totalWeight = blocks.stream().mapToInt(b -> b.weight).sum();
    }

    /**
     * Parse un pattern de blocs.
     * @param input Le pattern (ex: "20%grass,80%stone" ou "stone")
     * @return Le BlockPattern parsé, ou null si invalide
     */
    @Nullable
    public static BlockPattern parse(@NotNull String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();
        List<WeightedBlock> blocks = new ArrayList<>();

        // Vérifier si c'est un pattern avec pourcentages
        if (trimmed.contains("%")) {
            Matcher matcher = PERCENT_PATTERN.matcher(trimmed);
            while (matcher.find()) {
                try {
                    int percent = Integer.parseInt(matcher.group(1));
                    String blockType = matcher.group(2).trim();
                    if (percent > 0 && !blockType.isEmpty()) {
                        blocks.add(new WeightedBlock(blockType, percent));
                    }
                } catch (NumberFormatException e) {
                    // Ignorer les entrées invalides
                }
            }

            if (blocks.isEmpty()) {
                return null;
            }
        } else {
            // Pattern simple (un seul bloc)
            blocks.add(new WeightedBlock(trimmed, 100));
        }

        return new BlockPattern(blocks);
    }

    /**
     * Parse un pattern et résout les références spéciales (hand, *).
     * @param input Le pattern
     * @param handBlockType Le type de bloc en main du joueur (peut être null)
     * @return Le BlockPattern résolu, ou null si invalide
     */
    @Nullable
    public static BlockPattern parseAndResolve(@NotNull String input, @Nullable String handBlockType) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();
        List<WeightedBlock> blocks = new ArrayList<>();

        // Vérifier si c'est un pattern avec pourcentages
        if (trimmed.contains("%")) {
            Matcher matcher = PERCENT_PATTERN.matcher(trimmed);
            while (matcher.find()) {
                try {
                    int percent = Integer.parseInt(matcher.group(1));
                    String blockType = matcher.group(2).trim();
                    if (percent > 0 && !blockType.isEmpty()) {
                        // Résoudre hand/*
                        String resolved = resolveSpecial(blockType, handBlockType);
                        if (resolved != null) {
                            blocks.add(new WeightedBlock(resolved, percent));
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignorer les entrées invalides
                }
            }

            if (blocks.isEmpty()) {
                return null;
            }
        } else {
            // Pattern simple (un seul bloc)
            String resolved = resolveSpecial(trimmed, handBlockType);
            if (resolved == null) {
                return null;
            }
            blocks.add(new WeightedBlock(resolved, 100));
        }

        return new BlockPattern(blocks);
    }

    /**
     * Résout les références spéciales (hand, *, mainhand).
     */
    @Nullable
    private static String resolveSpecial(@NotNull String blockType, @Nullable String handBlockType) {
        String lower = blockType.toLowerCase();
        if (lower.equals("hand") || lower.equals("mainhand") || lower.equals("*")) {
            return handBlockType; // Peut être null si pas de bloc en main
        }
        return blockType;
    }

    /**
     * Sélectionne un bloc aléatoire selon les poids.
     * @return Le type de bloc sélectionné
     */
    @NotNull
    public String getRandomBlock() {
        if (blocks.size() == 1) {
            return blocks.get(0).blockType;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;

        for (WeightedBlock block : blocks) {
            cumulative += block.weight;
            if (roll < cumulative) {
                return block.blockType;
            }
        }

        // Fallback (ne devrait jamais arriver)
        return blocks.get(0).blockType;
    }

    /**
     * Vérifie si le pattern est un bloc unique (pas de variation).
     */
    public boolean isSingleBlock() {
        return blocks.size() == 1;
    }

    /**
     * Retourne le premier bloc (utile pour les patterns simples).
     */
    @NotNull
    public String getFirstBlock() {
        return blocks.get(0).blockType;
    }

    /**
     * Retourne tous les blocs du pattern.
     */
    @NotNull
    public List<WeightedBlock> getBlocks() {
        return new ArrayList<>(blocks);
    }

    /**
     * Retourne une représentation lisible du pattern.
     */
    @Override
    public String toString() {
        if (blocks.size() == 1) {
            return blocks.get(0).blockType;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(",");
            WeightedBlock b = blocks.get(i);
            sb.append(b.weight).append("%").append(b.blockType);
        }
        return sb.toString();
    }

    /**
     * Représente un bloc avec son poids (pourcentage).
     */
    public static class WeightedBlock {
        public final String blockType;
        public final int weight;

        public WeightedBlock(String blockType, int weight) {
            this.blockType = blockType;
            this.weight = weight;
        }
    }
}
