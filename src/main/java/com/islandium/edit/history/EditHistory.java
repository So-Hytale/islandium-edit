package com.islandium.edit.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'historique undo/redo par joueur.
 */
public class EditHistory {

    private final int maxHistorySize;
    private final Map<UUID, Deque<EditAction>> undoStacks;
    private final Map<UUID, Deque<EditAction>> redoStacks;

    /**
     * Crée un gestionnaire d'historique.
     *
     * @param maxHistorySize nombre maximum d'actions par joueur
     */
    public EditHistory(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
        this.undoStacks = new ConcurrentHashMap<>();
        this.redoStacks = new ConcurrentHashMap<>();
    }

    /**
     * Obtient ou crée la pile undo d'un joueur.
     */
    private Deque<EditAction> getUndoStack(@NotNull UUID playerId) {
        return undoStacks.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    }

    /**
     * Obtient ou crée la pile redo d'un joueur.
     */
    private Deque<EditAction> getRedoStack(@NotNull UUID playerId) {
        return redoStacks.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    }

    /**
     * Enregistre une nouvelle action dans l'historique.
     * Efface la pile redo (on ne peut plus redo après une nouvelle action).
     */
    public void pushAction(@NotNull UUID playerId, @NotNull EditAction action) {
        if (action.isEmpty()) {
            return;
        }

        Deque<EditAction> undoStack = getUndoStack(playerId);

        // Supprimer les anciennes actions si on dépasse la limite
        while (undoStack.size() >= maxHistorySize) {
            undoStack.removeLast();
        }

        undoStack.push(action);

        // Effacer la pile redo
        getRedoStack(playerId).clear();
    }

    /**
     * Dépile une action pour undo.
     * L'action est déplacée vers la pile redo.
     *
     * @return l'action à annuler ou null si rien à annuler
     */
    @Nullable
    public EditAction popUndo(@NotNull UUID playerId) {
        Deque<EditAction> undoStack = getUndoStack(playerId);
        if (undoStack.isEmpty()) {
            return null;
        }

        EditAction action = undoStack.pop();
        getRedoStack(playerId).push(action);
        return action;
    }

    /**
     * Dépile une action pour redo.
     * L'action est déplacée vers la pile undo.
     *
     * @return l'action à refaire ou null si rien à refaire
     */
    @Nullable
    public EditAction popRedo(@NotNull UUID playerId) {
        Deque<EditAction> redoStack = getRedoStack(playerId);
        if (redoStack.isEmpty()) {
            return null;
        }

        EditAction action = redoStack.pop();
        getUndoStack(playerId).push(action);
        return action;
    }

    /**
     * @return true si le joueur peut undo
     */
    public boolean canUndo(@NotNull UUID playerId) {
        Deque<EditAction> stack = undoStacks.get(playerId);
        return stack != null && !stack.isEmpty();
    }

    /**
     * @return true si le joueur peut redo
     */
    public boolean canRedo(@NotNull UUID playerId) {
        Deque<EditAction> stack = redoStacks.get(playerId);
        return stack != null && !stack.isEmpty();
    }

    /**
     * @return le nombre d'actions dans la pile undo du joueur
     */
    public int getUndoSize(@NotNull UUID playerId) {
        Deque<EditAction> stack = undoStacks.get(playerId);
        return stack != null ? stack.size() : 0;
    }

    /**
     * @return le nombre d'actions dans la pile redo du joueur
     */
    public int getRedoSize(@NotNull UUID playerId) {
        Deque<EditAction> stack = redoStacks.get(playerId);
        return stack != null ? stack.size() : 0;
    }

    /**
     * Efface l'historique d'un joueur.
     */
    public void clearPlayer(@NotNull UUID playerId) {
        undoStacks.remove(playerId);
        redoStacks.remove(playerId);
    }

    /**
     * Efface tout l'historique.
     */
    public void clearAll() {
        undoStacks.clear();
        redoStacks.clear();
    }
}
