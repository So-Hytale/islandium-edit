package com.islandium.edit.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Données du clipboard pour le copy/paste.
 * Stocke les blocs copiés avec leur position relative.
 */
public class ClipboardData {

    private final int width;
    private final int height;
    private final int depth;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    // Offset original (celui du copy, ne change jamais)
    private final int originalOffsetX;
    private final int originalOffsetY;
    private final int originalOffsetZ;
    private final Map<String, String> blocks; // "x,y,z" -> blockType
    private final Map<String, Integer> rotations; // "x,y,z" -> rotationIndex (0-63)

    public ClipboardData(int width, int height, int depth, int offsetX, int offsetY, int offsetZ) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        // Par défaut, l'offset original = offset actuel
        this.originalOffsetX = offsetX;
        this.originalOffsetY = offsetY;
        this.originalOffsetZ = offsetZ;
        this.blocks = new HashMap<>();
        this.rotations = new HashMap<>();
    }

    // Constructeur avec offset original séparé
    public ClipboardData(int width, int height, int depth, int offsetX, int offsetY, int offsetZ,
                         int originalOffsetX, int originalOffsetY, int originalOffsetZ) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.originalOffsetX = originalOffsetX;
        this.originalOffsetY = originalOffsetY;
        this.originalOffsetZ = originalOffsetZ;
        this.blocks = new HashMap<>();
        this.rotations = new HashMap<>();
    }

    /**
     * Définit un bloc dans le clipboard (sans rotation).
     */
    public void setBlock(int x, int y, int z, @NotNull String blockType) {
        blocks.put(makeKey(x, y, z), blockType);
    }

    /**
     * Définit un bloc dans le clipboard avec sa rotation.
     * @param rotationIndex index de rotation Hytale (0-63)
     */
    public void setBlock(int x, int y, int z, @NotNull String blockType, int rotationIndex) {
        String key = makeKey(x, y, z);
        blocks.put(key, blockType);
        if (rotationIndex != 0) {
            rotations.put(key, rotationIndex);
        }
    }

    /**
     * Obtient un bloc du clipboard.
     */
    @Nullable
    public String getBlock(int x, int y, int z) {
        return blocks.get(makeKey(x, y, z));
    }

    /**
     * Obtient la rotation d'un bloc du clipboard.
     * @return index de rotation (0-63), 0 par défaut
     */
    public int getRotation(int x, int y, int z) {
        return rotations.getOrDefault(makeKey(x, y, z), 0);
    }

    /**
     * Obtient la rotation par clé.
     */
    public int getRotation(@NotNull String key) {
        return rotations.getOrDefault(key, 0);
    }

    /**
     * @return tous les blocs stockés
     */
    @NotNull
    public Map<String, String> getBlocks() {
        return blocks;
    }

    /**
     * @return toutes les rotations stockées
     */
    @NotNull
    public Map<String, Integer> getRotations() {
        return rotations;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public int getOriginalOffsetX() {
        return originalOffsetX;
    }

    public int getOriginalOffsetY() {
        return originalOffsetY;
    }

    public int getOriginalOffsetZ() {
        return originalOffsetZ;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * Détermine l'axe dominant basé sur la position relative de la copie.
     * Utilise le centre de la sélection par rapport au joueur pour déterminer
     * dans quelle direction la structure a été copiée.
     *
     * @return "x", "y" ou "z" selon l'axe dominant
     */
    @NotNull
    public String getDominantAxis() {
        // Calculer le centre de la sélection par rapport au joueur
        // Le centre est à offset + (dimension / 2)
        double centerX = offsetX + (width / 2.0);
        double centerY = offsetY + (height / 2.0);
        double centerZ = offsetZ + (depth / 2.0);

        double absX = Math.abs(centerX);
        double absY = Math.abs(centerY);
        double absZ = Math.abs(centerZ);

        // Si la structure est principalement au-dessus/en-dessous
        if (absY > absX && absY > absZ) {
            return "y";
        }
        // Si la structure est principalement à l'est/ouest
        if (absX > absZ) {
            return "x";
        }
        // Sinon, nord/sud
        return "z";
    }

    private static String makeKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Parse une clé en coordonnées.
     */
    public static int[] parseKey(@NotNull String key) {
        String[] parts = key.split(",");
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }

    /**
     * Crée une copie avec rotation de 90 degrés (sens horaire vu de dessus).
     *
     * WorldEdit rotation 90° CW around origin (player position):
     * (x, z) → (z, -x)
     *
     * For a box at (offsetX, offsetZ) with dimensions (width, depth):
     * - Min corner (offsetX, offsetZ) → (offsetZ, -offsetX)
     * - Max corner (offsetX+width-1, offsetZ+depth-1) → (offsetZ+depth-1, -(offsetX+width-1))
     * - New min X = min(offsetZ, offsetZ+depth-1) = offsetZ
     * - New min Z = min(-offsetX, -offsetX-width+1) = -offsetX - width + 1
     *
     * The offset formula uses CURRENT offset (not original) to allow stacking rotations.
     */
    @NotNull
    public ClipboardData rotate90() {
        // Calculer le nouvel offset basé sur l'offset ACTUEL
        // Rotation 90° CW: (offsetX, offsetZ) → (offsetZ, -offsetX - width + 1)
        int newOffsetX = offsetZ;
        int newOffsetY = offsetY;
        int newOffsetZ = -offsetX - width + 1;

        // Les dimensions s'échangent: (width, depth) → (depth, width)
        // Conserver l'offset original pour les opérations qui en ont besoin (flipByLookDirection)
        ClipboardData rotated = new ClipboardData(depth, height, width, newOffsetX, newOffsetY, newOffsetZ,
                originalOffsetX, originalOffsetY, originalOffsetZ);

        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            int[] coords = parseKey(entry.getKey());
            int oldX = coords[0];
            int oldZ = coords[2];

            // Rotation 90° CW dans le système de coordonnées local (0 à dim-1):
            // (x, z) → (z, width - 1 - x)
            int newX = oldZ;
            int newZ = width - 1 - oldX;

            rotated.setBlock(newX, coords[1], newZ, entry.getValue());
        }

        return rotated;
    }

    /**
     * Flip sur l'axe X (style WorldEdit).
     * 1. Déplace le clipboard de l'autre côté du joueur (symétrique par rapport à X=0)
     * 2. Inverse le contenu (miroir)
     *
     * WorldEdit flip uses scale(-1, 1, 1) which mirrors around the origin.
     * For a box at offsetX with width:
     * - Point at offsetX becomes -offsetX after scale
     * - Point at offsetX+width-1 becomes -(offsetX+width-1)
     * - New min = min(-offsetX, -(offsetX+width-1)) = -(offsetX+width-1) = -offsetX - width + 1
     */
    @NotNull
    public ClipboardData flipX() {
        // Flip symétrique basé sur l'offset ACTUEL
        // Le nouveau min est à -(ancien max) = -(offsetX + width - 1)
        int newOffsetX = -offsetX - width + 1;

        // Conserver l'offset original pour flipByLookDirection
        ClipboardData flipped = new ClipboardData(width, height, depth, newOffsetX, offsetY, offsetZ,
                originalOffsetX, originalOffsetY, originalOffsetZ);

        // Inverser le contenu sur l'axe X (miroir)
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            int[] coords = parseKey(entry.getKey());
            int mirroredX = width - 1 - coords[0];
            flipped.setBlock(mirroredX, coords[1], coords[2], entry.getValue());
        }

        return flipped;
    }

    /**
     * Flip sur l'axe Z (style WorldEdit).
     * 1. Déplace le clipboard de l'autre côté du joueur (symétrique par rapport à Z=0)
     * 2. Inverse le contenu (miroir)
     *
     * WorldEdit flip formula: newOffset = -(offset + dimension - 1) = -offset - dimension + 1
     */
    @NotNull
    public ClipboardData flipZ() {
        // Flip symétrique basé sur l'offset ACTUEL
        int newOffsetZ = -offsetZ - depth + 1;

        // Conserver l'offset original pour flipByLookDirection
        ClipboardData flipped = new ClipboardData(width, height, depth, offsetX, offsetY, newOffsetZ,
                originalOffsetX, originalOffsetY, originalOffsetZ);

        // Inverser le contenu sur l'axe Z (miroir)
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            int[] coords = parseKey(entry.getKey());
            int mirroredZ = depth - 1 - coords[2];
            flipped.setBlock(coords[0], coords[1], mirroredZ, entry.getValue());
        }

        return flipped;
    }

    /**
     * Flip sur l'axe Y (style WorldEdit).
     * 1. Déplace le clipboard de l'autre côté du joueur (symétrique par rapport à Y=0)
     * 2. Inverse le contenu (miroir)
     *
     * WorldEdit flip formula: newOffset = -(offset + dimension - 1) = -offset - dimension + 1
     */
    @NotNull
    public ClipboardData flipY() {
        // Flip symétrique basé sur l'offset ACTUEL
        int newOffsetY = -offsetY - height + 1;

        // Conserver l'offset original pour flipByLookDirection
        ClipboardData flipped = new ClipboardData(width, height, depth, offsetX, newOffsetY, offsetZ,
                originalOffsetX, originalOffsetY, originalOffsetZ);

        // Inverser le contenu sur l'axe Y (miroir)
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            int[] coords = parseKey(entry.getKey());
            int mirroredY = height - 1 - coords[1];
            flipped.setBlock(coords[0], mirroredY, coords[2], entry.getValue());
        }

        return flipped;
    }

    /**
     * Flip le clipboard dans une direction spécifique avec un offset absolu.
     * Le clipboard est mirroré et placé à la position spécifiée.
     *
     * @param axis "x", "y" ou "z"
     * @param newOffsetX nouvel offset X
     * @param newOffsetY nouvel offset Y
     * @param newOffsetZ nouvel offset Z
     */
    @NotNull
    public ClipboardData flipToPosition(@NotNull String axis, int newOffsetX, int newOffsetY, int newOffsetZ) {
        // Toujours conserver l'offset ORIGINAL (celui du copy)
        ClipboardData flipped = new ClipboardData(width, height, depth, newOffsetX, newOffsetY, newOffsetZ,
                originalOffsetX, originalOffsetY, originalOffsetZ);

        // Miroir sur l'axe spécifié
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            int[] coords = parseKey(entry.getKey());
            int x = coords[0];
            int y = coords[1];
            int z = coords[2];

            switch (axis) {
                case "x" -> x = width - 1 - x;
                case "y" -> y = height - 1 - y;
                case "z" -> z = depth - 1 - z;
            }

            flipped.setBlock(x, y, z, entry.getValue());
        }

        return flipped;
    }

    /**
     * Crée une copie du clipboard avec un nouvel offset (sans miroir).
     */
    @NotNull
    public ClipboardData withOffset(int newOffsetX, int newOffsetY, int newOffsetZ) {
        ClipboardData copy = new ClipboardData(width, height, depth, newOffsetX, newOffsetY, newOffsetZ);
        copy.blocks.putAll(this.blocks);
        return copy;
    }
}
