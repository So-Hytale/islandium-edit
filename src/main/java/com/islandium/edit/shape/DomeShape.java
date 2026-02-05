package com.islandium.edit.shape;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme dôme (demi-sphère, pleine ou creuse).
 */
public class DomeShape implements Shape {

    private final int radius;
    private final boolean hollow;

    public DomeShape(int radius, boolean hollow) {
        this.radius = Math.max(1, radius);
        this.hollow = hollow;
    }

    @Override
    @NotNull
    public List<int[]> generatePositions(int centerX, int centerY, int centerZ) {
        List<int[]> positions = new ArrayList<>();
        int radiusSquared = radius * radius;
        int innerRadiusSquared = hollow ? (radius - 1) * (radius - 1) : 0;

        for (int y = 0; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSquared = x * x + y * y + z * z;

                    if (distSquared <= radiusSquared) {
                        if (!hollow || distSquared >= innerRadiusSquared || y == 0) {
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
        // Volume d'une demi-sphère: (2/3) * π * r³
        double volume = (2.0 / 3.0) * Math.PI * radius * radius * radius;
        if (hollow) {
            double innerVolume = (2.0 / 3.0) * Math.PI * (radius - 1) * (radius - 1) * (radius - 1);
            return (int) (volume - innerVolume);
        }
        return (int) volume;
    }

    @Override
    @NotNull
    public String getDescription() {
        return (hollow ? "Hollow " : "") + "Dome (r=" + radius + ")";
    }
}
