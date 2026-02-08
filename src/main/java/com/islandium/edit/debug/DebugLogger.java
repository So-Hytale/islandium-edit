package com.islandium.edit.debug;

import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.operation.ClipboardHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger de debug dédié pour les opérations clipboard (copy/paste/rotate/flip).
 * Écrit dans un fichier séparé pour faciliter le débogage des transformations.
 *
 * Le fichier est écrit dans: plugins/IslandiumEdit/debug-edit.log
 */
public class DebugLogger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String SEPARATOR = "========================================";
    private static final String SUBSEP = "----------------------------------------";

    private static volatile DebugLogger instance;
    private final Path logFile;
    private final Object lock = new Object();
    private boolean enabled = true;

    private DebugLogger(@NotNull Path pluginDir) {
        this.logFile = pluginDir.resolve("debug-edit.log");
        try {
            Files.createDirectories(logFile.getParent());
            // Écrire le header au démarrage
            synchronized (lock) {
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), false)))) {
                    pw.println(SEPARATOR);
                    pw.println("IslandiumEdit Debug Log");
                    pw.println("Started: " + LocalDateTime.now());
                    pw.println(SEPARATOR);
                    pw.println();
                }
            }
        } catch (IOException e) {
            System.err.println("[IslandiumEdit-Debug] Failed to create debug log: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Initialise le debug logger.
     * @param pluginDir répertoire du plugin (ex: plugins/IslandiumEdit/)
     */
    public static void init(@NotNull Path pluginDir) {
        instance = new DebugLogger(pluginDir);
    }

    /**
     * Retourne l'instance du logger.
     */
    @Nullable
    public static DebugLogger get() {
        return instance;
    }

    /**
     * Arrête le logger proprement.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.log("SHUTDOWN", "Debug logger stopped at " + LocalDateTime.now());
            instance = null;
        }
    }

    // === Méthodes de log génériques ===

    public void log(@NotNull String tag, @NotNull String message) {
        if (!enabled) return;
        write("[" + now() + "] [" + tag + "] " + message);
    }

    public void logSection(@NotNull String title) {
        if (!enabled) return;
        write("");
        write(SEPARATOR);
        write("[" + now() + "] " + title);
        write(SEPARATOR);
    }

    public void logSubSection(@NotNull String title) {
        if (!enabled) return;
        write(SUBSEP);
        write("[" + now() + "] " + title);
    }

    // === Logs spécifiques aux opérations ===

    /**
     * Log une opération COPY.
     */
    public void logCopy(@NotNull String playerName, int playerX, int playerY, int playerZ,
                        int[] bounds, int width, int height, int depth,
                        int offsetX, int offsetY, int offsetZ, int blockCount) {
        logSection("COPY - " + playerName);
        write("  Player pos: (" + playerX + ", " + playerY + ", " + playerZ + ")");
        write("  Selection bounds: (" + bounds[0] + ", " + bounds[1] + ", " + bounds[2]
                + ") -> (" + bounds[3] + ", " + bounds[4] + ", " + bounds[5] + ")");
        write("  Dimensions: " + width + " x " + height + " x " + depth);
        write("  Offset from player: (" + offsetX + ", " + offsetY + ", " + offsetZ + ")");
        write("  Blocks copied: " + blockCount);
    }

    /**
     * Log une opération ROTATE.
     */
    public void logRotate(@NotNull String playerName, int degrees,
                          @NotNull AffineTransform oldTransform,
                          @NotNull AffineTransform newTransform) {
        logSection("ROTATE - " + playerName + " (" + degrees + " deg)");
        write("  Old transform: " + oldTransform);
        write("  New transform: " + newTransform);
        write("  New Y rotation: " + newTransform.getYRotation() + " deg");
        write("  Is flip X: " + newTransform.isFlipX());
        write("  Is flip Z: " + newTransform.isFlipZ());
        write("  Is vertical flip: " + newTransform.isVerticalFlip());
        write("  Is identity: " + newTransform.isIdentity());
        write("  Is horizontal flip: " + newTransform.isHorizontalFlip());
    }

    /**
     * Log une opération FLIP.
     */
    public void logFlip(@NotNull String playerName, @NotNull String axis,
                        @NotNull AffineTransform oldTransform,
                        @NotNull AffineTransform newTransform) {
        logSection("FLIP - " + playerName + " (axis=" + axis + ")");
        write("  Old transform: " + oldTransform);
        write("  New transform: " + newTransform);
        write("  New Y rotation: " + newTransform.getYRotation() + " deg");
        write("  Is flip X: " + newTransform.isFlipX());
        write("  Is flip Z: " + newTransform.isFlipZ());
        write("  Is vertical flip: " + newTransform.isVerticalFlip());
        write("  Is identity: " + newTransform.isIdentity());
        write("  Is horizontal flip: " + newTransform.isHorizontalFlip());
    }

    /**
     * Log une opération FLIP BY LOOK DIRECTION.
     */
    public void logFlipByLook(@NotNull String playerName, float pitch, float yaw,
                              double dirX, double dirY, double dirZ,
                              @NotNull String axis, @NotNull String direction,
                              @NotNull AffineTransform oldTransform,
                              @NotNull AffineTransform newTransform) {
        logSection("FLIP BY LOOK - " + playerName);
        write("  Head rotation: pitch=" + pitch + " yaw=" + yaw);
        write("  Direction vector: (" + String.format("%.4f", dirX) + ", "
                + String.format("%.4f", dirY) + ", " + String.format("%.4f", dirZ) + ")");
        write("  Abs components: X=" + String.format("%.4f", Math.abs(dirX))
                + " Y=" + String.format("%.4f", Math.abs(dirY))
                + " Z=" + String.format("%.4f", Math.abs(dirZ)));
        write("  Chosen axis: " + axis + " (direction: " + direction + ")");
        write("  Old transform: " + oldTransform);
        write("  New transform: " + newTransform);
        write("  New Y rotation: " + newTransform.getYRotation() + " deg");
        write("  Is flip X: " + newTransform.isFlipX());
        write("  Is flip Z: " + newTransform.isFlipZ());
    }

    /**
     * Log une opération PASTE (début).
     */
    public void logPasteStart(@NotNull String playerName, int playerX, int playerY, int playerZ,
                              boolean skipAir, @NotNull ClipboardData clipboard,
                              @NotNull AffineTransform transform) {
        logSection("PASTE - " + playerName + (skipAir ? " (skip air)" : ""));
        write("  Player pos: (" + playerX + ", " + playerY + ", " + playerZ + ")");
        write("  Clipboard dimensions: " + clipboard.getWidth() + " x "
                + clipboard.getHeight() + " x " + clipboard.getDepth());
        write("  Clipboard offset: (" + clipboard.getOffsetX() + ", "
                + clipboard.getOffsetY() + ", " + clipboard.getOffsetZ() + ")");
        write("  Transform: " + transform);
        write("  Transform Y rotation: " + transform.getYRotation() + " deg");
        write("  Is flip X: " + transform.isFlipX());
        write("  Is flip Z: " + transform.isFlipZ());
        write("  Is vertical flip: " + transform.isVerticalFlip());
        write("  Is identity: " + transform.isIdentity());
        write("  Block count in clipboard: " + clipboard.getBlockCount());
    }

    /**
     * Log le calcul de position d'un bloc durant le paste (pour les N premiers blocs).
     */
    public void logPasteBlock(int index, int clipX, int clipY, int clipZ,
                              double relX, double relY, double relZ,
                              double transformedX, double transformedY, double transformedZ,
                              int worldX, int worldY, int worldZ,
                              @NotNull String blockType,
                              int originalRotation, int transformedRotation) {
        write("  Block[" + index + "]: clip=(" + clipX + "," + clipY + "," + clipZ + ")"
                + " rel=(" + String.format("%.1f", relX) + "," + String.format("%.1f", relY) + "," + String.format("%.1f", relZ) + ")"
                + " -> transformed=(" + String.format("%.2f", transformedX) + "," + String.format("%.2f", transformedY) + "," + String.format("%.2f", transformedZ) + ")"
                + " -> world=(" + worldX + "," + worldY + "," + worldZ + ")"
                + " type=" + blockType
                + " rot=" + originalRotation + "->" + transformedRotation);
    }

    /**
     * Log la fin d'un paste.
     */
    public void logPasteEnd(int totalBlocks, int processed, int failed) {
        logSubSection("PASTE COMPLETE");
        write("  Total blocks: " + totalBlocks);
        write("  Processed: " + processed);
        write("  Failed: " + failed);
    }

    /**
     * Log la transformation de rotation d'un bloc.
     */
    public void logRotationTransform(int originalIndex, int yaw, int pitch, int roll,
                                     int yRotation, boolean flipX, boolean flipZ, boolean vFlip,
                                     int newYaw, int newPitch, int newRoll, int newIndex) {
        write("    RotTransform: idx=" + originalIndex
                + " decomp=(y=" + yaw + " p=" + pitch + " r=" + roll + ")"
                + " transform=(rotY=" + yRotation + " flipX=" + flipX + " flipZ=" + flipZ + " vFlip=" + vFlip + ")"
                + " -> new=(y=" + newYaw + " p=" + newPitch + " r=" + newRoll + ")"
                + " -> idx=" + newIndex);
    }

    /**
     * Log la transformation d'un block state (facing, axis, etc).
     */
    public void logBlockStateTransform(@NotNull String originalBlockId, @NotNull String transformedBlockId,
                                       int yRotation, boolean flipX, boolean flipZ, boolean vFlip) {
        if (!originalBlockId.equals(transformedBlockId)) {
            write("    BlockState: \"" + originalBlockId + "\" -> \"" + transformedBlockId + "\""
                    + " (rotY=" + yRotation + " flipX=" + flipX + " flipZ=" + flipZ + " vFlip=" + vFlip + ")");
        }
    }

    /**
     * Log la transformation de matrice (détail).
     */
    public void logMatrixOperation(@NotNull String operation,
                                   @NotNull AffineTransform before,
                                   @NotNull AffineTransform after) {
        write("  Matrix " + operation + ":");
        write("    Before: " + before);
        write("    After:  " + after);
    }

    /**
     * Log les données du clipboard holder.
     */
    public void logClipboardState(@NotNull String context, @NotNull ClipboardHolder holder) {
        ClipboardData clip = holder.getClipboard();
        AffineTransform transform = holder.getTransform();
        write("  [" + context + "] Clipboard state:");
        write("    Dimensions: " + clip.getWidth() + "x" + clip.getHeight() + "x" + clip.getDepth());
        write("    Offset: (" + clip.getOffsetX() + ", " + clip.getOffsetY() + ", " + clip.getOffsetZ() + ")");
        write("    OriginalOffset: (" + clip.getOriginalOffsetX() + ", " + clip.getOriginalOffsetY() + ", " + clip.getOriginalOffsetZ() + ")");
        write("    Block count: " + clip.getBlockCount());
        write("    Transform: " + transform);
        write("    Transform identity: " + transform.isIdentity());
    }

    /**
     * Log la preview.
     */
    public void logPreview(@NotNull String playerName, int playerX, int playerY, int playerZ,
                           boolean isTransformed, boolean persistent, int blockCount) {
        log("PREVIEW", playerName + " pos=(" + playerX + "," + playerY + "," + playerZ + ")"
                + " transformed=" + isTransformed + " persistent=" + persistent
                + " blocks=" + blockCount);
    }

    /**
     * Log un test de transformation de point (pour vérifier la matrice).
     */
    public void logPointTransform(@NotNull String context,
                                  double inX, double inY, double inZ,
                                  double outX, double outY, double outZ) {
        write("  [" + context + "] Point (" + String.format("%.2f", inX) + ", "
                + String.format("%.2f", inY) + ", " + String.format("%.2f", inZ) + ") -> ("
                + String.format("%.2f", outX) + ", " + String.format("%.2f", outY) + ", "
                + String.format("%.2f", outZ) + ")");
    }

    /**
     * Log une erreur.
     */
    public void logError(@NotNull String tag, @NotNull String message, @Nullable Throwable error) {
        write("[" + now() + "] [ERROR/" + tag + "] " + message);
        if (error != null) {
            write("  Exception: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            // Stack trace (premiers 5 éléments)
            StackTraceElement[] stack = error.getStackTrace();
            int max = Math.min(5, stack.length);
            for (int i = 0; i < max; i++) {
                write("    at " + stack[i]);
            }
        }
    }

    // === Internal ===

    private String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private void write(@NotNull String line) {
        synchronized (lock) {
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), true)))) {
                pw.println(line);
            } catch (IOException e) {
                // Silently ignore write errors to avoid spam
            }
        }
    }
}
