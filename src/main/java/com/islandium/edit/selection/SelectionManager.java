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

/**
 * Manager pour les selections utilisant l'API native BuilderToolsPlugin.
 * Lit et ecrit directement dans la BlockSelection native de Hytale.
 * Compatible avec le Selection Tool natif ET la wand custom.
 * Envoie une visualisation debug (cadre vert) quand la selection est modifiee
 * via la wand ou les commandes /epos1 /epos2.
 */
public class SelectionManager {

    // Couleur de la selection (vert lime)
    private static final Vector3f SELECTION_COLOR = new Vector3f(0.2f, 1.0f, 0.2f);
    // Duree d'affichage en secondes
    private static final float DISPLAY_DURATION = 300.0f;
    // Epaisseur des lignes de la boite
    private static final double LINE_THICKNESS = 0.05;

    /**
     * Obtient le BuilderState du joueur.
     * Utilise player.getPlayerRef() qui fonctionne depuis n'importe quel thread,
     * contrairement a store.getComponent() qui exige le WorldThread.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public BuilderToolsPlugin.BuilderState getBuilderState(@NotNull Player player) {
        try {
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) {
                return null;
            }
            return BuilderToolsPlugin.getState(player, playerRef);
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    /**
     * Obtient la BlockSelection native du joueur.
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
     * Verifie si le joueur a une selection valide (pos1 et pos2 definies).
     * Lit directement depuis la selection native Hytale.
     */
    public boolean hasValidSelection(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        return selection != null && selection.hasSelectionBounds();
    }

    /**
     * Obtient le coin minimum de la selection.
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
     * Obtient le coin maximum de la selection.
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
     * Definit la position 1 de la selection.
     * Ecrit directement dans la BlockSelection native, comme le ferait l'outil de selection Hytale.
     * Garde la position 2 existante si presente.
     * Envoie la visualisation debug au client.
     *
     * @return true si la position a ete definie avec succes
     */
    public boolean setPos1(@NotNull Player player, @NotNull Vector3i pos) {
        try {
            BlockSelection selection = getSelection(player);
            if (selection == null) {
                return false;
            }

            // Recuperer la pos2 existante, ou utiliser pos comme fallback
            Vector3i pos2 = selection.hasSelectionBounds() ? selection.getSelectionMax() : pos;
            selection.setSelectionArea(pos, pos2);

            // Envoyer le visuel au client
            sendSelectionVisual(player);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Definit la position 2 de la selection.
     * Ecrit directement dans la BlockSelection native, comme le ferait l'outil de selection Hytale.
     * Garde la position 1 existante si presente.
     * Envoie la visualisation debug au client.
     *
     * @return true si la position a ete definie avec succes
     */
    public boolean setPos2(@NotNull Player player, @NotNull Vector3i pos) {
        try {
            BlockSelection selection = getSelection(player);
            if (selection == null) {
                return false;
            }

            // Recuperer la pos1 existante, ou utiliser pos comme fallback
            Vector3i pos1 = selection.hasSelectionBounds() ? selection.getSelectionMin() : pos;
            selection.setSelectionArea(pos1, pos);

            // Envoyer le visuel au client
            sendSelectionVisual(player);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Definit les deux positions de la selection.
     *
     * @return true si les positions ont ete definies avec succes
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
     * Efface la selection du joueur.
     *
     * @return true si la selection a ete effacee
     */
    @SuppressWarnings("deprecation")
    public boolean clearSelection(@NotNull Player player) {
        try {
            BlockSelection selection = getSelection(player);
            if (selection == null) {
                return false;
            }

            // Definir une selection vide (meme point)
            selection.setSelectionArea(new Vector3i(0, 0, 0), new Vector3i(0, 0, 0));

            // Effacer les formes debug
            var connection = player.getPlayerConnection();
            if (connection != null) {
                connection.write(new ClearDebugShapes());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtient les bounds de la selection sous forme de tableau.
     * Lit directement depuis la selection native Hytale.
     *
     * @return int[] {minX, minY, minZ, maxX, maxY, maxZ} ou null si pas de selection
     */
    @Nullable
    public int[] getSelectionBounds(@NotNull Player player) {
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
     * Calcule le volume de la selection en nombre de blocs.
     *
     * @return le volume ou 0 si pas de selection valide
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
     * Obtient les dimensions de la selection.
     *
     * @return int[] {width, height, depth} ou null si pas de selection
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

    // ==================== Visualisation debug ====================

    /**
     * Envoie le visuel de la selection au client via DisplayDebug packets.
     * Lit les positions depuis la BlockSelection native.
     */
    @SuppressWarnings("deprecation")
    public void sendSelectionVisual(@NotNull Player player) {
        int[] bounds = getSelectionBounds(player);
        if (bounds == null) {
            return;
        }

        var connection = player.getPlayerConnection();
        if (connection == null) {
            return;
        }

        // Effacer les anciennes formes debug
        connection.write(new ClearDebugShapes());

        // Calculer min/max (inclusif, donc +1 sur max)
        double minX = bounds[0];
        double minY = bounds[1];
        double minZ = bounds[2];
        double maxX = bounds[3] + 1;
        double maxY = bounds[4] + 1;
        double maxZ = bounds[5] + 1;

        // Construire les 12 aretes de la boite
        List<DisplayDebug> packets = buildBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);

        // Envoyer tous les packets
        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    /**
     * Construit les 12 aretes d'une boite 3D.
     */
    private List<DisplayDebug> buildBoxEdges(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        List<DisplayDebug> packets = new ArrayList<>();

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;

        // 4 aretes horizontales en bas (Y = minY)
        packets.add(createEdge(minX + sizeX/2, minY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX + sizeX/2, minY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));
        packets.add(createEdge(maxX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));

        // 4 aretes horizontales en haut (Y = maxY)
        packets.add(createEdge(minX + sizeX/2, maxY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX + sizeX/2, maxY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));
        packets.add(createEdge(maxX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));

        // 4 aretes verticales (piliers)
        packets.add(createEdge(minX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(maxX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(minX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(maxX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));

        return packets;
    }

    /**
     * Cree un packet DisplayDebug pour une arete (cube allonge).
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
            true,
            null,
            1.0f
        );
    }
}
