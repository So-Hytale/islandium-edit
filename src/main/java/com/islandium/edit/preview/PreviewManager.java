package com.islandium.edit.preview;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.debug.DebugLogger;
import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.operation.ClipboardHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Gestionnaire de preview visuelle pour le clipboard.
 * Utilise les debug shapes (cubes bleus) pour visualiser la structure.
 * Supporte les transformations (rotation/flip) comme WorldEdit.
 * Ne modifie pas le monde reel.
 */
public class PreviewManager {

    private final EditPlugin plugin;
    private final Map<UUID, PreviewSession> activePreviews;
    private final Map<UUID, int[]> frozenPositions;
    private final ScheduledExecutorService scheduler;

    /** Couleur de la preview (bleu) */
    private static final Vector3f PREVIEW_COLOR = new Vector3f(0.2f, 0.6f, 1.0f);

    /** Couleur apres transformation (cyan) */
    private static final Vector3f TRANSFORMED_COLOR = new Vector3f(0.2f, 0.9f, 0.9f);

    /** Couleur du point de reference (rouge) - position joueur / centre du miroir */
    private static final Vector3f REFERENCE_COLOR = new Vector3f(1.0f, 0.2f, 0.2f);

    /** Couleur de la preview figee (vert) */
    private static final Vector3f FROZEN_COLOR = new Vector3f(0.2f, 1.0f, 0.4f);

    /** Couleur du point de reference fige (jaune) */
    private static final Vector3f FROZEN_REFERENCE_COLOR = new Vector3f(1.0f, 0.9f, 0.2f);

    /** Duree d'affichage des debug shapes en secondes */
    private static final float DISPLAY_DURATION = 5.0f;

    /** Intervalle de rafraichissement de la preview persistante en ms */
    private static final int REFRESH_INTERVAL_MS = 1000;

    public PreviewManager(@NotNull EditPlugin plugin) {
        this.plugin = plugin;
        this.activePreviews = new ConcurrentHashMap<>();
        this.frozenPositions = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IslandiumEdit-Preview");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Lance une preview temporaire du clipboard (5 secondes).
     *
     * @return CompletableFuture avec le resultat
     */
    public CompletableFuture<PreviewResult> showPreview(@NotNull Player player) {
        return showPreviewInternal(player, false);
    }

    /**
     * Lance une preview persistante du clipboard.
     * Se rafraichit toutes les secondes jusqu'a paste ou cancel.
     *
     * @return CompletableFuture avec le resultat
     */
    public CompletableFuture<PreviewResult> startPersistentPreview(@NotNull Player player) {
        return showPreviewInternal(player, true);
    }

    /**
     * Implementation interne de la preview.
     */
    @SuppressWarnings("deprecation")
    private CompletableFuture<PreviewResult> showPreviewInternal(@NotNull Player player, boolean persistent) {
        ClipboardHolder holder = plugin.getClipboardOperations().getClipboardHolder(player);
        if (holder == null || holder.isEmpty()) {
            return CompletableFuture.completedFuture(
                PreviewResult.failure("Clipboard vide"));
        }

        UUID playerId = player.getUuid();

        // Si une preview est deja active, la stopper d'abord
        if (activePreviews.containsKey(playerId)) {
            stopPreview(playerId);
        }

        CompletableFuture<PreviewResult> future = new CompletableFuture<>();

        // Creer la session de preview avec le holder (inclut la transformation)
        PreviewSession session = new PreviewSession(player, holder, persistent);
        activePreviews.put(playerId, session);

        // Afficher les debug shapes
        sendPreviewShapes(session, future);

        return future;
    }

    /**
     * Envoie les debug shapes pour la preview avec transformation.
     */
    @SuppressWarnings("deprecation")
    private void sendPreviewShapes(PreviewSession session, CompletableFuture<PreviewResult> future) {
        Player player = session.getPlayer();
        ClipboardHolder holder = session.getHolder();
        UUID playerId = player.getUuid();

        var connection = player.getPlayerConnection();
        if (connection == null) {
            activePreviews.remove(playerId);
            if (future != null) {
                future.complete(PreviewResult.failure("Connexion joueur introuvable"));
            }
            return;
        }

        // Position du joueur
        var transformComponent = player.getTransformComponent();
        if (transformComponent == null) {
            activePreviews.remove(playerId);
            if (future != null) {
                future.complete(PreviewResult.failure("Position joueur introuvable"));
            }
            return;
        }

        var pos = transformComponent.getPosition();

        // Utiliser la position figee si disponible, sinon la position du joueur
        int playerX, playerY, playerZ;
        boolean isFrozen = frozenPositions.containsKey(playerId);
        if (isFrozen) {
            int[] frozen = frozenPositions.get(playerId);
            playerX = frozen[0];
            playerY = frozen[1];
            playerZ = frozen[2];
        } else {
            playerX = (int) Math.floor(pos.getX());
            playerY = (int) Math.floor(pos.getY());
            playerZ = (int) Math.floor(pos.getZ());
        }

        ClipboardData clipboard = holder.getClipboard();
        AffineTransform transform = holder.getTransform();
        boolean isTransformed = !transform.isIdentity();

        // Effacer les anciennes shapes
        connection.write(new ClearDebugShapes());

        // Construire les debug shapes pour chaque bloc AVEC transformation
        List<DisplayDebug> shapes = new ArrayList<>();
        int placed = 0;

        // Cube de reference: jaune si fige, rouge sinon
        Vector3f refColor = isFrozen ? FROZEN_REFERENCE_COLOR : REFERENCE_COLOR;
        shapes.add(createBlockCube(playerX + 0.5, playerY + 0.5, playerZ + 0.5, refColor));

        // Couleur: vert si fige, cyan si transforme, bleu sinon
        Vector3f color = isFrozen ? FROZEN_COLOR : (isTransformed ? TRANSFORMED_COLOR : PREVIEW_COLOR);

        // WorldEdit style: transform around PLAYER position (origin = 0,0,0)
        // Le joueur est le point central du miroir/rotation
        int offsetX = clipboard.getOffsetX();
        int offsetY = clipboard.getOffsetY();
        int offsetZ = clipboard.getOffsetZ();

        for (Map.Entry<String, String> entry : clipboard.getBlocks().entrySet()) {
            String blockType = entry.getValue();
            // Ne pas montrer les blocs d'air dans la preview
            if ("air".equalsIgnoreCase(blockType)) {
                continue;
            }

            int[] coords = ClipboardData.parseKey(entry.getKey());

            // Position relative au joueur (offset + position dans le clipboard)
            double relX = offsetX + coords[0];
            double relY = offsetY + coords[1];
            double relZ = offsetZ + coords[2];

            // Appliquer la transformation autour du joueur (origin = 0,0,0)
            double[] transformed = transform.apply(relX, relY, relZ);

            // Corriger les erreurs de précision flottante (ex: cos(270°) ≈ -1.84e-16 au lieu de 0)
            for (int i = 0; i < 3; i++) {
                double rounded = Math.round(transformed[i]);
                if (Math.abs(transformed[i] - rounded) < 1e-8) {
                    transformed[i] = rounded;
                }
            }

            // Ajouter la position du joueur + 0.5 pour le centre du bloc
            // Utiliser Math.floor pour être cohérent avec le paste
            double worldX = playerX + Math.floor(transformed[0]) + 0.5;
            double worldY = playerY + Math.floor(transformed[1]) + 0.5;
            double worldZ = playerZ + Math.floor(transformed[2]) + 0.5;

            // Creer un cube debug pour ce bloc
            shapes.add(createBlockCube(worldX, worldY, worldZ, color));
            placed++;
        }

        // Envoyer toutes les shapes
        for (DisplayDebug shape : shapes) {
            connection.write(shape);
        }

        session.setPlacedCount(placed);

        // Debug log
        DebugLogger dbg = DebugLogger.get();
        if (dbg != null) {
            dbg.logPreview(player.getDisplayName(), playerX, playerY, playerZ,
                    isTransformed, session.isPersistent(), placed);
        }

        // Programmer selon le mode
        if (session.isPersistent()) {
            // Mode persistant: rafraichir toutes les secondes
            ScheduledFuture<?> refreshTask = scheduler.schedule(
                () -> refreshPreview(playerId),
                REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
            session.setRefreshTask(refreshTask);

            String transformInfo = isTransformed ? " (transforme)" : "";
            if (future != null) {
                future.complete(PreviewResult.success(
                    "Preview active" + transformInfo + " (/epaste pour coller, /epreview stop pour annuler)", placed));
            }
        } else {
            // Mode temporaire: pas de programmation (les shapes disparaissent naturellement)
            String transformInfo = isTransformed ? " (transforme)" : "";
            if (future != null) {
                future.complete(PreviewResult.success(
                    "Preview affichee" + transformInfo, placed));
            }
        }
    }

    /**
     * Cree un cube debug pour representer un bloc.
     */
    private DisplayDebug createBlockCube(double x, double y, double z, Vector3f color) {
        // Cube de taille 0.9 pour laisser un petit espace entre les blocs
        Matrix4d matrix = new Matrix4d()
            .identity()
            .translate(x, y, z)
            .scale(0.9, 0.9, 0.9);

        return new DisplayDebug(
            DebugShape.Cube,
            matrix.asFloatData(),
            color,
            DISPLAY_DURATION,
            true,  // filled
            null
        );
    }

    /**
     * Rafraichit la preview persistante (appele toutes les secondes).
     */
    @SuppressWarnings("deprecation")
    private void refreshPreview(UUID playerId) {
        PreviewSession session = activePreviews.get(playerId);
        if (session == null || !session.isPersistent()) {
            return;
        }

        // Verifier que le joueur est toujours connecte
        Player player = session.getPlayer();
        if (player == null) {
            stopPreview(playerId);
            return;
        }

        // Recharger le holder (peut avoir ete modifie par flip/rotate)
        ClipboardHolder newHolder = plugin.getClipboardOperations().getClipboardHolder(player);
        if (newHolder == null || newHolder.isEmpty()) {
            stopPreview(playerId);
            return;
        }
        session.setHolder(newHolder);

        // Rafraichir les cubes debug (figes ou mobiles)
        // sendPreviewShapes utilise la position figee si disponible
        sendPreviewShapes(session, null);
    }

    /**
     * Arrete la preview.
     */
    @SuppressWarnings("deprecation")
    public void stopPreview(@NotNull Player player) {
        stopPreview(player.getUuid());
    }

    /**
     * Arrete la preview.
     */
    @SuppressWarnings("deprecation")
    public void stopPreview(UUID playerId) {
        PreviewSession session = activePreviews.remove(playerId);
        boolean wasFrozen = frozenPositions.remove(playerId) != null;

        if (session == null) {
            return;
        }

        // Annuler les taches programmees
        if (session.getRefreshTask() != null) {
            session.getRefreshTask().cancel(false);
        }

        // Effacer les debug shapes
        Player player = session.getPlayer();
        if (player != null) {
            var connection = player.getPlayerConnection();
            if (connection != null) {
                connection.write(new ClearDebugShapes());
            }
        }
    }

    /**
     * Annule la preview (alias pour compatibilite).
     */
    public void cancelPreview(@NotNull Player player) {
        stopPreview(player.getUuid());
    }

    // === Freeze (figer la position de la preview) ===

    /**
     * Fige la preview a la position actuelle du joueur.
     * Les cubes debug restent affiches en vert a la position figee.
     */
    @SuppressWarnings("deprecation")
    public void freezeAt(@NotNull Player player) {
        var transformComponent = player.getTransformComponent();
        if (transformComponent == null) return;

        var pos = transformComponent.getPosition();
        int x = (int) Math.floor(pos.getX());
        int y = (int) Math.floor(pos.getY());
        int z = (int) Math.floor(pos.getZ());

        UUID playerId = player.getUuid();
        frozenPositions.put(playerId, new int[]{x, y, z});

        // Re-envoyer les cubes debug immediatement a la position figee (vert)
        PreviewSession session = activePreviews.get(playerId);
        if (session != null) {
            sendPreviewShapes(session, null);
        }
    }

    /**
     * Defige la preview (reprend le suivi du joueur avec cubes debug).
     */
    @SuppressWarnings("deprecation")
    public void unfreeze(@NotNull Player player) {
        UUID playerId = player.getUuid();
        frozenPositions.remove(playerId);

        // Re-envoyer immediatement les cubes debug a la position du joueur (bleu)
        PreviewSession session = activePreviews.get(playerId);
        if (session != null) {
            sendPreviewShapes(session, null);
        }
    }

    /**
     * Verifie si la preview est figee.
     */
    @SuppressWarnings("deprecation")
    public boolean isFrozen(@NotNull Player player) {
        return frozenPositions.containsKey(player.getUuid());
    }

    /**
     * Retourne la position figee ou null.
     */
    @SuppressWarnings("deprecation")
    public int[] getFrozenPosition(@NotNull Player player) {
        return frozenPositions.get(player.getUuid());
    }

    /**
     * Verifie si une preview est active.
     */
    @SuppressWarnings("deprecation")
    public boolean hasActivePreview(@NotNull Player player) {
        return activePreviews.containsKey(player.getUuid());
    }

    /**
     * Verifie si une preview persistante est active.
     */
    @SuppressWarnings("deprecation")
    public boolean hasPersistentPreview(@NotNull Player player) {
        PreviewSession session = activePreviews.get(player.getUuid());
        return session != null && session.isPersistent();
    }

    public void shutdown() {
        // Stopper toutes les previews actives
        for (UUID playerId : new ArrayList<>(activePreviews.keySet())) {
            stopPreview(playerId);
        }
        activePreviews.clear();
        frozenPositions.clear();
        scheduler.shutdown();
    }

    /**
     * Resultat d'une operation de preview.
     */
    public record PreviewResult(boolean success, String message, int blockCount) {
        public static PreviewResult success(String message, int blockCount) {
            return new PreviewResult(true, message, blockCount);
        }

        public static PreviewResult failure(String message) {
            return new PreviewResult(false, message, 0);
        }
    }

    /**
     * Session de preview pour un joueur.
     */
    private static class PreviewSession {
        private final Player player;
        private ClipboardHolder holder;
        private final boolean persistent;
        private int placedCount;
        private ScheduledFuture<?> refreshTask;

        public PreviewSession(Player player, ClipboardHolder holder, boolean persistent) {
            this.player = player;
            this.holder = holder;
            this.persistent = persistent;
        }

        public Player getPlayer() {
            return player;
        }

        public ClipboardHolder getHolder() {
            return holder;
        }

        public void setHolder(ClipboardHolder holder) {
            this.holder = holder;
        }

        // Deprecated compatibility method
        public ClipboardData getClipboard() {
            return holder != null ? holder.getClipboard() : null;
        }

        public void setClipboard(ClipboardData clipboard) {
            // No-op for compatibility - use setHolder instead
        }

        public boolean isPersistent() {
            return persistent;
        }

        public int getPlacedCount() {
            return placedCount;
        }

        public void setPlacedCount(int count) {
            this.placedCount = count;
        }

        public ScheduledFuture<?> getRefreshTask() {
            return refreshTask;
        }

        public void setRefreshTask(ScheduledFuture<?> task) {
            this.refreshTask = task;
        }
    }
}
