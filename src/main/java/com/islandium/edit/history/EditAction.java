package com.islandium.edit.history;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Représente une action d'édition contenant plusieurs changements de blocs.
 * Utilisé pour l'historique undo/redo.
 */
public class EditAction {

    private final String description;
    private final Instant timestamp;
    private final List<BlockChange> changes;

    public EditAction(@NotNull String description) {
        this.description = description;
        this.timestamp = Instant.now();
        // Thread-safe car les changements peuvent être ajoutés depuis plusieurs threads world.execute()
        this.changes = new CopyOnWriteArrayList<>();
    }

    /**
     * Ajoute un changement de bloc à cette action.
     * Thread-safe.
     */
    public void addChange(@NotNull BlockChange change) {
        changes.add(change);
    }

    /**
     * Ajoute un changement de bloc à cette action.
     * Thread-safe.
     */
    public void addChange(@NotNull String worldId, int x, int y, int z,
                          String oldBlockType, @NotNull String newBlockType) {
        changes.add(BlockChange.of(worldId, x, y, z, oldBlockType, newBlockType));
    }

    /**
     * Ajoute un changement de bloc avec rotation à cette action.
     * Thread-safe.
     */
    public void addChange(@NotNull String worldId, int x, int y, int z,
                          String oldBlockType, @NotNull String newBlockType,
                          int oldRotation, int newRotation) {
        changes.add(BlockChange.of(worldId, x, y, z, oldBlockType, newBlockType, oldRotation, newRotation));
    }

    /**
     * @return la description de l'action
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * @return le timestamp de création
     */
    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return la liste des changements (copie thread-safe)
     */
    @NotNull
    public List<BlockChange> getChanges() {
        // CopyOnWriteArrayList est déjà thread-safe pour l'itération
        return List.copyOf(changes);
    }

    /**
     * @return le nombre de blocs affectés
     */
    public int getBlockCount() {
        return changes.size();
    }

    /**
     * @return true si l'action n'a aucun changement
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * @return l'ID du monde (depuis le premier changement) ou null si vide
     */
    public String getWorldId() {
        return changes.isEmpty() ? null : changes.get(0).worldId();
    }

    @Override
    public String toString() {
        return String.format("EditAction[%s, %d blocks]", description, changes.size());
    }
}
