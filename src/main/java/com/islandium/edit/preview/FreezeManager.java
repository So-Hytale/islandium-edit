package com.islandium.edit.preview;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.operation.ClipboardHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire de freeze: place les vrais blocs du clipboard dans le monde
 * a une position fixe pour permettre au joueur de se deplacer et inspecter.
 * Sauvegarde les blocs originaux pour restauration.
 */
public class FreezeManager {

    private final EditPlugin plugin;
    private final Map<UUID, FreezeSession> frozenSessions;

    public FreezeManager(@NotNull EditPlugin plugin) {
        this.plugin = plugin;
        this.frozenSessions = new ConcurrentHashMap<>();
    }

    /**
     * Fige le clipboard a la position actuelle du joueur.
     * Place les vrais blocs et sauvegarde les originaux.
     *
     * @return nombre de blocs places, ou -1 si erreur
     */
    @SuppressWarnings("deprecation")
    public int freeze(@NotNull Player player) {
        ClipboardHolder holder = plugin.getClipboardOperations().getClipboardHolder(player);
        if (holder == null || holder.isEmpty()) {
            return -1;
        }

        World world;
        try {
            world = player.getWorld();
        } catch (Exception e) {
            return -1;
        }
        if (world == null) return -1;

        var transformComponent = player.getTransformComponent();
        if (transformComponent == null) return -1;

        var pos = transformComponent.getPosition();
        int playerX = (int) Math.floor(pos.getX());
        int playerY = (int) Math.floor(pos.getY());
        int playerZ = (int) Math.floor(pos.getZ());

        ClipboardData clipboard = holder.getClipboard();
        AffineTransform transform = holder.getTransform();

        int offsetX = clipboard.getOffsetX();
        int offsetY = clipboard.getOffsetY();
        int offsetZ = clipboard.getOffsetZ();

        // Calculer toutes les positions et stocker les blocs originaux
        List<int[]> positions = new ArrayList<>();
        List<String> newBlockTypes = new ArrayList<>();
        List<Integer> newRotations = new ArrayList<>();
        List<String> originalBlockTypes = new ArrayList<>();
        List<Integer> originalRotations = new ArrayList<>();

        int clipWidth = clipboard.getWidth();
        int clipHeight = clipboard.getHeight();
        int clipDepth = clipboard.getDepth();

        for (int cx = 0; cx < clipWidth; cx++) {
            for (int cy = 0; cy < clipHeight; cy++) {
                for (int cz = 0; cz < clipDepth; cz++) {
                    String blockType = clipboard.getBlock(cx, cy, cz);
                    boolean isAir = blockType == null || "air".equalsIgnoreCase(blockType);

                    // Ignorer l'air pour le freeze (on ne veut pas ecraser avec de l'air)
                    if (isAir) continue;

                    // Position relative au joueur
                    double relX = offsetX + cx;
                    double relY = offsetY + cy;
                    double relZ = offsetZ + cz;

                    // Appliquer la transformation
                    double[] transformed = transform.apply(relX, relY, relZ);

                    // Corriger precision flottante
                    for (int i = 0; i < 3; i++) {
                        double rounded = Math.round(transformed[i]);
                        if (Math.abs(transformed[i] - rounded) < 1e-8) {
                            transformed[i] = rounded;
                        }
                    }

                    int worldX = playerX + (int) Math.floor(transformed[0]);
                    int worldY = playerY + (int) Math.floor(transformed[1]);
                    int worldZ = playerZ + (int) Math.floor(transformed[2]);

                    // Transformer le nom du bloc pour les flips
                    if (transform.isFlipX() ^ transform.isFlipZ()) {
                        blockType = plugin.getClipboardOperations().transformBlockName(blockType);
                    }

                    // Transformer la rotation
                    int originalRotation = clipboard.getRotation(cx, cy, cz);
                    int transformedRotation = plugin.getClipboardOperations()
                            .transformRotation(originalRotation, transform, blockType);

                    // Lire le bloc original a cette position
                    String origType = "air";
                    int origRot = 0;
                    try {
                        BlockType oldBt = world.getBlockType(worldX, worldY, worldZ);
                        if (oldBt != null && oldBt != BlockType.EMPTY) {
                            origType = oldBt.getId();
                        }
                        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
                        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                        if (chunk != null && !"air".equalsIgnoreCase(origType)) {
                            origRot = chunk.getRotationIndex(worldX, worldY, worldZ);
                        }
                    } catch (Exception e) {
                        // Ignorer
                    }

                    positions.add(new int[]{worldX, worldY, worldZ});
                    newBlockTypes.add(blockType);
                    newRotations.add(transformedRotation);
                    originalBlockTypes.add(origType);
                    originalRotations.add(origRot);
                }
            }
        }

        // Sauvegarder la session
        FreezeSession session = new FreezeSession(
                playerX, playerY, playerZ,
                positions, originalBlockTypes, originalRotations
        );
        frozenSessions.put(player.getUuid(), session);

        // Placer les vrais blocs
        int placed = 0;
        for (int i = 0; i < positions.size(); i++) {
            int[] p = positions.get(i);
            String blockType = newBlockTypes.get(i);
            int rotation = newRotations.get(i);

            try {
                placeBlock(world, p[0], p[1], p[2], blockType, rotation);
                placed++;
            } catch (Exception e) {
                plugin.log(Level.WARNING, "[FREEZE] Erreur placement: " + e.getMessage());
            }
        }

        return placed;
    }

    /**
     * Defige: restaure les blocs originaux.
     *
     * @return nombre de blocs restaures, ou -1 si aucun freeze actif
     */
    @SuppressWarnings("deprecation")
    public int unfreeze(@NotNull Player player) {
        FreezeSession session = frozenSessions.remove(player.getUuid());
        if (session == null) return -1;

        // Aussi defiger la position dans le PreviewManager
        plugin.getPreviewManager().unfreeze(player);

        World world;
        try {
            world = player.getWorld();
        } catch (Exception e) {
            return -1;
        }
        if (world == null) return -1;

        int restored = 0;
        List<int[]> positions = session.positions;
        List<String> origTypes = session.originalBlockTypes;
        List<Integer> origRotations = session.originalRotations;

        for (int i = 0; i < positions.size(); i++) {
            int[] p = positions.get(i);
            String origType = origTypes.get(i);
            int origRot = origRotations.get(i);

            try {
                if ("air".equalsIgnoreCase(origType)) {
                    world.breakBlock(p[0], p[1], p[2], 0);
                } else {
                    placeBlock(world, p[0], p[1], p[2], origType, origRot);
                }
                restored++;
            } catch (Exception e) {
                plugin.log(Level.WARNING, "[UNFREEZE] Erreur restauration: " + e.getMessage());
            }
        }

        return restored;
    }

    /**
     * Verifie si un freeze est actif pour ce joueur.
     */
    @SuppressWarnings("deprecation")
    public boolean isFrozen(@NotNull Player player) {
        return frozenSessions.containsKey(player.getUuid());
    }

    /**
     * Retourne la position figee (pour le paste).
     */
    @SuppressWarnings("deprecation")
    public int[] getFrozenPosition(@NotNull Player player) {
        FreezeSession session = frozenSessions.get(player.getUuid());
        return session != null ? new int[]{session.playerX, session.playerY, session.playerZ} : null;
    }

    public void shutdown() {
        frozenSessions.clear();
    }

    /**
     * Place un bloc avec rotation dans le monde.
     */
    private void placeBlock(@NotNull World world, int x, int y, int z,
                             @NotNull String blockType, int rotation) {
        if ("air".equalsIgnoreCase(blockType)) {
            world.breakBlock(x, y, z, 0);
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);

        if (chunk != null && rotation != 0) {
            BlockType bt = BlockType.getAssetMap().getAsset(blockType);
            if (bt != null) {
                world.setBlock(x, y, z, blockType);
                int blockId = chunk.getBlock(x, y, z);
                if (blockId > 0) {
                    chunk.setBlock(x, y, z, blockId, bt, rotation, 0, 0);
                }
            } else {
                world.setBlock(x, y, z, blockType);
            }
        } else {
            world.setBlock(x, y, z, blockType);
        }
    }

    /**
     * Session de freeze stockant les positions et blocs originaux.
     */
    private static class FreezeSession {
        final int playerX, playerY, playerZ;
        final List<int[]> positions;
        final List<String> originalBlockTypes;
        final List<Integer> originalRotations;

        FreezeSession(int playerX, int playerY, int playerZ,
                      List<int[]> positions,
                      List<String> originalBlockTypes,
                      List<Integer> originalRotations) {
            this.playerX = playerX;
            this.playerY = playerY;
            this.playerZ = playerZ;
            this.positions = positions;
            this.originalBlockTypes = originalBlockTypes;
            this.originalRotations = originalRotations;
        }
    }
}
