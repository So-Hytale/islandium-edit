package com.islandium.edit.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Représente un changement de bloc individuel pour l'historique undo/redo.
 */
public record BlockChange(
        @NotNull String worldId,
        int x,
        int y,
        int z,
        @Nullable String oldBlockType,
        @NotNull String newBlockType,
        int oldRotation,
        int newRotation
) {

    /**
     * Crée un BlockChange sans rotation (compatibilité).
     */
    public static BlockChange of(@NotNull String worldId, int x, int y, int z,
                                  @Nullable String oldBlockType, @NotNull String newBlockType) {
        return new BlockChange(worldId, x, y, z, oldBlockType, newBlockType, 0, 0);
    }

    /**
     * Crée un BlockChange avec rotation.
     */
    public static BlockChange of(@NotNull String worldId, int x, int y, int z,
                                  @Nullable String oldBlockType, @NotNull String newBlockType,
                                  int oldRotation, int newRotation) {
        return new BlockChange(worldId, x, y, z, oldBlockType, newBlockType, oldRotation, newRotation);
    }

    /**
     * @return l'ancien type de bloc (ou "air" si null)
     */
    @NotNull
    public String getOldBlockTypeSafe() {
        return oldBlockType != null ? oldBlockType : "air";
    }

    @Override
    public String toString() {
        return String.format("BlockChange[%s @ (%d,%d,%d): %s -> %s rot=%d->%d]",
                worldId, x, y, z, getOldBlockTypeSafe(), newBlockType, oldRotation, newRotation);
    }
}
