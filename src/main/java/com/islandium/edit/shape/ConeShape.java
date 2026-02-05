package com.islandium.edit.shape;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme cône (plein ou creux).
 */
public class ConeShape implements Shape {

    private final int radius;
    private final int height;
    private final boolean hollow;

    public ConeShape(int radius, int height, boolean hollow) {
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
        this.hollow = hollow;
    }

    @Override
    @NotNull
    public List<int[]> generatePositions(int centerX, int centerY, int centerZ) {
        List<int[]> positions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            // Le rayon diminue linéairement de radius à 0
            double progress = (double) y / height;
            int currentRadius = (int) Math.round(radius * (1 - progress));

            if (currentRadius < 0) currentRadius = 0;

            int radiusSquared = currentRadius * currentRadius;
            int innerRadiusSquared = hollow && currentRadius > 0 ? (currentRadius - 1) * (currentRadius - 1) : 0;

            for (int x = -currentRadius; x <= currentRadius; x++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    int distSquared = x * x + z * z;

                    if (distSquared <= radiusSquared) {
                        if (!hollow || distSquared >= innerRadiusSquared || y == 0 || currentRadius <= 1) {
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
        // Volume d'un cône: (1/3) * π * r² * h
        double volume = (1.0 / 3.0) * Math.PI * radius * radius * height;
        if (hollow) {
            double innerVolume = (1.0 / 3.0) * Math.PI * (radius - 1) * (radius - 1) * height;
            return (int) (volume - innerVolume);
        }
        return (int) volume;
    }

    @Override
    @NotNull
    public String getDescription() {
        return (hollow ? "Hollow " : "") + "Cone (r=" + radius + ", h=" + height + ")";
    }
}
