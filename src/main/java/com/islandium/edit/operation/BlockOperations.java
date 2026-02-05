package com.islandium.edit.operation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.history.BlockChange;
import com.islandium.edit.history.EditAction;
import com.islandium.edit.shape.Shape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service pour les opérations de modification de blocs.
 * Toutes les opérations sont asynchrones et utilisent un batching pour éviter le lag.
 */
public class BlockOperations {

    private final EditPlugin plugin;
    private final ScheduledExecutorService scheduler;

    public BlockOperations(@NotNull EditPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IslandiumEdit-Operations");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Arrête le scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    // === Résolution du type de bloc ===

    /**
     * Résout le type de bloc depuis un argument.
     * Supporte:
     * - "hand", "mainhand" ou "*" : utilise le bloc en main du joueur
     * - Sinon : retourne l'argument tel quel (ID du bloc)
     *
     * @return le type de bloc résolu, ou null si invalide
     */
    @Nullable
    public String resolveBlockType(@NotNull Player player, @NotNull String blockTypeArg) {
        String arg = blockTypeArg.toLowerCase().trim();

        // "hand", "mainhand" ou "*" = bloc en main
        if (arg.equals("hand") || arg.equals("mainhand") || arg.equals("*")) {
            try {
                var inventory = player.getInventory();
                if (inventory == null) {
                    return null;
                }
                ItemStack hand = inventory.getItemInHand();
                if (hand == null || hand.isEmpty()) {
                    return null;
                }
                // Retourner l'ID de l'item (qui peut être un bloc)
                return hand.getItemId();
            } catch (Exception e) {
                return null;
            }
        }

        // Sinon, retourner l'argument tel quel
        return blockTypeArg;
    }

    /**
     * Résout le type de bloc et retourne un résultat avec message d'erreur si échec.
     */
    @Nullable
    public ResolvedBlock resolveBlockTypeWithError(@NotNull Player player, @NotNull String blockTypeArg) {
        String resolved = resolveBlockType(player, blockTypeArg);
        if (resolved == null) {
            String arg = blockTypeArg.toLowerCase().trim();
            if (arg.equals("hand") || arg.equals("mainhand") || arg.equals("*")) {
                return new ResolvedBlock(null, "Aucun bloc en main");
            }
            return new ResolvedBlock(null, "Type de bloc invalide");
        }
        return new ResolvedBlock(resolved, null);
    }

    /**
     * Résultat de la résolution d'un type de bloc.
     */
    public record ResolvedBlock(@Nullable String blockType, @Nullable String error) {
        public boolean isValid() {
            return blockType != null;
        }
    }

    // === Résolution de patterns ===

    /**
     * Obtient le bloc en main du joueur.
     */
    @Nullable
    public String getHandBlockType(@NotNull Player player) {
        try {
            var inventory = player.getInventory();
            if (inventory == null) return null;
            ItemStack hand = inventory.getItemInHand();
            if (hand == null || hand.isEmpty()) return null;
            return hand.getItemId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Résout un pattern de blocs (ex: "20%grass,80%stone" ou "stone" ou "hand").
     * @return Le pattern résolu, ou null si invalide
     */
    @Nullable
    public BlockPattern resolvePattern(@NotNull Player player, @NotNull String patternArg) {
        String handBlock = getHandBlockType(player);
        return BlockPattern.parseAndResolve(patternArg, handBlock);
    }

    /**
     * Résout un pattern et retourne un résultat avec message d'erreur si échec.
     */
    @NotNull
    public ResolvedPattern resolvePatternWithError(@NotNull Player player, @NotNull String patternArg) {
        // Vérifier si c'est un pattern avec hand/* sans bloc en main
        String lower = patternArg.toLowerCase().trim();
        if (lower.equals("hand") || lower.equals("mainhand") || lower.equals("*")) {
            String handBlock = getHandBlockType(player);
            if (handBlock == null) {
                return new ResolvedPattern(null, "Aucun bloc en main");
            }
        }
        // Vérifier les patterns contenant hand/*
        if (lower.contains("%hand") || lower.contains("%*") || lower.contains("%mainhand")) {
            String handBlock = getHandBlockType(player);
            if (handBlock == null) {
                return new ResolvedPattern(null, "Aucun bloc en main");
            }
        }

        BlockPattern pattern = resolvePattern(player, patternArg);
        if (pattern == null) {
            return new ResolvedPattern(null, "Pattern invalide. Format: block ou 20%block1,80%block2");
        }
        return new ResolvedPattern(pattern, null);
    }

    /**
     * Résultat de la résolution d'un pattern.
     */
    public record ResolvedPattern(@Nullable BlockPattern pattern, @Nullable String error) {
        public boolean isValid() {
            return pattern != null;
        }
    }

    // === Opérations principales ===

    /**
     * Remplit la sélection avec un type de bloc.
     */
    public CompletableFuture<OperationResult> fill(@NotNull Player player, @NotNull String blockType) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = generateCuboidPositions(bounds);
        return checkAndExecute(player, world, positions, blockType, "Fill with " + blockType);
    }

    /**
     * Remplace un type de bloc par un autre dans la sélection.
     */
    public CompletableFuture<OperationResult> replace(@NotNull Player player,
                                                       @NotNull String fromBlock,
                                                       @NotNull String toBlock) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        // Normaliser "air" et "void" pour la comparaison
        boolean searchingForAir = fromBlock.equalsIgnoreCase("air") || fromBlock.equalsIgnoreCase("void");

        // Trouver les blocs à remplacer
        List<int[]> toReplace = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockType bt = world.getBlockType(x, y, z);

                    // Vérifier si le bloc correspond
                    boolean matches = false;
                    if (searchingForAir) {
                        // Pour air/void: matcher les blocs null, EMPTY, ou avec ID "air"
                        matches = (bt == null || bt == BlockType.EMPTY ||
                                   bt.getId().equalsIgnoreCase("air"));
                    } else {
                        // Pour les autres blocs: comparaison normale
                        matches = (bt != null && bt != BlockType.EMPTY &&
                                   bt.getId().equalsIgnoreCase(fromBlock));
                    }

                    if (matches) {
                        toReplace.add(new int[]{x, y, z});
                    }
                }
            }
        }

        if (toReplace.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.success("Aucun bloc a remplacer", 0));
        }

        return checkAndExecute(player, world, toReplace, toBlock,
                "Replace " + fromBlock + " -> " + toBlock);
    }

    /**
     * Remplit les blocs d'air au niveau Y spécifié dans la sélection.
     * (fill - remplace uniquement l'air au niveau des pieds)
     */
    public CompletableFuture<OperationResult> fillAirAtLevel(@NotNull Player player,
                                                               @NotNull String blockType,
                                                               int yLevel) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        // Trouver les blocs d'air au niveau Y
        List<int[]> airBlocks = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                BlockType bt = world.getBlockType(x, yLevel, z);
                boolean isAir = (bt == null || bt == BlockType.EMPTY || bt.getId().equalsIgnoreCase("air"));
                if (isAir) {
                    airBlocks.add(new int[]{x, yLevel, z});
                }
            }
        }

        if (airBlocks.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.success("Aucun bloc d'air a remplir a Y=" + yLevel, 0));
        }

        return checkAndExecute(player, world, airBlocks, blockType, "Fill air at Y=" + yLevel);
    }

    /**
     * Remplit les blocs d'air depuis le niveau Y jusqu'à Y - depth dans la sélection.
     * (fillair - remplace l'air sur plusieurs niveaux vers le bas)
     */
    public CompletableFuture<OperationResult> fillAirRange(@NotNull Player player,
                                                            @NotNull String blockType,
                                                            int yStart,
                                                            int depth) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        int yEnd = yStart - depth;
        int yMin = Math.min(yStart, yEnd);
        int yMax = Math.max(yStart, yEnd);

        // Trouver les blocs d'air dans la plage Y
        List<int[]> airBlocks = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockType bt = world.getBlockType(x, y, z);
                    boolean isAir = (bt == null || bt == BlockType.EMPTY || bt.getId().equalsIgnoreCase("air"));
                    if (isAir) {
                        airBlocks.add(new int[]{x, y, z});
                    }
                }
            }
        }

        if (airBlocks.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.success("Aucun bloc d'air a remplir (Y=" + yMax + " a Y=" + yMin + ")", 0));
        }

        return checkAndExecute(player, world, airBlocks, blockType,
                "Fill air from Y=" + yMax + " to Y=" + yMin);
    }

    /**
     * Remplit les blocs d'air autour du joueur au niveau Y spécifié (cercle).
     * (efill - ne nécessite pas de sélection)
     */
    public CompletableFuture<OperationResult> fillAirAroundPlayer(@NotNull Player player,
                                                                    @NotNull String blockType,
                                                                    int centerX, int yLevel, int centerZ,
                                                                    int radius) {
        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        CompletableFuture<OperationResult> future = new CompletableFuture<>();

        // Recherche async des blocs d'air
        scheduler.schedule(() -> {
            world.execute(() -> {
                // Trouver les blocs d'air au niveau Y dans le rayon (cercle)
                List<int[]> airBlocks = new ArrayList<>();
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        // Vérifier si dans le rayon (cercle)
                        int dx = x - centerX;
                        int dz = z - centerZ;
                        if (dx * dx + dz * dz > radius * radius) {
                            continue;
                        }

                        BlockType bt = world.getBlockType(x, yLevel, z);
                        boolean isAir = (bt == null || bt == BlockType.EMPTY || bt.getId().equalsIgnoreCase("air"));
                        if (isAir) {
                            airBlocks.add(new int[]{x, yLevel, z});
                        }
                    }
                }

                if (airBlocks.isEmpty()) {
                    future.complete(OperationResult.success("Aucun bloc d'air a remplir (rayon " + radius + ", Y=" + yLevel + ")", 0));
                    return;
                }

                // Exécuter le remplissage
                checkAndExecute(player, world, airBlocks, blockType,
                        "Fill air around player (r=" + radius + ", Y=" + yLevel + ")")
                        .thenAccept(future::complete);
            });
        }, 0, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Remplit les blocs d'air sous les pieds du joueur en descendant (flood fill).
     * S'arrête quand il rencontre un bloc solide (ne traverse pas les murs).
     * Descend jusqu'au sol ou Y=0.
     * (efillr - ne nécessite pas de sélection)
     */
    public CompletableFuture<OperationResult> fillAirBelowPlayer(@NotNull Player player,
                                                                  @NotNull String blockType,
                                                                  int centerX, int yStart, int centerZ,
                                                                  int radius) {
        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        CompletableFuture<OperationResult> future = new CompletableFuture<>();

        // Recherche async des blocs d'air avec flood fill
        scheduler.schedule(() -> {
            world.execute(() -> {
                // Flood fill depuis la position du joueur vers le bas
                // On utilise un Set pour éviter les doublons
                Set<Long> visited = new HashSet<>();
                List<int[]> airBlocks = new ArrayList<>();
                Queue<int[]> queue = new LinkedList<>();

                // Commencer sous les pieds du joueur (Y - 1)
                int startY = yStart - 1;
                queue.add(new int[]{centerX, startY, centerZ});

                while (!queue.isEmpty()) {
                    int[] current = queue.poll();
                    int x = current[0];
                    int y = current[1];
                    int z = current[2];

                    // Vérifier les limites (Y >= 0 et pas au-dessus du point de départ)
                    if (y < 0 || y > startY) continue;

                    // Vérifier si dans le rayon horizontal
                    int dx = x - centerX;
                    int dz = z - centerZ;
                    if (dx * dx + dz * dz > radius * radius) continue;

                    // Créer une clé unique pour ce bloc
                    long key = ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
                    if (visited.contains(key)) continue;
                    visited.add(key);

                    // Vérifier si c'est de l'air
                    BlockType bt = world.getBlockType(x, y, z);
                    boolean isAir = (bt == null || bt == BlockType.EMPTY || bt.getId().equalsIgnoreCase("air"));

                    if (!isAir) continue; // Bloc solide = barrière, on ne traverse pas

                    airBlocks.add(new int[]{x, y, z});

                    // Ajouter uniquement les voisins qui sont de l'air (connectés)
                    // Cela empêche de "sauter" par-dessus les murs
                    int[][] neighbors = {
                        {x + 1, y, z},
                        {x - 1, y, z},
                        {x, y, z + 1},
                        {x, y, z - 1},
                        {x, y - 1, z} // Vers le bas
                    };

                    for (int[] neighbor : neighbors) {
                        queue.add(neighbor);
                    }
                }

                if (airBlocks.isEmpty()) {
                    future.complete(OperationResult.success("Aucun bloc d'air sous les pieds (rayon " + radius + ")", 0));
                    return;
                }

                // Exécuter le remplissage
                checkAndExecute(player, world, airBlocks, blockType,
                        "Fill air below player (r=" + radius + ")")
                        .thenAccept(future::complete);
            });
        }, 0, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Remplace des blocs dans un rayon autour du joueur.
     * (replacenear - ne nécessite pas de sélection)
     */
    public CompletableFuture<OperationResult> replaceNear(@NotNull Player player,
                                                           int radius,
                                                           @NotNull String fromBlock,
                                                           @NotNull String toBlock) {
        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        // Position du joueur
        var transform = player.getTransformComponent();
        var pos = transform.getPosition();
        int centerX = (int) pos.getX();
        int centerY = (int) pos.getY();
        int centerZ = (int) pos.getZ();

        // Limiter le rayon
        if (radius < 1 || radius > 100) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Rayon invalide (1-100)"));
        }

        boolean searchingForAir = fromBlock.equalsIgnoreCase("air") || fromBlock.equalsIgnoreCase("void");

        // Trouver les blocs à remplacer dans le rayon
        List<int[]> toReplace = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Vérifier si dans le rayon (sphérique)
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }

                    BlockType bt = world.getBlockType(x, y, z);
                    boolean matches = false;
                    if (searchingForAir) {
                        matches = (bt == null || bt == BlockType.EMPTY ||
                                   bt.getId().equalsIgnoreCase("air"));
                    } else {
                        matches = (bt != null && bt != BlockType.EMPTY &&
                                   bt.getId().equalsIgnoreCase(fromBlock));
                    }

                    if (matches) {
                        toReplace.add(new int[]{x, y, z});
                    }
                }
            }
        }

        if (toReplace.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.success("Aucun bloc a remplacer dans un rayon de " + radius, 0));
        }

        return checkAndExecute(player, world, toReplace, toBlock,
                "Replace near " + fromBlock + " -> " + toBlock + " (r=" + radius + ")");
    }

    /**
     * Crée les murs de la sélection.
     */
    public CompletableFuture<OperationResult> walls(@NotNull Player player, @NotNull String blockType) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = new ArrayList<>();

        // Murs sur X (min et max)
        for (int y = bounds[1]; y <= bounds[4]; y++) {
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                positions.add(new int[]{bounds[0], y, z});
                positions.add(new int[]{bounds[3], y, z});
            }
        }

        // Murs sur Z (sans les coins déjà ajoutés)
        for (int y = bounds[1]; y <= bounds[4]; y++) {
            for (int x = bounds[0] + 1; x < bounds[3]; x++) {
                positions.add(new int[]{x, y, bounds[2]});
                positions.add(new int[]{x, y, bounds[5]});
            }
        }

        return checkAndExecute(player, world, positions, blockType, "Walls with " + blockType);
    }

    /**
     * Crée le sol de la sélection (face Y minimum).
     */
    public CompletableFuture<OperationResult> floor(@NotNull Player player, @NotNull String blockType) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = new ArrayList<>();
        int y = bounds[1];
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                positions.add(new int[]{x, y, z});
            }
        }

        return checkAndExecute(player, world, positions, blockType, "Floor with " + blockType);
    }

    /**
     * Crée le plafond de la sélection (face Y maximum).
     */
    public CompletableFuture<OperationResult> ceiling(@NotNull Player player, @NotNull String blockType) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = new ArrayList<>();
        int y = bounds[4];
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                positions.add(new int[]{x, y, z});
            }
        }

        return checkAndExecute(player, world, positions, blockType, "Ceiling with " + blockType);
    }

    /**
     * Crée le contour (les 12 arêtes) de la sélection.
     */
    public CompletableFuture<OperationResult> outline(@NotNull Player player, @NotNull String blockType) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = new ArrayList<>();

        // 4 arêtes horizontales sur X (en bas)
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            positions.add(new int[]{x, bounds[1], bounds[2]});
            positions.add(new int[]{x, bounds[1], bounds[5]});
            positions.add(new int[]{x, bounds[4], bounds[2]});
            positions.add(new int[]{x, bounds[4], bounds[5]});
        }

        // 4 arêtes verticales (sans les coins)
        for (int y = bounds[1] + 1; y < bounds[4]; y++) {
            positions.add(new int[]{bounds[0], y, bounds[2]});
            positions.add(new int[]{bounds[0], y, bounds[5]});
            positions.add(new int[]{bounds[3], y, bounds[2]});
            positions.add(new int[]{bounds[3], y, bounds[5]});
        }

        // 4 arêtes horizontales sur Z (sans les coins)
        for (int z = bounds[2] + 1; z < bounds[5]; z++) {
            positions.add(new int[]{bounds[0], bounds[1], z});
            positions.add(new int[]{bounds[0], bounds[4], z});
            positions.add(new int[]{bounds[3], bounds[1], z});
            positions.add(new int[]{bounds[3], bounds[4], z});
        }

        return checkAndExecute(player, world, positions, blockType, "Outline with " + blockType);
    }

    /**
     * Place une forme à la position spécifiée.
     */
    public CompletableFuture<OperationResult> placeShape(@NotNull Player player,
                                                          @NotNull Shape shape,
                                                          int centerX, int centerY, int centerZ,
                                                          @NotNull String blockType) {
        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        List<int[]> positions = shape.generatePositions(centerX, centerY, centerZ);

        return checkAndExecute(player, world, positions, blockType, shape.getDescription());
    }

    // === Undo / Redo ===

    /**
     * Annule la dernière action.
     */
    public CompletableFuture<OperationResult> undo(@NotNull Player player) {
        UUID playerId = player.getUuid();
        EditAction action = plugin.getEditHistory().popUndo(playerId);

        if (action == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Rien a annuler"));
        }

        if (action.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Action vide"));
        }

        // Utiliser le monde du joueur (plus fiable que getWorldById)
        World world = getPlayerWorld(player);
        if (world == null) {
            // Fallback sur la recherche par ID
            String worldId = action.getWorldId();
            if (worldId != null) {
                world = getWorldById(worldId);
            }
        }

        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        // Restaurer les anciens blocs
        return applyChangesReverse(world, action.getChanges())
                .thenApply(count -> OperationResult.success(
                        "Undo: " + action.getDescription(), count));
    }

    /**
     * Refait la dernière action annulée.
     */
    public CompletableFuture<OperationResult> redo(@NotNull Player player) {
        UUID playerId = player.getUuid();
        EditAction action = plugin.getEditHistory().popRedo(playerId);

        if (action == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Rien a refaire"));
        }

        if (action.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Action vide"));
        }

        // Utiliser le monde du joueur (plus fiable que getWorldById)
        World world = getPlayerWorld(player);
        if (world == null) {
            // Fallback sur la recherche par ID
            String worldId = action.getWorldId();
            if (worldId != null) {
                world = getWorldById(worldId);
            }
        }

        if (world == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Monde introuvable"));
        }

        // Remettre les nouveaux blocs
        return applyChangesForward(world, action.getChanges())
                .thenApply(count -> OperationResult.success(
                        "Redo: " + action.getDescription(), count));
    }

    // === Helpers ===

    /**
     * Version legacy avec String (convertit en pattern simple).
     */
    private CompletableFuture<OperationResult> checkAndExecute(@NotNull Player player,
                                                                 @NotNull World world,
                                                                 @NotNull List<int[]> positions,
                                                                 @NotNull String blockType,
                                                                 @NotNull String description) {
        BlockPattern pattern = BlockPattern.parse(blockType);
        if (pattern == null) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Type de bloc invalide: " + blockType));
        }
        return checkAndExecutePattern(player, world, positions, pattern, description);
    }

    /**
     * Version avec BlockPattern pour support des pourcentages.
     */
    private CompletableFuture<OperationResult> checkAndExecutePattern(@NotNull Player player,
                                                                 @NotNull World world,
                                                                 @NotNull List<int[]> positions,
                                                                 @NotNull BlockPattern pattern,
                                                                 @NotNull String description) {
        if (positions.isEmpty()) {
            return CompletableFuture.completedFuture(
                    OperationResult.success("Aucun bloc a modifier", 0));
        }

        if (positions.size() > EditPlugin.MAX_BLOCKS_PER_OPERATION) {
            return CompletableFuture.completedFuture(
                    OperationResult.failure("Operation trop grande: " + positions.size() +
                            " blocs (max: " + EditPlugin.MAX_BLOCKS_PER_OPERATION + ")"));
        }

        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        EditAction action = new EditAction(description);
        String worldId = world.getName();

        processBlocksInBatchesPattern(world, worldId, positions, pattern, action, result -> {
            if (result.success && !action.isEmpty()) {
                plugin.getEditHistory().pushAction(player.getUuid(), action);
            }
            future.complete(result);
        });

        return future;
    }

    /**
     * Version legacy avec String.
     */
    private void processBlocksInBatches(@NotNull World world,
                                         @NotNull String worldId,
                                         @NotNull List<int[]> positions,
                                         @NotNull String blockType,
                                         @NotNull EditAction action,
                                         @NotNull Consumer<OperationResult> callback) {
        BlockPattern pattern = BlockPattern.parse(blockType);
        if (pattern != null) {
            processBlocksInBatchesPattern(world, worldId, positions, pattern, action, callback);
        } else {
            callback.accept(OperationResult.failure("Type de bloc invalide"));
        }
    }

    /**
     * Version avec BlockPattern pour support des pourcentages.
     */
    private void processBlocksInBatchesPattern(@NotNull World world,
                                         @NotNull String worldId,
                                         @NotNull List<int[]> positions,
                                         @NotNull BlockPattern pattern,
                                         @NotNull EditAction action,
                                         @NotNull Consumer<OperationResult> callback) {
        int total = positions.size();
        int[] processed = {0};
        int[] failed = {0};

        List<List<int[]>> batches = partition(positions, EditPlugin.BLOCKS_PER_BATCH);

        for (int i = 0; i < batches.size(); i++) {
            List<int[]> batch = batches.get(i);
            int delay = i * EditPlugin.BATCH_DELAY_MS;

            scheduler.schedule(() -> {
                world.execute(() -> {
                    for (int[] pos : batch) {
                        try {
                            BlockType oldBt = world.getBlockType(pos[0], pos[1], pos[2]);
                            String oldType = (oldBt != null && oldBt != BlockType.EMPTY)
                                    ? oldBt.getId() : "air";

                            // Obtenir le bloc à placer (peut varier avec les patterns)
                            String blockType = pattern.getRandomBlock();

                            // Pour "air", utiliser breakBlock au lieu de setBlock
                            if ("air".equalsIgnoreCase(blockType)) {
                                world.breakBlock(pos[0], pos[1], pos[2], 0);
                            } else {
                                world.setBlock(pos[0], pos[1], pos[2], blockType);
                            }
                            action.addChange(worldId, pos[0], pos[1], pos[2], oldType, blockType);
                            processed[0]++;
                        } catch (Exception e) {
                            failed[0]++;
                        }
                    }

                    if (processed[0] + failed[0] >= total) {
                        if (failed[0] > 0) {
                            callback.accept(OperationResult.partial(
                                    failed[0] + " blocs ont echoue", processed[0]));
                        } else {
                            callback.accept(OperationResult.success("OK", processed[0]));
                        }
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private CompletableFuture<Integer> applyChangesReverse(@NotNull World world,
                                                            @NotNull List<BlockChange> changes) {
        CompletableFuture<Integer> future = new CompletableFuture<>();

        // Si pas de changements, retourner immédiatement 0
        if (changes.isEmpty()) {
            future.complete(0);
            return future;
        }

        int[] count = {0};
        int[] processedBatches = {0};

        List<List<BlockChange>> batches = partition(changes, EditPlugin.BLOCKS_PER_BATCH);
        int totalBatches = batches.size();

        System.out.println("[IslandiumEdit] Undo: " + changes.size() + " changes in " + totalBatches + " batches");

        for (int i = 0; i < batches.size(); i++) {
            List<BlockChange> batch = batches.get(i);
            int delay = i * EditPlugin.BATCH_DELAY_MS;

            scheduler.schedule(() -> {
                world.execute(() -> {
                    for (BlockChange change : batch) {
                        try {
                            String oldType = change.getOldBlockTypeSafe();
                            // Pour "air", utiliser breakBlock au lieu de setBlock
                            if ("air".equalsIgnoreCase(oldType)) {
                                world.breakBlock(change.x(), change.y(), change.z(), 0);
                            } else {
                                world.setBlock(change.x(), change.y(), change.z(), oldType);
                            }
                            count[0]++;
                        } catch (Exception e) {
                            System.err.println("[IslandiumEdit] Undo error: " + e.getMessage());
                        }
                    }

                    processedBatches[0]++;
                    if (processedBatches[0] >= totalBatches) {
                        future.complete(count[0]);
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }

        return future;
    }

    private CompletableFuture<Integer> applyChangesForward(@NotNull World world,
                                                            @NotNull List<BlockChange> changes) {
        CompletableFuture<Integer> future = new CompletableFuture<>();

        // Si pas de changements, retourner immédiatement 0
        if (changes.isEmpty()) {
            future.complete(0);
            return future;
        }

        int[] count = {0};
        int[] processedBatches = {0};

        List<List<BlockChange>> batches = partition(changes, EditPlugin.BLOCKS_PER_BATCH);
        int totalBatches = batches.size();

        for (int i = 0; i < batches.size(); i++) {
            List<BlockChange> batch = batches.get(i);
            int delay = i * EditPlugin.BATCH_DELAY_MS;

            scheduler.schedule(() -> {
                world.execute(() -> {
                    for (BlockChange change : batch) {
                        try {
                            String newType = change.newBlockType();
                            // Pour "air", utiliser breakBlock au lieu de setBlock
                            if ("air".equalsIgnoreCase(newType)) {
                                world.breakBlock(change.x(), change.y(), change.z(), 0);
                            } else {
                                world.setBlock(change.x(), change.y(), change.z(), newType);
                            }
                            count[0]++;
                        } catch (Exception e) {
                            System.err.println("[IslandiumEdit] Redo error: " + e.getMessage());
                        }
                    }

                    processedBatches[0]++;
                    if (processedBatches[0] >= totalBatches) {
                        future.complete(count[0]);
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }

        return future;
    }

    private List<int[]> generateCuboidPositions(int[] bounds) {
        List<int[]> positions = new ArrayList<>();
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    positions.add(new int[]{x, y, z});
                }
            }
        }
        return positions;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @Nullable
    private World getPlayerWorld(@NotNull Player player) {
        try {
            return player.getWorld();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private World getWorldById(@NotNull String worldId) {
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }

        World world = universe.getWorld(worldId);
        if (world != null) {
            return world;
        }

        // Recherche partielle
        for (Map.Entry<String, World> entry : universe.getWorlds().entrySet()) {
            if (entry.getKey().contains(worldId) || entry.getValue().getName().contains(worldId)) {
                return entry.getValue();
            }
        }

        return null;
    }

    // === Result class ===

    public record OperationResult(boolean success, String message, int blocksAffected) {

        public static OperationResult success(String message, int blocksAffected) {
            return new OperationResult(true, message, blocksAffected);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message, 0);
        }

        public static OperationResult partial(String message, int blocksAffected) {
            return new OperationResult(true, message, blocksAffected);
        }
    }
}
