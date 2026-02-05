package com.islandium.edit.shape;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme pyramide (pleine ou creuse).
 */
public class PyramidShape implements Shape {

    private final int size;
    private final boolean hollow;

    public PyramidShape(int size, boolean hollow) {
        this.size = Math.max(1, size);
        this.hollow = hollow;
    }

    @Override
    @NotNull
    public List<int[]> generatePositions(int centerX, int centerY, int centerZ) {
        List<int[]> positions = new ArrayList<>();

        for (int y = 0; y < size; y++) {
            int layerSize = size - y;

            for (int x = -layerSize + 1; x < layerSize; x++) {
                for (int z = -layerSize + 1; z < layerSize; z++) {
                    if (!hollow || isEdge(x, z, layerSize) || y == 0) {
                        positions.add(new int[]{centerX + x, centerY + y, centerZ + z});
                    }
                }
            }
        }

        return positions;
    }

    private boolean isEdge(int x, int z, int layerSize) {
        return Math.abs(x) == layerSize - 1 || Math.abs(z) == layerSize - 1;
    }

    @Override
    public int estimateBlockCount() {
        // Somme des carrés de 1 à size
        int total = 0;
        for (int i = 1; i <= size; i++) {
            int side = 2 * i - 1;
            if (hollow && i < size) {
                total += side * 4 - 4; // Périmètre
            } else {
                total += side * side;
            }
        }
        return total;
    }

    @Override
    @NotNull
    public String getDescription() {
        return (hollow ? "Hollow " : "") + "Pyramid (size=" + size + ")";
    }
}
