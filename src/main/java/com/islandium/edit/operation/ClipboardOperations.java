package com.islandium.edit.operation;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.debug.DebugLogger;
import com.islandium.edit.history.EditAction;
import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.math.BlockSizeHelper;
import com.islandium.edit.math.BlockTransform;
import com.islandium.edit.math.RotationOverrides;
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
                int skippedFillers = 0;
                Map<String, Integer> blockCounts = new java.util.TreeMap<>();

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        for (int z = 0; z < depth; z++) {
                            int worldX = bounds[0] + x;
                            int worldY = bounds[1] + y;
                            int worldZ = bounds[2] + z;

                            BlockType bt = world.getBlockType(worldX, worldY, worldZ);
                            if (bt == null || bt == BlockType.EMPTY || "air".equalsIgnoreCase(bt.getId())) {
                                // Ne pas stocker l'air dans le clipboard (absence = air)
                            } else {
                                // Récupérer la rotation et le filler via le chunk
                                int rotation = 0;
                                boolean isFiller = false;
                                try {
                                    long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
                                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                                    if (chunk != null) {
                                        rotation = chunk.getRotationIndex(worldX, worldY, worldZ);
                                        // Vérifier si c'est un filler (position secondaire d'un bloc multi-part)
                                        int fillerValue = chunk.getFiller(worldX, worldY, worldZ);
                                        isFiller = BlockSizeHelper.isFiller(fillerValue);
                                    }
                                } catch (Exception e) {
                                    // Ignorer les erreurs
                                }

                                if (isFiller) {
                                    // Skipper les fillers : ils seront recréés automatiquement
                                    // lors du paste quand on place le bloc origine
                                    skippedFillers++;
                                } else {
                                    clipboard.setBlock(x, y, z, bt.getId(), rotation);
                                    blockCounts.merge(bt.getId(), 1, Integer::sum);
                                    count++;
                                }
                            }
                        }
                    }
                }

                // Create a new holder with identity transform
                clipboards.put(playerId, new ClipboardHolder(clipboard));

                // Debug log
                DebugLogger dbg = DebugLogger.get();
                if (dbg != null) {
                    dbg.logCopy(player.getDisplayName(), playerX, playerY, playerZ,
                            bounds, width, height, depth, offsetX, offsetY, offsetZ, count);
                    if (skippedFillers > 0) {
                        dbg.log("COPY", "Fillers skipped: " + skippedFillers + " (positions secondaires multi-part)");
                    }
                    // Log liste de tous les types de blocs copiés avec quantité + info multi-part
                    dbg.log("COPY", "Block types (" + blockCounts.size() + " types):");
                    for (Map.Entry<String, Integer> bc : blockCounts.entrySet()) {
                        String blockId = bc.getKey();
                        int qty = bc.getValue();
                        BlockSizeHelper.BlockSizeInfo sizeInfo = BlockSizeHelper.getBlockSize(blockId, 0);
                        if (sizeInfo != null && sizeInfo.isMultiPart()) {
                            int gridPositions = sizeInfo.totalGridPositions();
                            int realCount = gridPositions > 0 ? qty / gridPositions : qty;
                            dbg.log("COPY", "  " + qty + "x " + blockId
                                    + " [MULTI-PART " + sizeInfo.gridWidth() + "x" + sizeInfo.gridHeight() + "x" + sizeInfo.gridDepth()
                                    + " -> ~" + realCount + " objets reels]"
                                    + " flip=" + sizeInfo.flipType());
                        } else {
                            dbg.log("COPY", "  " + qty + "x " + blockId);
                        }
                    }
                    // Log résumé des blocs avec rotation
                    dbg.log("COPY", "Blocks with rotation: " + clipboard.getRotations().size());
                    for (Map.Entry<String, Integer> rotEntry : clipboard.getRotations().entrySet()) {
                        String bt = clipboard.getBlocks().get(rotEntry.getKey());
                        int[] rc = ClipboardData.parseKey(rotEntry.getKey());
                        int ridx = rotEntry.getValue();
                        int ry = ridx % 4;
                        int rp = (ridx / 4) % 4;
                        int rr = (ridx / 16) % 4;
                        dbg.log("COPY", "  [" + bt + "] pos=(" + rc[0] + "," + rc[1] + "," + rc[2]
                                + ") idx=" + ridx + " yaw=" + ry + " p=" + rp + " r=" + rr);
                    }
                }

                // Envoyer la preview au client
                try {
                    sendClipboardPreview(player, world, bounds, playerX, playerY, playerZ);
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
     */
    public CompletableFuture<BlockOperations.OperationResult> paste(@NotNull Player player, boolean skipAir) {
        return paste(player, skipAir, null);
    }

    /**
     * Colle le clipboard du joueur a une position donnee (ou position actuelle si null).
     * The transform is applied at paste time (WorldEdit style).
     *
     * @param skipAir si true, les blocs d'air du clipboard sont ignorés
     * @param positionOverride position [x,y,z] a utiliser au lieu de la position du joueur, ou null
     */
    public CompletableFuture<BlockOperations.OperationResult> paste(@NotNull Player player, boolean skipAir,
                                                                      int[] positionOverride) {
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

        int playerX, playerY, playerZ;

        if (positionOverride != null) {
            playerX = positionOverride[0];
            playerY = positionOverride[1];
            playerZ = positionOverride[2];
        } else {
            var transformComponent = player.getTransformComponent();
            if (transformComponent == null) {
                return CompletableFuture.completedFuture(
                        BlockOperations.OperationResult.failure("Position joueur introuvable"));
            }

            var pos = transformComponent.getPosition();
            playerX = (int) Math.floor(pos.getX());
            playerY = (int) Math.floor(pos.getY());
            playerZ = (int) Math.floor(pos.getZ());
        }

        ClipboardData clipboard = holder.getClipboard();
        AffineTransform transform = holder.getTransform();

        // Debug log paste start
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logPasteStart(player.getDisplayName(), playerX, playerY, playerZ, skipAir, clipboard, transform);
            // Log quelques points de test pour vérifier la matrice
            double[] test1 = transform.apply(1, 0, 0);
            dbg.logPointTransform("test X-axis", 1, 0, 0, test1[0], test1[1], test1[2]);
            double[] test2 = transform.apply(0, 0, 1);
            dbg.logPointTransform("test Z-axis", 0, 0, 1, test2[0], test2[1], test2[2]);
            double[] test3 = transform.apply(0, 1, 0);
            dbg.logPointTransform("test Y-axis", 0, 1, 0, test3[0], test3[1], test3[2]);
        }

        // Construire la liste des positions, types de blocs et rotations avec transformation
        List<int[]> positions = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();
        List<Integer> blockRotations = new ArrayList<>();

        // WorldEdit style: transform is applied around the PLAYER (origin = 0,0,0)
        int offsetX = clipboard.getOffsetX();
        int offsetY = clipboard.getOffsetY();
        int offsetZ = clipboard.getOffsetZ();

        int debugBlockIndex = 0;
        boolean flipX = transform.isFlipX();
        boolean flipZ = transform.isFlipZ();
        boolean vFlip = transform.isVerticalFlip();

        // Itérer sur tout le volume du clipboard
        int clipWidth = clipboard.getWidth();
        int clipHeight = clipboard.getHeight();
        int clipDepth = clipboard.getDepth();

        // === Construire les listes de blocs en 2 passes : AIR d'abord, SOLIDES ensuite ===
        // L'air est placé en premier pour nettoyer la zone, puis les blocs solides sont placés.
        // Cela évite que l'air du clipboard écrase les fillers auto-créés par Hytale
        // lorsque des blocs multi-part (lanternes, bancs...) sont placés.
        // Pas besoin de prédire les positions filler : elles n'existent pas encore quand l'air est posé.

        // Première passe : collecter l'air
        for (int cx = 0; cx < clipWidth; cx++) {
            for (int cy = 0; cy < clipHeight; cy++) {
                for (int cz = 0; cz < clipDepth; cz++) {
                    String blockType = clipboard.getBlock(cx, cy, cz);
                    boolean isAir = blockType == null || "air".equalsIgnoreCase(blockType);

                    if (!isAir || skipAir) continue;

                    double relX = offsetX + cx;
                    double relY = offsetY + cy;
                    double relZ = offsetZ + cz;
                    double[] transformed = transform.apply(relX, relY, relZ);
                    for (int i = 0; i < 3; i++) {
                        double rounded = Math.round(transformed[i]);
                        if (Math.abs(transformed[i] - rounded) < 1e-8) transformed[i] = rounded;
                    }
                    int worldX = playerX + (int) Math.floor(transformed[0]);
                    int worldY = playerY + (int) Math.floor(transformed[1]);
                    int worldZ = playerZ + (int) Math.floor(transformed[2]);

                    positions.add(new int[]{worldX, worldY, worldZ});
                    blockTypes.add("air");
                    blockRotations.add(0);
                    debugBlockIndex++;
                }
            }
        }

        int airCount = positions.size();

        // Deuxième passe : collecter les blocs solides
        for (int cx = 0; cx < clipWidth; cx++) {
            for (int cy = 0; cy < clipHeight; cy++) {
                for (int cz = 0; cz < clipDepth; cz++) {
                    String blockType = clipboard.getBlock(cx, cy, cz);
                    boolean isAir = blockType == null || "air".equalsIgnoreCase(blockType);

                    if (isAir) continue;

                    double relX = offsetX + cx;
                    double relY = offsetY + cy;
                    double relZ = offsetZ + cz;
                    double[] transformed = transform.apply(relX, relY, relZ);
                    for (int i = 0; i < 3; i++) {
                        double rounded = Math.round(transformed[i]);
                        if (Math.abs(transformed[i] - rounded) < 1e-8) transformed[i] = rounded;
                    }
                    int worldX = playerX + (int) Math.floor(transformed[0]);
                    int worldY = playerY + (int) Math.floor(transformed[1]);
                    int worldZ = playerZ + (int) Math.floor(transformed[2]);

                    // Transformer le nom du bloc pour les flips (Corner_Left <-> Corner_Right)
                    if (transform.isFlipX() ^ transform.isFlipZ()) {
                        blockType = transformBlockName(blockType);
                    }

                    // Récupérer et transformer la rotation
                    int originalRotation = clipboard.getRotation(cx, cy, cz);
                    int transformedRotation = transformRotation(originalRotation, transform, blockType);

                    // Compensation de position pour les blocs multi-part.
                    //
                    // Le swap COMPLET (0<->2 + 1<->3) inverse TOUTES les directions du filler.
                    // Le miroir ne gère qu'un seul axe (X pour flipX, Z pour flipZ).
                    // L'axe NON-miroir qui s'inverse est un effet secondaire → compensation.
                    //
                    // Directions filler par yaw (conceptuel au yaw 0: W=width, D=depth):
                    //   yaw 0: +X(W), +Z(D), +Y(H)
                    //   yaw 1: +Z(W), -X(D), +Y(H)
                    //   yaw 2: -X(W), -Z(D), +Y(H)
                    //   yaw 3: -Z(W), +X(D), +Y(H)
                    //
                    // FlipX : miroir X gère l'inversion X. L'inversion Z est l'effet secondaire.
                    //   swap 0<->2: Z(D) s'inverse -> compenser Z ± (conceptDepth-1)
                    //   swap 1<->3: Z(W) s'inverse -> compenser Z ± (conceptWidth-1)
                    //
                    // FlipZ : miroir Z gère l'inversion Z. L'inversion X est l'effet secondaire.
                    //   swap 0<->2: X(W) s'inverse -> compenser X ± (conceptWidth-1)
                    //   swap 1<->3: X(D) s'inverse -> compenser X ± (conceptDepth-1)
                    //
                    // vFlip: filler Y toujours en +Y -> worldY += (height-1)
                    {
                        BlockSizeHelper.BlockSizeInfo baseSizeInfo = BlockSizeHelper.getBlockSize(blockType, 0);
                        if (baseSizeInfo != null && baseSizeInfo.isMultiPart()) {
                            int origYaw = originalRotation % 4;
                            int transYaw = transformedRotation % 4;
                            int cW = baseSizeInfo.gridWidth();   // conceptual width
                            int cD = baseSizeInfo.gridDepth();   // conceptual depth
                            int cH = baseSizeInfo.gridHeight();  // conceptual height

                            if (flipX && origYaw != transYaw) {
                                // FlipX: compenser l'inversion Z (effet secondaire)
                                if (origYaw == 0 && transYaw == 2) {
                                    // D: +Z → -Z -> compenser Z += (cD-1)
                                    if (cD > 1) worldZ += (cD - 1);
                                } else if (origYaw == 2 && transYaw == 0) {
                                    // D: -Z → +Z -> compenser Z -= (cD-1)
                                    if (cD > 1) worldZ -= (cD - 1);
                                } else if (origYaw == 1 && transYaw == 3) {
                                    // W: +Z → -Z -> compenser Z += (cW-1)
                                    if (cW > 1) worldZ += (cW - 1);
                                } else if (origYaw == 3 && transYaw == 1) {
                                    // W: -Z → +Z -> compenser Z -= (cW-1)
                                    if (cW > 1) worldZ -= (cW - 1);
                                }
                                if (dbg != null) {
                                    dbg.log("PASTE", "  Multi-part flipX comp: " + blockType
                                            + " origYaw=" + origYaw + " transYaw=" + transYaw
                                            + " cW=" + cW + " cD=" + cD
                                            + " -> world=(" + worldX + "," + worldY + "," + worldZ + ")");
                                }
                            }

                            if (flipZ && origYaw != transYaw) {
                                // FlipZ: compenser l'inversion X (effet secondaire)
                                if (origYaw == 0 && transYaw == 2) {
                                    // W: +X → -X -> compenser X += (cW-1)
                                    if (cW > 1) worldX += (cW - 1);
                                } else if (origYaw == 2 && transYaw == 0) {
                                    // W: -X → +X -> compenser X -= (cW-1)
                                    if (cW > 1) worldX -= (cW - 1);
                                } else if (origYaw == 1 && transYaw == 3) {
                                    // D: -X → +X -> compenser X -= (cD-1)
                                    if (cD > 1) worldX -= (cD - 1);
                                } else if (origYaw == 3 && transYaw == 1) {
                                    // D: +X → -X -> compenser X += (cD-1)
                                    if (cD > 1) worldX += (cD - 1);
                                }
                                if (dbg != null) {
                                    dbg.log("PASTE", "  Multi-part flipZ comp: " + blockType
                                            + " origYaw=" + origYaw + " transYaw=" + transYaw
                                            + " cW=" + cW + " cD=" + cD
                                            + " -> world=(" + worldX + "," + worldY + "," + worldZ + ")");
                                }
                            }

                            if (vFlip) {
                                if (cH > 1) {
                                    worldY += (cH - 1);
                                    if (dbg != null) {
                                        dbg.log("PASTE", "  Multi-part vFlip: " + blockType
                                                + " gh=" + cH + " -> world=(" + worldX + "," + worldY + "," + worldZ + ")");
                                    }
                                }
                            }
                        }
                    }

                    positions.add(new int[]{worldX, worldY, worldZ});
                    blockTypes.add(blockType);
                    blockRotations.add(transformedRotation);

                    // Log uniquement les blocs non-air avec rotation (blocs orientés) + filtre
                    if (dbg != null && originalRotation != 0 && dbg.matchesBlockFilter(blockType)) {
                        dbg.logPasteBlock(debugBlockIndex, cx, cy, cz,
                                relX, relY, relZ,
                                transformed[0], transformed[1], transformed[2],
                                worldX, worldY, worldZ,
                                blockType, originalRotation, transformedRotation);
                    }
                    debugBlockIndex++;
                }
            }
        }

        if (dbg != null) {
            dbg.log("PASTE", "Total blocks to paste: " + positions.size() + " (air first: " + airCount + ", solids: " + (positions.size() - airCount) + ")");
        }

        CompletableFuture<BlockOperations.OperationResult> future = new CompletableFuture<>();
        EditAction action = new EditAction("Paste clipboard");
        String worldId = world.getName();

        final int totalToPaste = positions.size();
        processBlocksWithRotations(world, worldId, positions, blockTypes, blockRotations, action, result -> {
            if (result.success() && !action.isEmpty()) {
                plugin.getEditHistory().pushAction(player.getUuid(), action);
            }
            // Debug log paste end
            DebugLogger dbg2 = DebugLogger.get();
            if (dbg2 != null) {
                dbg2.logPasteEnd(totalToPaste, result.blocksAffected(),
                        result.success() ? 0 : totalToPaste - result.blocksAffected());
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

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logRotate(player.getDisplayName(), degrees, oldTransform, newTransform);
            dbg.logClipboardState("after rotate", holder);
        }

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

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logFlip(player.getDisplayName(), axis, oldTransform, flipTransform);
            dbg.logClipboardState("after flip", holder);
        }

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
        AffineTransform oldTransform = holder.getTransform();
        AffineTransform flipTransform = switch (axis) {
            case "x" -> oldTransform.scale(-1, 1, 1);
            case "y" -> oldTransform.scale(1, -1, 1);
            case "z" -> oldTransform.scale(1, 1, -1);
            default -> oldTransform;
        };

        holder.setTransform(flipTransform);

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logFlipByLook(player.getDisplayName(), pitch, yaw, dirX, dirY, dirZ,
                    axis, direction, oldTransform, flipTransform);
            dbg.logClipboardState("after flipByLook", holder);
        }

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
        AffineTransform oldTransform = holder.getTransform();
        AffineTransform flipTransform = switch (axis) {
            case "x" -> oldTransform.scale(-1, 1, 1);
            case "y" -> oldTransform.scale(1, -1, 1);
            case "z" -> oldTransform.scale(1, 1, -1);
            default -> oldTransform;
        };

        holder.setTransform(flipTransform);

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logFlip(player.getDisplayName(), axis + " (auto)", oldTransform, flipTransform);
            dbg.logClipboardState("after flipAuto", holder);
        }

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

        AffineTransform oldTransform = holder.getTransform();
        holder.setTransform(new AffineTransform());

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logSection("RESET TRANSFORM - " + player.getDisplayName());
            dbg.log("RESET", "Old transform: " + oldTransform);
            dbg.log("RESET", "New transform: identity");
        }

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
     * Transforme le nom d'un bloc lors d'un flip (miroir).
     * Inverse Corner_Left <-> Corner_Right et Inverted_Corner_Left <-> Inverted_Corner_Right.
     */
    @NotNull
    public static String transformBlockName(@NotNull String blockType) {
        // Utiliser des marqueurs temporaires pour éviter les doubles remplacements
        if (blockType.contains("Inverted_Corner_Left")) {
            return blockType.replace("Inverted_Corner_Left", "Inverted_Corner_Right");
        } else if (blockType.contains("Inverted_Corner_Right")) {
            return blockType.replace("Inverted_Corner_Right", "Inverted_Corner_Left");
        } else if (blockType.contains("Corner_Left")) {
            return blockType.replace("Corner_Left", "Corner_Right");
        } else if (blockType.contains("Corner_Right")) {
            return blockType.replace("Corner_Right", "Corner_Left");
        }
        return blockType;
    }

    /**
     * Transforme un index de rotation Hytale (0-63) selon la transformation appliquée.
     *
     * L'index de rotation Hytale encode yaw/pitch/roll avec 4 valeurs chacun (0, 90, 180, 270).
     * Index = yaw + pitch*4 + roll*16 (approximativement)
     *
     * Pour un flip ou rotation, on transforme les composantes individuellement.
     */
    public int transformRotation(int rotationIndex, @NotNull AffineTransform transform, @NotNull String blockType) {
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
        boolean vFlip = transform.isVerticalFlip();

        // Appliquer la rotation Y du transform
        int yRotation = transform.getYRotation();
        if (yRotation != 0) {
            // Ajouter la rotation (chaque step = 90°)
            int steps = (yRotation / 90) % 4;
            yaw = (yaw + steps) % 4;
        }

        // Appliquer les flips
        RotationOverrides overrides = RotationOverrides.get();
        boolean useNativeFlip = overrides != null && overrides.isUseNativeFlip();

        // Détection multipart via getBlockSize (plus fiable que isMultiPart qui utilise
        // seulement protrudesUnitBox). On utilise la rotation ACTUELLE pour la détection.
        BlockSizeHelper.BlockSizeInfo sizeInfo = BlockSizeHelper.getBlockSize(blockType, rotationIndex);
        boolean isMultiPart = sizeInfo != null && sizeInfo.isMultiPart();

        // === FlipX (miroir est/ouest) ===
        // Pour les blocs multi-part : swap COMPLET 0<->2 ET 1<->3.
        //   Un miroir X inverse la direction X. Pour les multipart, cela affecte :
        //   - yaw 0/2 : le width (axe X) s'inverse -> swap 0<->2
        //   - yaw 1/3 : le depth (axe X) s'inverse -> swap 1<->3
        //   Les dimensions non-miroir (Z) s'inversent aussi (effet secondaire du swap).
        //   La compensation de position corrige cet effet secondaire dans le paste.
        // Pour les blocs normaux : swap standard 1<->3 (Est<->Ouest) + overrides éventuels.
        if (flipX) {
            if (isMultiPart) {
                // Multi-part: swap COMPLET (0<->2 et 1<->3)
                if (yaw == 0) yaw = 2;
                else if (yaw == 2) yaw = 0;
                else if (yaw == 1) yaw = 3;
                else if (yaw == 3) yaw = 1;
            } else if (useNativeFlip) {
                int nativeYaw = BlockSizeHelper.flipYawViaApi(blockType, yaw, "x");
                if (nativeYaw >= 0) {
                    yaw = nativeYaw;
                } else {
                    if (yaw == 1) yaw = 3;
                    else if (yaw == 3) yaw = 1;
                }
            } else {
                boolean skipStandard = overrides != null && overrides.shouldReplaceStandardFlipX(blockType);
                if (!skipStandard) {
                    if (yaw == 1) yaw = 3;
                    else if (yaw == 3) yaw = 1;
                }
                if (overrides != null) {
                    yaw = overrides.applyFlipXYaw(yaw, blockType);
                }
            }
        }

        // === FlipZ (miroir nord/sud) ===
        // Pour les blocs multi-part : swap COMPLET 0<->2 ET 1<->3.
        //   Un miroir Z inverse la direction Z. Pour les multipart, cela affecte :
        //   - yaw 1/3 : le width (axe Z) s'inverse -> swap 1<->3
        //   - yaw 0/2 : le depth (axe Z) s'inverse -> swap 0<->2
        //   Les dimensions non-miroir (X) s'inversent aussi (effet secondaire du swap).
        //   La compensation de position corrige cet effet secondaire dans le paste.
        // Pour les blocs normaux : swap standard 0<->2 (Nord<->Sud) + overrides éventuels.
        if (flipZ) {
            if (isMultiPart) {
                // Multi-part: swap COMPLET (0<->2 et 1<->3)
                if (yaw == 0) yaw = 2;
                else if (yaw == 2) yaw = 0;
                else if (yaw == 1) yaw = 3;
                else if (yaw == 3) yaw = 1;
            } else if (useNativeFlip) {
                int nativeYaw = BlockSizeHelper.flipYawViaApi(blockType, yaw, "z");
                if (nativeYaw >= 0) {
                    yaw = nativeYaw;
                } else {
                    if (yaw == 0) yaw = 2;
                    else if (yaw == 2) yaw = 0;
                }
            } else {
                boolean skipStandard = overrides != null && overrides.shouldReplaceStandardFlipZ(blockType);
                if (!skipStandard) {
                    if (yaw == 0) yaw = 2;
                    else if (yaw == 2) yaw = 0;
                }
                if (overrides != null) {
                    yaw = overrides.applyFlipZYaw(yaw, blockType);
                }
            }
        }

        if (vFlip) {
            // Pas d'API native pour le flip vertical, toujours overrides manuels
            boolean skipStandard = overrides != null && overrides.shouldReplaceStandardFlipY(blockType);
            if (!skipStandard) {
                // Flip Y standard : inverse le pitch
                if (pitch == 1) pitch = 3;
                else if (pitch == 3) pitch = 1;
            }
            // Appliquer les overrides
            if (overrides != null) {
                pitch = overrides.applyFlipYPitch(pitch, blockType);
            }
        }

        int newIndex = yaw + pitch * 4 + roll * 16;

        // Debug log (seulement si rotation originale != 0, et pas air) + filtre
        if (rotationIndex != 0 && !"air".equalsIgnoreCase(blockType)) {
            DebugLogger dbg = DebugLogger.get();
            if (dbg != null && dbg.matchesBlockFilter(blockType)) {
                int origYaw = rotationIndex % 4;
                int origPitch = (rotationIndex / 4) % 4;
                int origRoll = (rotationIndex / 16) % 4;
                dbg.logRotationTransform(rotationIndex, origYaw, origPitch, origRoll,
                        yRotation, flipX, flipZ, vFlip,
                        yaw, pitch, roll, newIndex);
            }
        }

        return newIndex;
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
                            // Lire l'ancien type et rotation AVANT de modifier
                            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos[0], pos[2]);
                            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);

                            BlockType oldBt = world.getBlockType(pos[0], pos[1], pos[2]);
                            String oldType = (oldBt != null && oldBt != BlockType.EMPTY)
                                    ? oldBt.getId() : "air";
                            int oldRotation = 0;
                            if (chunk != null && !"air".equalsIgnoreCase(oldType)) {
                                oldRotation = chunk.getRotationIndex(pos[0], pos[1], pos[2]);
                            }

                            // Skip si le bloc est identique (type + rotation)
                            if (oldType.equals(blockType) && oldRotation == rotation) {
                                processed[0]++;
                                continue;
                            }

                            // Pour "air", utiliser breakBlock au lieu de setBlock
                            if ("air".equalsIgnoreCase(blockType)) {
                                // Skip air -> air
                                if ("air".equalsIgnoreCase(oldType)) {
                                    processed[0]++;
                                    continue;
                                }
                                world.breakBlock(pos[0], pos[1], pos[2], 0);
                            } else {
                                // Utiliser le chunk pour placer le bloc avec rotation
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
                            action.addChange(worldId, pos[0], pos[1], pos[2], oldType, blockType,
                                    oldRotation, rotation);
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
     * Utilise le systeme natif de copie de Hytale (computeSelectionCopy) pour remplir
     * le clipboard du BuilderState. Cela active la preview du Paste Tool natif
     * avec les vrais blocs en transparent.
     * La position du joueur est utilisee comme point d'origine pour le placement relatif.
     */
    @SuppressWarnings("deprecation")
    private void sendClipboardPreview(@NotNull Player player, @NotNull World world,
                                       int[] bounds, int playerX, int playerY, int playerZ) {
        BuilderToolsPlugin.BuilderState state = plugin.getSelectionManager().getBuilderState(player);
        if (state == null) return;

        // computeSelectionCopy cree une nouvelle BlockSelection, appelle notre consumer
        // pour la remplir avec les blocs, puis envoie sendUpdate() au client
        state.computeSelectionCopy(selection -> {
            // Definir les bounds de la selection
            selection.setSelectionArea(
                new Vector3i(bounds[0], bounds[1], bounds[2]),
                new Vector3i(bounds[3], bounds[4], bounds[5])
            );

            // Position du joueur comme point d'origine (pour le placement relatif du Paste Tool)
            selection.setPosition(playerX, playerY, playerZ);

            // Copier les blocs depuis le monde chunk par chunk
            for (int bx = bounds[0]; bx <= bounds[3]; bx++) {
                for (int bz = bounds[2]; bz <= bounds[5]; bz++) {
                    long chunkIdx = ChunkUtil.indexChunkFromBlock(bx, bz);
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
                    if (chunk != null) {
                        for (int by = bounds[1]; by <= bounds[4]; by++) {
                            selection.copyFromAtWorld(bx, by, bz, chunk, null);
                        }
                    }
                }
            }
        });
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
