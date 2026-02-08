package com.islandium.edit.operation;

import com.islandium.edit.math.AffineTransform;
import org.jetbrains.annotations.NotNull;

/**
 * Holds a clipboard and its associated transform.
 * Like WorldEdit, the transform is stored separately and applied at paste time.
 * This allows multiple rotations/flips to be composed without modifying the original data.
 */
public class ClipboardHolder {

    private final ClipboardData clipboard;
    private AffineTransform transform;

    public ClipboardHolder(@NotNull ClipboardData clipboard) {
        this.clipboard = clipboard;
        this.transform = new AffineTransform(); // Identity
    }

    /**
     * Gets the original clipboard (unmodified).
     */
    @NotNull
    public ClipboardData getClipboard() {
        return clipboard;
    }

    /**
     * Gets the current transform.
     */
    @NotNull
    public AffineTransform getTransform() {
        return transform;
    }

    /**
     * Sets the transform.
     */
    public void setTransform(@NotNull AffineTransform transform) {
        this.transform = transform;
    }

    /**
     * Checks if the clipboard is empty.
     */
    public boolean isEmpty() {
        return clipboard.isEmpty();
    }

    /**
     * Gets the block count.
     */
    public int getBlockCount() {
        return clipboard.getBlockCount();
    }

    /**
     * Gets the origin offset (from the original clipboard).
     */
    public int getOffsetX() {
        return clipboard.getOffsetX();
    }

    public int getOffsetY() {
        return clipboard.getOffsetY();
    }

    public int getOffsetZ() {
        return clipboard.getOffsetZ();
    }

    /**
     * Gets the dimensions of the clipboard.
     */
    public int getWidth() {
        return clipboard.getWidth();
    }

    public int getHeight() {
        return clipboard.getHeight();
    }

    public int getDepth() {
        return clipboard.getDepth();
    }

    /**
     * Calculates the transformed bounding box.
     * Returns [minX, minY, minZ, maxX, maxY, maxZ] after transform.
     */
    @NotNull
    public int[] getTransformedBounds() {
        int ox = clipboard.getOffsetX();
        int oy = clipboard.getOffsetY();
        int oz = clipboard.getOffsetZ();
        int w = clipboard.getWidth();
        int h = clipboard.getHeight();
        int d = clipboard.getDepth();

        // Transform all 8 corners
        double[][] corners = {
            {ox, oy, oz},
            {ox + w - 1, oy, oz},
            {ox, oy + h - 1, oz},
            {ox, oy, oz + d - 1},
            {ox + w - 1, oy + h - 1, oz},
            {ox + w - 1, oy, oz + d - 1},
            {ox, oy + h - 1, oz + d - 1},
            {ox + w - 1, oy + h - 1, oz + d - 1}
        };

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

        for (double[] corner : corners) {
            double[] transformed = transform.apply(corner[0], corner[1], corner[2]);
            // Corriger les erreurs de précision flottante (cos/sin de 90°/270°)
            for (int i = 0; i < 3; i++) {
                double rounded = Math.round(transformed[i]);
                if (Math.abs(transformed[i] - rounded) < 1e-8) {
                    transformed[i] = rounded;
                }
            }
            minX = Math.min(minX, transformed[0]);
            minY = Math.min(minY, transformed[1]);
            minZ = Math.min(minZ, transformed[2]);
            maxX = Math.max(maxX, transformed[0]);
            maxY = Math.max(maxY, transformed[1]);
            maxZ = Math.max(maxZ, transformed[2]);
        }

        return new int[] {
            (int) Math.floor(minX),
            (int) Math.floor(minY),
            (int) Math.floor(minZ),
            (int) Math.ceil(maxX),
            (int) Math.ceil(maxY),
            (int) Math.ceil(maxZ)
        };
    }
}
