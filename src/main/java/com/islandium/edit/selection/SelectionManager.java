package com.islandium.edit.selection;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pour les sélections visuelles utilisant l'API BuilderToolsPlugin.
 * Le rendu visuel est envoyé via EditorSelection packet.
 */
public class SelectionManager {

    // Stockage local des positions pour chaque joueur (par UUID)
    private final Map<UUID, Vector3i> pos1Map = new ConcurrentHashMap<>();
    private final Map<UUID, Vector3i> pos2Map = new ConcurrentHashMap<>();

    /**
     * Obtient le PlayerRef depuis un Player.
     */
    @Nullable
    public PlayerRef getPlayerRef(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return null;
            }
            var store = ref.getStore();
            return store.getComponent(ref, PlayerRef.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtient le BuilderState du joueur.
     */
    @Nullable
    public BuilderToolsPlugin.BuilderState getBuilderState(@NotNull Player player) {
        try {
            PlayerRef playerRef = getPlayerRef(player);
            if (playerRef == null) {
                return null;
            }
            return BuilderToolsPlugin.getState(player, playerRef);
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    /**
     * Obtient la BlockSelection du joueur.
     */
    @Nullable
    public BlockSelection getSelection(@NotNull Player player) {
        try {
            BuilderToolsPlugin.BuilderState state = getBuilderState(player);
            if (state == null) {
                return null;
            }
            return state.getSelection();
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    /**
     * Vérifie si le joueur a une sélection valide (pos1 et pos2 définies).
     * Vérifie d'abord localement, puis dans BuilderToolsPlugin.
     */
    public boolean hasValidSelection(@NotNull Player player) {
        // Vérifier d'abord localement
        if (hasLocalSelection(player)) {
            return true;
        }
        // Fallback sur BuilderToolsPlugin
        BlockSelection selection = getSelection(player);
        return selection != null && selection.hasSelectionBounds();
    }

    /**
     * Obtient le coin minimum de la sélection.
     */
    @Nullable
    public Vector3i getSelectionMin(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        if (selection == null || !selection.hasSelectionBounds()) {
            return null;
        }
        return selection.getSelectionMin();
    }

    /**
     * Obtient le coin maximum de la sélection.
     */
    @Nullable
    public Vector3i getSelectionMax(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        if (selection == null || !selection.hasSelectionBounds()) {
            return null;
        }
        return selection.getSelectionMax();
    }

    /**
     * Définit la position 1 de la sélection.
     * Garde la position 2 existante si présente.
     * Le rendu visuel est mis à jour automatiquement.
     *
     * @return true si la position a été définie avec succès
     */
    @SuppressWarnings("deprecation")
    public boolean setPos1(@NotNull Player player, @NotNull Vector3i pos) {
        try {
            UUID playerId = player.getUuid();

            // Stocker pos1 localement
            pos1Map.put(playerId, pos);

            // Aussi mettre à jour dans BuilderToolsPlugin si disponible
            try {
                BuilderToolsPlugin.BuilderState state = getBuilderState(player);
                if (state != null) {
                    BlockSelection selection = state.getSelection();
                    if (selection != null) {
                        Vector3i pos2 = pos2Map.get(playerId);
                        if (pos2 != null) {
                            selection.setSelectionArea(pos, pos2);
                        } else {
                            selection.setSelectionArea(pos, pos);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Envoyer le visuel au client directement
            sendSelectionVisual(player);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Définit la position 2 de la sélection.
     * Garde la position 1 existante si présente.
     * Le rendu visuel est mis à jour automatiquement.
     *
     * @return true si la position a été définie avec succès
     */
    @SuppressWarnings("deprecation")
    public boolean setPos2(@NotNull Player player, @NotNull Vector3i pos) {
        try {
            UUID playerId = player.getUuid();

            // Stocker pos2 localement
            pos2Map.put(playerId, pos);

            // Aussi mettre à jour dans BuilderToolsPlugin si disponible
            try {
                BuilderToolsPlugin.BuilderState state = getBuilderState(player);
                if (state != null) {
                    BlockSelection selection = state.getSelection();
                    if (selection != null) {
                        Vector3i pos1 = pos1Map.get(playerId);
                        if (pos1 != null) {
                            selection.setSelectionArea(pos1, pos);
                        } else {
                            selection.setSelectionArea(pos, pos);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Envoyer le visuel au client directement
            sendSelectionVisual(player);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Définit les deux positions de la sélection.
     * Le rendu visuel est mis à jour automatiquement.
     *
     * @return true si les positions ont été définies avec succès
     */
    public boolean setSelection(@NotNull Player player, @NotNull Vector3i pos1, @NotNull Vector3i pos2) {
        try {
            BlockSelection selection = getSelection(player);
            if (selection == null) {
                return false;
            }

            selection.setSelectionArea(pos1, pos2);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Efface la sélection du joueur.
     *
     * @return true si la sélection a été effacée
     */
    public boolean clearSelection(@NotNull Player player) {
        try {
            BlockSelection selection = getSelection(player);
            if (selection == null) {
                return false;
            }

            // Définir une sélection vide (même point)
            selection.setSelectionArea(new Vector3i(0, 0, 0), new Vector3i(0, 0, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtient les bounds de la sélection sous forme de tableau.
     * Vérifie d'abord localement, puis dans BuilderToolsPlugin.
     *
     * @return int[] {minX, minY, minZ, maxX, maxY, maxZ} ou null si pas de sélection
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public int[] getSelectionBounds(@NotNull Player player) {
        UUID playerId = player.getUuid();
        Vector3i p1 = pos1Map.get(playerId);
        Vector3i p2 = pos2Map.get(playerId);

        // Utiliser les positions locales si disponibles
        if (p1 != null && p2 != null) {
            return new int[]{
                    Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ()),
                    Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ())
            };
        }

        // Fallback sur BuilderToolsPlugin
        Vector3i min = getSelectionMin(player);
        Vector3i max = getSelectionMax(player);

        if (min == null || max == null) {
            return null;
        }

        return new int[]{
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        };
    }

    /**
     * Calcule le volume de la sélection en nombre de blocs.
     *
     * @return le volume ou 0 si pas de sélection valide
     */
    public long getVolume(@NotNull Player player) {
        int[] bounds = getSelectionBounds(player);
        if (bounds == null) {
            return 0;
        }

        long dx = bounds[3] - bounds[0] + 1;
        long dy = bounds[4] - bounds[1] + 1;
        long dz = bounds[5] - bounds[2] + 1;

        return dx * dy * dz;
    }

    /**
     * Obtient les dimensions de la sélection.
     *
     * @return int[] {width, height, depth} ou null si pas de sélection
     */
    @Nullable
    public int[] getSelectionDimensions(@NotNull Player player) {
        int[] bounds = getSelectionBounds(player);
        if (bounds == null) {
            return null;
        }

        return new int[]{
                bounds[3] - bounds[0] + 1,
                bounds[4] - bounds[1] + 1,
                bounds[5] - bounds[2] + 1
        };
    }

    // ==================== Méthodes pour le stockage local ====================

    /**
     * Obtient Pos1 depuis le stockage local.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public Vector3i getLocalPos1(@NotNull Player player) {
        return pos1Map.get(player.getUuid());
    }

    /**
     * Obtient Pos2 depuis le stockage local.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public Vector3i getLocalPos2(@NotNull Player player) {
        return pos2Map.get(player.getUuid());
    }

    /**
     * Vérifie si le joueur a une sélection valide (pos1 ET pos2 définies localement).
     */
    @SuppressWarnings("deprecation")
    public boolean hasLocalSelection(@NotNull Player player) {
        UUID playerId = player.getUuid();
        return pos1Map.containsKey(playerId) && pos2Map.containsKey(playerId);
    }

    /**
     * Calcule le volume de la sélection locale.
     */
    @SuppressWarnings("deprecation")
    public long getLocalVolume(@NotNull Player player) {
        UUID playerId = player.getUuid();
        Vector3i p1 = pos1Map.get(playerId);
        Vector3i p2 = pos2Map.get(playerId);

        if (p1 == null || p2 == null) {
            return 0;
        }

        long dx = Math.abs(p2.getX() - p1.getX()) + 1;
        long dy = Math.abs(p2.getY() - p1.getY()) + 1;
        long dz = Math.abs(p2.getZ() - p1.getZ()) + 1;

        return dx * dy * dz;
    }

    // Couleur de la sélection (vert lime)
    private static final Vector3f SELECTION_COLOR = new Vector3f(0.2f, 1.0f, 0.2f);
    // Durée d'affichage en secondes
    private static final float DISPLAY_DURATION = 300.0f;
    // Épaisseur des lignes de la boîte
    private static final double LINE_THICKNESS = 0.05;

    /**
     * Envoie le visuel de la sélection au client via DisplayDebug packets.
     * Dessine une boîte 3D autour de la sélection.
     */
    @SuppressWarnings("deprecation")
    public void sendSelectionVisual(@NotNull Player player) {
        UUID playerId = player.getUuid();
        Vector3i p1 = pos1Map.get(playerId);
        Vector3i p2 = pos2Map.get(playerId);

        if (p1 == null || p2 == null) {
            return;
        }

        var connection = player.getPlayerConnection();
        if (connection == null) {
            return;
        }

        // Effacer les anciennes formes debug
        connection.write(new ClearDebugShapes());

        // Calculer min/max (inclusif, donc +1 sur max)
        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX()) + 1;
        double maxY = Math.max(p1.getY(), p2.getY()) + 1;
        double maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;

        // Dimensions de la boîte
        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;

        // Centre de la boîte
        double centerX = minX + sizeX / 2.0;
        double centerY = minY + sizeY / 2.0;
        double centerZ = minZ + sizeZ / 2.0;

        // Construire les 12 arêtes de la boîte
        List<DisplayDebug> packets = buildBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);

        // Envoyer tous les packets
        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    /**
     * Construit les 12 arêtes d'une boîte 3D.
     */
    private List<DisplayDebug> buildBoxEdges(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        List<DisplayDebug> packets = new ArrayList<>();

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;

        // 4 arêtes horizontales en bas (Y = minY)
        packets.add(createEdge(minX + sizeX/2, minY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS)); // bas avant
        packets.add(createEdge(minX + sizeX/2, minY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS)); // bas arrière
        packets.add(createEdge(minX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ)); // bas gauche
        packets.add(createEdge(maxX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ)); // bas droite

        // 4 arêtes horizontales en haut (Y = maxY)
        packets.add(createEdge(minX + sizeX/2, maxY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS)); // haut avant
        packets.add(createEdge(minX + sizeX/2, maxY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS)); // haut arrière
        packets.add(createEdge(minX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ)); // haut gauche
        packets.add(createEdge(maxX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ)); // haut droite

        // 4 arêtes verticales (piliers)
        packets.add(createEdge(minX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS)); // avant gauche
        packets.add(createEdge(maxX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS)); // avant droite
        packets.add(createEdge(minX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS)); // arrière gauche
        packets.add(createEdge(maxX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS)); // arrière droite

        return packets;
    }

    /**
     * Crée un packet DisplayDebug pour une arête (cube allongé).
     */
    private DisplayDebug createEdge(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        Matrix4d matrix = new Matrix4d()
            .identity()
            .translate(x, y, z)
            .scale(scaleX, scaleY, scaleZ);

        return new DisplayDebug(
            DebugShape.Cube,
            matrix.asFloatData(),
            SELECTION_COLOR,
            DISPLAY_DURATION,
            true,  // wireframe/transparent
            null
        );
    }

    /**
     * Efface le visuel de la sélection pour le joueur.
     */
    @SuppressWarnings("deprecation")
    public void clearSelectionVisual(@NotNull Player player) {
        UUID playerId = player.getUuid();
        pos1Map.remove(playerId);
        pos2Map.remove(playerId);

        // Effacer les formes debug
        var connection = player.getPlayerConnection();
        if (connection != null) {
            connection.write(new ClearDebugShapes());
        }
    }
}
