package com.islandium.edit.shape;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme cylindre (plein ou creux).
 */
public class CylinderShape implements Shape {

    private final int radius;
    private final int height;
    private final boolean hollow;

    public CylinderShape(int radius, int height, boolean hollow) {
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
        this.hollow = hollow;
    }

    @Override
    @NotNull
    public List<int[]> generatePositions(int centerX, int centerY, int centerZ) {
        List<int[]> positions = new ArrayList<>();
        int radiusSquared = radius * radius;
        int innerRadiusSquared = hollow ? (radius - 1) * (radius - 1) : 0;

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSquared = x * x + z * z;

                    if (distSquared <= radiusSquared) {
                        if (!hollow || distSquared >= innerRadiusSquared) {
                            positions.add(new int[]{centerX + x, centerY + y, centerZ + z});
                        }
                    }
                }
            }
        }

        return positions;
    }

    @Override
    public int estimateBlockCount() {
        double volume = Math.PI * radius * radius * height;
        if (hollow) {
            double innerVolume = Math.PI * (radius - 1) * (radius - 1) * height;
            return (int) (volume - innerVolume);
        }
        return (int) volume;
    }

    @Override
    @NotNull
    public String getDescription() {
        return (hollow ? "Hollow " : "") + "Cylinder (r=" + radius + ", h=" + height + ")";
    }
}
