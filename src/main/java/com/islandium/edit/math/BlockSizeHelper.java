package com.islandium.edit.math;

import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes.RotatedVariantBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFlipType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.math.shape.Box;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilitaire pour détecter les dimensions d'un bloc via l'API Hytale.
 *
 * Utilise BlockBoundingBoxes pour déterminer si un bloc est multi-part
 * (occupe plus de 1x1x1 dans le monde) et quelles sont ses dimensions réelles.
 *
 * Utilise BlockFlipType pour déterminer le comportement de flip d'un bloc
 * (ORTHOGONAL = asymétrique, SYMMETRIC = symétrique).
 */
public class BlockSizeHelper {

    /**
     * Informations sur la taille d'un bloc.
     */
    public record BlockSizeInfo(
            @NotNull String blockId,
            double width,
            double height,
            double depth,
            boolean protrudesUnitBox,
            boolean isMultiPart,
            @Nullable BlockFlipType flipType,
            boolean isState
    ) {
        /**
         * @return nombre de positions de grille occupées (arrondi supérieur)
         */
        public int gridWidth() {
            return (int) Math.ceil(width);
        }

        public int gridHeight() {
            return (int) Math.ceil(height);
        }

        public int gridDepth() {
            return (int) Math.ceil(depth);
        }

        /**
         * @return nombre total de positions de grille occupées
         */
        public int totalGridPositions() {
            return gridWidth() * gridHeight() * gridDepth();
        }

        /**
         * @return true si le bloc nécessite un traitement spécial pour le flip (asymétrique)
         */
        public boolean isOrthogonalFlip() {
            return flipType == BlockFlipType.ORTHOGONAL;
        }

        /**
         * @return true si le bloc est symétrique (pas de traitement spécial pour flip)
         */
        public boolean isSymmetricFlip() {
            return flipType == BlockFlipType.SYMMETRIC;
        }

        @Override
        public String toString() {
            return "BlockSizeInfo{" + blockId
                    + " size=" + String.format("%.2f", width) + "x" + String.format("%.2f", height) + "x" + String.format("%.2f", depth)
                    + " grid=" + gridWidth() + "x" + gridHeight() + "x" + gridDepth()
                    + " protrudes=" + protrudesUnitBox
                    + " multiPart=" + isMultiPart
                    + " flip=" + flipType
                    + " state=" + isState
                    + "}";
        }
    }

    /**
     * Obtient les informations de taille d'un bloc par son ID, pour une rotation donnée.
     *
     * @param blockId     ID du bloc (ex: "Chandelier")
     * @param rotationIndex index de rotation (0-63), 0 pour rotation par défaut
     * @return les informations de taille, ou null si le bloc n'existe pas
     */
    @Nullable
    public static BlockSizeInfo getBlockSize(@NotNull String blockId, int rotationIndex) {
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt == null) return null;
        return getBlockSize(bt, rotationIndex);
    }

    /**
     * Obtient les informations de taille d'un BlockType, pour une rotation donnée.
     */
    @Nullable
    public static BlockSizeInfo getBlockSize(@NotNull BlockType bt, int rotationIndex) {
        try {
            int hitboxIndex = bt.getHitboxTypeIndex();
            BlockBoundingBoxes bboxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxIndex);

            if (bboxes == null) {
                // Pas de hitbox définie, on suppose un bloc 1x1x1
                return new BlockSizeInfo(
                        bt.getId(), 1.0, 1.0, 1.0, false, false,
                        bt.getFlipType(), bt.isState()
                );
            }

            boolean protrudes = bboxes.protrudesUnitBox();
            RotatedVariantBoxes variant = bboxes.get(rotationIndex);

            double width = 1.0, height = 1.0, depth = 1.0;
            if (variant != null) {
                Box bbox = variant.getBoundingBox();
                if (bbox != null) {
                    width = bbox.width();
                    height = bbox.height();
                    depth = bbox.depth();
                }
            }

            boolean isMultiPart = protrudes || width > 1.0 || height > 1.0 || depth > 1.0;

            return new BlockSizeInfo(
                    bt.getId(), width, height, depth, protrudes, isMultiPart,
                    bt.getFlipType(), bt.isState()
            );
        } catch (Exception e) {
            // Si l'API échoue (version incompatible, etc.), retourner un bloc 1x1x1
            return new BlockSizeInfo(
                    bt.getId(), 1.0, 1.0, 1.0, false, false,
                    null, false
            );
        }
    }

    /**
     * Vérifie rapidement si un bloc est multi-part (sans calculer les dimensions exactes).
     */
    public static boolean isMultiPart(@NotNull String blockId) {
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt == null) return false;
        return isMultiPart(bt);
    }

    /**
     * Vérifie rapidement si un BlockType est multi-part.
     */
    public static boolean isMultiPart(@NotNull BlockType bt) {
        try {
            int hitboxIndex = bt.getHitboxTypeIndex();
            BlockBoundingBoxes bboxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxIndex);
            return bboxes != null && bboxes.protrudesUnitBox();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Vérifie si un bloc utilise le flip orthogonal (asymétrique).
     * Ces blocs nécessitent un traitement spécial lors des flips.
     */
    public static boolean isOrthogonalFlip(@NotNull String blockId) {
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt == null) return false;
        BlockFlipType ft = bt.getFlipType();
        return ft == BlockFlipType.ORTHOGONAL;
    }

    /**
     * Obtient le BlockFlipType d'un bloc.
     */
    @Nullable
    public static BlockFlipType getFlipType(@NotNull String blockId) {
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt == null) return null;
        return bt.getFlipType();
    }

    // === Filler (positions secondaires des blocs multi-part) ===

    /**
     * Vérifie si une position est un filler (position secondaire d'un bloc multi-part).
     * Un filler est une position occupée par un bloc multi-part qui n'est pas l'origine.
     *
     * @param fillerValue valeur retournée par chunk.getFiller(x, y, z)
     * @return true si c'est un filler (pas l'origine)
     */
    public static boolean isFiller(int fillerValue) {
        return fillerValue != FillerBlockUtil.NO_FILLER;
    }

    /**
     * Décode la valeur filler en offsets vers le bloc origine.
     *
     * @param fillerValue valeur retournée par chunk.getFiller(x, y, z)
     * @return tableau [dx, dy, dz] où origine = (x - dx, y - dy, z - dz), ou null si pas un filler
     */
    @Nullable
    public static int[] getFillerOffset(int fillerValue) {
        if (fillerValue == FillerBlockUtil.NO_FILLER) return null;
        return new int[]{
                FillerBlockUtil.unpackX(fillerValue),
                FillerBlockUtil.unpackY(fillerValue),
                FillerBlockUtil.unpackZ(fillerValue)
        };
    }

    // === BlockFlipType API pour les rotations ===

    /**
     * Utilise l'API native BlockFlipType.flipYaw() pour calculer le nouveau yaw après un flip.
     *
     * @param blockId   ID du bloc
     * @param yaw       yaw actuel (0-3 : 0=0°, 1=90°, 2=180°, 3=270°)
     * @param flipAxis  axe du flip ("x" ou "z")
     * @return nouveau yaw après flip, ou -1 si l'API n'est pas disponible
     */
    public static int flipYawViaApi(@NotNull String blockId, int yaw, @NotNull String flipAxis) {
        try {
            BlockType bt = BlockType.getAssetMap().getAsset(blockId);
            if (bt == null) return -1;

            BlockFlipType flipType = bt.getFlipType();
            if (flipType == null) return -1;

            // Convertir yaw (0-3) en Rotation enum
            Rotation yawRotation = Rotation.VALUES[yaw % 4];

            // Convertir l'axe en Axis enum
            Axis axis = switch (flipAxis.toLowerCase()) {
                case "x" -> Axis.X;
                case "z" -> Axis.Z;
                default -> null;
            };
            if (axis == null) return -1;

            // Appel API native
            Rotation flipped = flipType.flipYaw(yawRotation, axis);
            if (flipped == null) return -1;

            // Convertir le résultat en int (0-3)
            return flipped.ordinal();
        } catch (Exception e) {
            return -1;
        }
    }
}
