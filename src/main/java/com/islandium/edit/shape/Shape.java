package com.islandium.edit.shape;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface pour les formes géométriques (sphère, cylindre, etc.).
 */
public interface Shape {

    /**
     * Génère la liste des positions de blocs pour cette forme.
     *
     * @param centerX centre X
     * @param centerY centre Y
     * @param centerZ centre Z
     * @return liste de positions [x, y, z]
     */
    @NotNull
    List<int[]> generatePositions(int centerX, int centerY, int centerZ);

    /**
     * @return une estimation du nombre de blocs
     */
    int estimateBlockCount();

    /**
     * @return une description de la forme
     */
    @NotNull
    String getDescription();
}
