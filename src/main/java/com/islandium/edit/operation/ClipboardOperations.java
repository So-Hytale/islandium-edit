package com.islandium.edit.operation;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.history.EditAction;
import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.math.BlockTransform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service pour les opérations de clipboard (copy/paste/rotate/flip).
 *
 * Like WorldEdit, this uses a deferred transform approach:
 * - The original clipboard data is never modified
 * - A transform matrix is stored separately
 * - The transform is applied at paste time
 */
public class ClipboardOperations {

    private final EditPlugin plugin;
    private final Map<UUID, ClipboardHolder> clipboards;
    private final ScheduledExecutorService scheduler;

    public ClipboardOperations(@NotNull EditPlugin plugin) {
        this.plugin = plugin;
        this.clipboards = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IslandiumEdit-Clipboard");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Copie la sélection du joueur dans son clipboard (asynchrone).
     */
    public CompletableFuture<BlockOperations.OperationResult> copy(@NotNull Player player) {
        int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
        if (bounds == null) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Aucune selection definie"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Monde introuvable"));
        }

        // Position du joueur (pour l'offset)
        var transform = player.getTransformComponent();
        if (transform == null) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Position joueur introuvable"));
        }

        var pos = transform.getPosition();
        // Utiliser Math.floor pour cohérence avec paste et preview
        int playerX = (int) Math.floor(pos.getX());
        int playerY = (int) Math.floor(pos.getY());
        int playerZ = (int) Math.floor(pos.getZ());

        int width = bounds[3] - bounds[0] + 1;
        int height = bounds[4] - bounds[1] + 1;
        int depth = bounds[5] - bounds[2] + 1;

        // Offset depuis la position du joueur
        int offsetX = bounds[0] - playerX;
        int offsetY = bounds[1] - playerY;
        int offsetZ = bounds[2] - playerZ;

        UUID playerId = player.getUuid();
        CompletableFuture<BlockOperations.OperationResult> future = new CompletableFuture<>();

        // Exécuter la copie de manière asynchrone
        scheduler.execute(() -> {
            world.execute(() -> {
                ClipboardData clipboard = new ClipboardData(width, height, depth, offsetX, offsetY, offsetZ);
                int count = 0;

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        for (int z = 0; z < depth; z++) {
                            int worldX = bounds[0] + x;
                            int worldY = bounds[1] + y;
                            int worldZ = bounds[2] + z;

                            BlockType bt = world.getBlockType(worldX, worldY, worldZ);
                            if (bt == null || bt == BlockType.EMPTY || "air".equalsIgnoreCase(bt.getId())) {
                                clipboard.setBlock(x, y, z, "air", 0);
                            } else {
                                // Récupérer la rotation du bloc via le chunk
                                int rotation = 0;
                                try {
                                    long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
                                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                                    if (chunk != null) {
                                        rotation = chunk.getRotationIndex(worldX, worldY, worldZ);
                                    }
                                } catch (Exception e) {
                                    // Ignorer les erreurs de rotation
                                }
                                clipboard.setBlock(x, y, z, bt.getId(), rotation);
                                count++;
                            }
                        }
                    }
                }

                // Create a new holder with identity transform
                clipboards.put(playerId, new ClipboardHolder(clipboard));

                // Envoyer la preview au client
                try {
                    sendClipboardPreview(player, world, bounds);
                } catch (Exception e) {
                    plugin.getLogger().at(java.util.logging.Level.WARNING).log(
                        "[COPY] Erreur preview: " + e.getMessage());
                }

                future.complete(BlockOperations.OperationResult.success("Copie effectuee", count));
            });
        });

        return future;
    }

    /**
     * Colle le clipboard du joueur à sa position actuelle.
     * The transform is applied at paste time (WorldEdit style).
     *
     * @param skipAir si true, les blocs d'air du clipboard sont ignorés
     */
    public CompletableFuture<BlockOperations.OperationResult> paste(@NotNull Player player, boolean skipAir) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Clipboard vide"));
        }

        World world = getPlayerWorld(player);
        if (world == null) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Monde introuvable"));
        }

        // Position du joueur
        var transformComponent = player.getTransformComponent();
        if (transformComponent == null) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure("Position joueur introuvable"));
        }

        var pos = transformComponent.getPosition();
        // Utiliser Math.floor pour avoir le même comportement que la preview
        // (int) tronque vers 0, Math.floor arrondit vers le bas
        int playerX = (int) Math.floor(pos.getX());
        int playerY = (int) Math.floor(pos.getY());
        int playerZ = (int) Math.floor(pos.getZ());

        ClipboardData clipboard = holder.getClipboard();
        AffineTransform transform = holder.getTransform();

        // Construire la liste des positions, types de blocs et rotations avec transformation
        List<int[]> positions = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();
        List<Integer> blockRotations = new ArrayList<>();

        // WorldEdit style: transform is applied around the PLAYER (origin = 0,0,0)
        int offsetX = clipboard.getOffsetX();
        int offsetY = clipboard.getOffsetY();
        int offsetZ = clipboard.getOffsetZ();

        for (Map.Entry<String, String> entry : clipboard.getBlocks().entrySet()) {
            String key = entry.getKey();
            String blockType = entry.getValue();

            // Si skipAir est activé, ignorer les blocs d'air
            if (skipAir && "air".equalsIgnoreCase(blockType)) {
                continue;
            }

            int[] coords = ClipboardData.parseKey(key);

            // Position relative au joueur (offset + position dans le clipboard)
            double relX = offsetX + coords[0];
            double relY = offsetY + coords[1];
            double relZ = offsetZ + coords[2];

            // Appliquer la transformation autour du joueur (origin = 0,0,0)
            double[] transformed = transform.apply(relX, relY, relZ);

            // Ajouter la position du joueur
            int worldX = playerX + (int) Math.floor(transformed[0]);
            int worldY = playerY + (int) Math.floor(transformed[1]);
            int worldZ = playerZ + (int) Math.floor(transformed[2]);

            positions.add(new int[]{worldX, worldY, worldZ});
            blockTypes.add(blockType);

            // Récupérer et transformer la rotation
            int originalRotation = clipboard.getRotation(key);
            int transformedRotation = transformRotation(originalRotation, transform);
            blockRotations.add(transformedRotation);
        }

        if (positions.size() > EditPlugin.MAX_BLOCKS_PER_OPERATION) {
            return CompletableFuture.completedFuture(
                    BlockOperations.OperationResult.failure(
                            "Clipboard trop grand: " + positions.size() +
                                    " blocs (max: " + EditPlugin.MAX_BLOCKS_PER_OPERATION + ")"));
        }

        CompletableFuture<BlockOperations.OperationResult> future = new CompletableFuture<>();
        EditAction action = new EditAction("Paste clipboard");
        String worldId = world.getName();

        processBlocksWithRotations(world, worldId, positions, blockTypes, blockRotations, action, result -> {
            if (result.success() && !action.isEmpty()) {
                plugin.getEditHistory().pushAction(player.getUuid(), action);
            }
            future.complete(result);
        });

        return future;
    }

    /**
     * Fait une rotation du clipboard (WorldEdit style - deferred).
     * The rotation is stored as a transform and applied at paste time.
     */
    public BlockOperations.OperationResult rotate(@NotNull Player player, int degrees) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return BlockOperations.OperationResult.failure("Clipboard vide");
        }

        // Compose the new rotation with existing transform
        AffineTransform oldTransform = holder.getTransform();
        AffineTransform newTransform = oldTransform.rotateY(degrees);
        holder.setTransform(newTransform);

        return BlockOperations.OperationResult.success("Rotation de " + degrees + " degres", holder.getBlockCount());
    }

    /**
     * Fait un flip du clipboard sur un axe (WorldEdit style - deferred).
     * The flip is stored as a transform and applied at paste time.
     */
    public BlockOperations.OperationResult flip(@NotNull Player player, @NotNull String axis) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return BlockOperations.OperationResult.failure("Clipboard vide");
        }

        AffineTransform oldTransform = holder.getTransform();

        // Create scale transform for flip
        // WorldEdit: direction.abs().multiply(-2).add(1, 1, 1)
        // For axis "x": scale(-1, 1, 1)
        // For axis "y": scale(1, -1, 1)
        // For axis "z": scale(1, 1, -1)
        AffineTransform flipTransform = switch (axis.toLowerCase()) {
            case "x" -> oldTransform.scale(-1, 1, 1);
            case "y" -> oldTransform.scale(1, -1, 1);
            case "z" -> oldTransform.scale(1, 1, -1);
            default -> null;
        };

        if (flipTransform == null) {
            return BlockOperations.OperationResult.failure("Axe invalide: " + axis + " (utiliser x, y ou z)");
        }

        holder.setTransform(flipTransform);

        return BlockOperations.OperationResult.success("Flip sur l'axe " + axis.toUpperCase(), holder.getBlockCount());
    }

    /**
     * Fait un flip du clipboard basé sur la direction du regard du joueur (WorldEdit style).
     * Calcule le vecteur de direction à partir des angles de rotation de la tête.
     */
    public BlockOperations.OperationResult flipByLookDirection(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return BlockOperations.OperationResult.failure("Clipboard vide");
        }

        // Obtenir la rotation de la tête (angles en radians)
        var headRot = player.getPlayerRef().getHeadRotation();
        if (headRot == null) {
            return BlockOperations.OperationResult.failure("Direction introuvable");
        }

        // headRot: X = pitch (vertical), Y = yaw (horizontal), Z = roll
        float pitch = headRot.getX();
        float yaw = headRot.getY();

        // Calculer le vecteur de direction
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        // Direction vector - Hytale convention
        double dirX = -sinYaw * cosPitch;
        double dirY = -sinPitch;
        double dirZ = cosYaw * cosPitch;

        // Déterminer l'axe de flip basé sur la composante dominante
        String axis;
        String direction;

        double absX = Math.abs(dirX);
        double absY = Math.abs(dirY);
        double absZ = Math.abs(dirZ);

        if (absY > absX && absY > absZ) {
            axis = "y";
            direction = dirY > 0 ? "bas" : "haut";
        } else if (absX > absZ) {
            axis = "x";
            direction = dirX > 0 ? "ouest" : "est";
        } else {
            axis = "z";
            direction = dirZ > 0 ? "nord" : "sud";
        }

        // Apply flip transform (WorldEdit style)
        AffineTransform flipTransform = switch (axis) {
            case "x" -> holder.getTransform().scale(-1, 1, 1);
            case "y" -> holder.getTransform().scale(1, -1, 1);
            case "z" -> holder.getTransform().scale(1, 1, -1);
            default -> holder.getTransform();
        };

        holder.setTransform(flipTransform);

        return BlockOperations.OperationResult.success("Flip vers " + direction + " (axe " + axis.toUpperCase() + ")", holder.getBlockCount());
    }

    /**
     * Fait un flip auto basé sur l'axe dominant du clipboard.
     */
    public BlockOperations.OperationResult flipAuto(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return BlockOperations.OperationResult.failure("Clipboard vide");
        }

        // Determine dominant axis based on clipboard position
        ClipboardData clipboard = holder.getClipboard();
        double centerX = clipboard.getOffsetX() + (clipboard.getWidth() / 2.0);
        double centerY = clipboard.getOffsetY() + (clipboard.getHeight() / 2.0);
        double centerZ = clipboard.getOffsetZ() + (clipboard.getDepth() / 2.0);

        double absX = Math.abs(centerX);
        double absY = Math.abs(centerY);
        double absZ = Math.abs(centerZ);

        String axis;
        if (absY > absX && absY > absZ) {
            axis = "y";
        } else if (absX > absZ) {
            axis = "x";
        } else {
            axis = "z";
        }

        // Apply flip
        AffineTransform flipTransform = switch (axis) {
            case "x" -> holder.getTransform().scale(-1, 1, 1);
            case "y" -> holder.getTransform().scale(1, -1, 1);
            case "z" -> holder.getTransform().scale(1, 1, -1);
            default -> holder.getTransform();
        };

        holder.setTransform(flipTransform);
        return BlockOperations.OperationResult.success("Flip sur l'axe " + axis.toUpperCase(), holder.getBlockCount());
    }

    /**
     * Remet le clipboard à son état original (reset transform).
     */
    public BlockOperations.OperationResult resetTransform(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        if (holder == null || holder.isEmpty()) {
            return BlockOperations.OperationResult.failure("Clipboard vide");
        }

        holder.setTransform(new AffineTransform());
        return BlockOperations.OperationResult.success("Transform reset", holder.getBlockCount());
    }

    /**
     * Vérifie si le joueur a un clipboard.
     */
    public boolean hasClipboard(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        return holder != null && !holder.isEmpty();
    }

    /**
     * Obtient le holder du clipboard du joueur.
     */
    @Nullable
    public ClipboardHolder getClipboardHolder(@NotNull Player player) {
        return clipboards.get(player.getUuid());
    }

    /**
     * Obtient le clipboard du joueur (pour compatibilité).
     */
    @Nullable
    public ClipboardData getClipboard(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        return holder != null ? holder.getClipboard() : null;
    }

    /**
     * Obtient la taille du clipboard.
     */
    public int getClipboardSize(@NotNull Player player) {
        ClipboardHolder holder = clipboards.get(player.getUuid());
        return holder != null ? holder.getBlockCount() : 0;
    }

    /**
     * Efface le clipboard du joueur.
     */
    public void clearClipboard(@NotNull Player player) {
        clipboards.remove(player.getUuid());
    }

    // === Helpers ===

    /**
     * Transforme un index de rotation Hytale (0-63) selon la transformation appliquée.
     *
     * L'index de rotation Hytale encode yaw/pitch/roll avec 4 valeurs chacun (0, 90, 180, 270).
     * Index = yaw + pitch*4 + roll*16 (approximativement)
     *
     * Pour un flip ou rotation, on transforme les composantes individuellement.
     */
    private int transformRotation(int rotationIndex, @NotNull AffineTransform transform) {
        if (rotationIndex == 0 && transform.isIdentity()) {
            return 0;
        }

        // Décomposer l'index en yaw, pitch, roll
        // Hytale: 4 valeurs de yaw (0, 90, 180, 270) * 4 valeurs de pitch * 4 valeurs de roll = 64
        int yaw = rotationIndex % 4;      // 0=0°, 1=90°, 2=180°, 3=270°
        int pitch = (rotationIndex / 4) % 4;
        int roll = (rotationIndex / 16) % 4;

        boolean flipX = transform.isFlipX();
        boolean flipZ = transform.isFlipZ();

        // Appliquer la rotation Y du transform
        int yRotation = transform.getYRotation();
        if (yRotation != 0) {
            // Ajouter la rotation (chaque step = 90°)
            int steps = (yRotation / 90) % 4;
            yaw = (yaw + steps) % 4;
        }

        // Appliquer les flips
        // Pour un miroir, on inverse les directions PERPENDICULAIRES à l'axe du miroir
        //
        // Flip X (miroir E/W): inverse est <-> ouest
        // Flip Z (miroir N/S): inverse nord <-> sud
        //
        // Convention Hytale: yaw 0, 1, 2, 3
        // Les deux flips doivent inverser les paires appropriées
        //
        // Après tests: flip X inverse 0<->2, flip Z inverse 1<->3
        if (flipX) {
            if (yaw == 0) yaw = 2;
            else if (yaw == 2) yaw = 0;
        }

        if (flipZ) {
            if (yaw == 1) yaw = 3;
            else if (yaw == 3) yaw = 1;
        }

        if (transform.isVerticalFlip()) {
            // Flip Y inverse le pitch
            if (pitch == 1) pitch = 3;
            else if (pitch == 3) pitch = 1;
        }

        return yaw + pitch * 4 + roll * 16;
    }

    private void processBlocksWithRotations(@NotNull World world,
                                             @NotNull String worldId,
                                             @NotNull List<int[]> positions,
                                             @NotNull List<String> blockTypes,
                                             @NotNull List<Integer> rotations,
                                             @NotNull EditAction action,
                                             @NotNull java.util.function.Consumer<BlockOperations.OperationResult> callback) {
        int total = positions.size();
        int[] processed = {0};
        int[] failed = {0};

        int batchCount = (total + EditPlugin.BLOCKS_PER_BATCH - 1) / EditPlugin.BLOCKS_PER_BATCH;

        for (int i = 0; i < batchCount; i++) {
            int start = i * EditPlugin.BLOCKS_PER_BATCH;
            int end = Math.min(start + EditPlugin.BLOCKS_PER_BATCH, total);
            int delay = i * EditPlugin.BATCH_DELAY_MS;

            int finalStart = start;
            int finalEnd = end;
            scheduler.schedule(() -> {
                world.execute(() -> {
                    for (int j = finalStart; j < finalEnd; j++) {
                        int[] pos = positions.get(j);
                        String blockType = blockTypes.get(j);
                        int rotation = rotations.get(j);

                        try {
                            BlockType oldBt = world.getBlockType(pos[0], pos[1], pos[2]);
                            String oldType = (oldBt != null && oldBt != BlockType.EMPTY)
                                    ? oldBt.getId() : "air";

                            // Pour "air", utiliser breakBlock au lieu de setBlock
                            if ("air".equalsIgnoreCase(blockType)) {
                                world.breakBlock(pos[0], pos[1], pos[2], 0);
                            } else {
                                // Utiliser le chunk pour placer le bloc avec rotation
                                long chunkIndex = ChunkUtil.indexChunkFromBlock(pos[0], pos[2]);
                                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                                if (chunk != null && rotation != 0) {
                                    // Récupérer le BlockType depuis l'AssetMap
                                    BlockType bt = BlockType.getAssetMap().getAsset(blockType);
                                    if (bt != null) {
                                        // Placer d'abord le bloc normalement pour obtenir son ID
                                        world.setBlock(pos[0], pos[1], pos[2], blockType);
                                        // Puis récupérer l'ID du bloc placé et le replacer avec rotation
                                        int blockId = chunk.getBlock(pos[0], pos[1], pos[2]);
                                        if (blockId > 0) {
                                            // setBlock(x, y, z, id, blockType, rotation, filler, settings)
                                            chunk.setBlock(pos[0], pos[1], pos[2], blockId, bt, rotation, 0, 0);
                                        }
                                    } else {
                                        // Fallback si le BlockType n'est pas trouvé
                                        world.setBlock(pos[0], pos[1], pos[2], blockType);
                                    }
                                } else {
                                    // Pas de rotation ou chunk non chargé, utiliser setBlock normal
                                    world.setBlock(pos[0], pos[1], pos[2], blockType);
                                }
                            }
                            action.addChange(worldId, pos[0], pos[1], pos[2], oldType, blockType);
                            processed[0]++;
                        } catch (Exception e) {
                            failed[0]++;
                            plugin.getLogger().at(java.util.logging.Level.WARNING).log(
                                "[PASTE ERROR] " + e.getMessage());
                        }
                    }

                    if (processed[0] + failed[0] >= total) {
                        if (failed[0] > 0) {
                            callback.accept(BlockOperations.OperationResult.partial(
                                    failed[0] + " blocs ont echoue", processed[0]));
                        } else {
                            callback.accept(BlockOperations.OperationResult.success("OK", processed[0]));
                        }
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Envoie une preview des blocs copies au client.
     * Construit une BlockSelection native, l'injecte temporairement dans le BuilderState
     * pour envoyer le paquet toPacketWithSelection(), puis restaure la selection originale.
     */
    @SuppressWarnings("deprecation")
    private void sendClipboardPreview(@NotNull Player player, @NotNull World world, int[] bounds) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        BuilderToolsPlugin.BuilderState state = plugin.getSelectionManager().getBuilderState(player);
        if (state == null) return;

        // Sauvegarder la selection actuelle
        BlockSelection originalSelection = state.getSelection();

        // Construire la BlockSelection avec les blocs du monde
        BlockSelection clipboardSelection = new BlockSelection();
        clipboardSelection.setSelectionArea(
            new Vector3i(bounds[0], bounds[1], bounds[2]),
            new Vector3i(bounds[3], bounds[4], bounds[5])
        );

        for (int bx = bounds[0]; bx <= bounds[3]; bx++) {
            for (int bz = bounds[2]; bz <= bounds[5]; bz++) {
                long chunkIdx = ChunkUtil.indexChunkFromBlock(bx, bz);
                WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
                if (chunk != null) {
                    for (int by = bounds[1]; by <= bounds[4]; by++) {
                        clipboardSelection.copyFromAtWorld(bx, by, bz, chunk, null);
                    }
                }
            }
        }

        // Injecter temporairement, envoyer le paquet, puis restaurer
        state.setSelection(clipboardSelection);
        connection.write(clipboardSelection.toPacketWithSelection());
        state.setSelection(originalSelection);
    }

    @Nullable
    private World getPlayerWorld(@NotNull Player player) {
        try {
            return player.getWorld();
        } catch (Exception e) {
            return null;
        }
    }
}
