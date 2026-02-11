package com.islandium.edit.debug;

import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.operation.ClipboardHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Logger de debug dédié pour les opérations clipboard (copy/paste/rotate/flip).
 * Écrit dans des fichiers séparés par type d'opération + un fichier maître chronologique.
 *
 * Structure des fichiers dans: plugins/IslandiumEdit/logs/
 *   master.log    - Ordre chronologique de toutes les opérations (résumé)
 *   copy.log      - Détails des opérations COPY
 *   paste.log     - Détails des opérations PASTE
 *   flip.log      - Détails des opérations FLIP/ROTATE
 *   undo.log      - Détails des opérations UNDO/REDO
 *   preview.log   - Détails des opérations PREVIEW
 */
public class DebugLogger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String SEPARATOR = "========================================";
    private static final String SUBSEP = "----------------------------------------";

    private static volatile DebugLogger instance;
    private final Path logsDir;
    private final Path masterFile;
    private final Path copyFile;
    private final Path pasteFile;
    private final Path flipFile;
    private final Path undoFile;
    private final Path previewFile;
    private final Object lock = new Object();
    private boolean enabled = true;

    // Compteurs pour le fichier maître
    private final AtomicInteger operationCounter = new AtomicInteger(0);

    // Filtre de blocs: si non-vide, seuls les blocs dont le type contient un des patterns seront loggés
    private final Set<String> blockFilters = Collections.synchronizedSet(new LinkedHashSet<>());

    private DebugLogger(@NotNull Path pluginDir) {
        this.logsDir = pluginDir.resolve("logs");
        this.masterFile = logsDir.resolve("master.log");
        this.copyFile = logsDir.resolve("copy.log");
        this.pasteFile = logsDir.resolve("paste.log");
        this.flipFile = logsDir.resolve("flip.log");
        this.undoFile = logsDir.resolve("undo.log");
        this.previewFile = logsDir.resolve("preview.log");
        try {
            Files.createDirectories(logsDir);
            // Écrire les headers au démarrage (écrase les fichiers précédents)
            String startTime = LocalDateTime.now().toString();
            synchronized (lock) {
                writeHeader(masterFile, "MASTER LOG - Chronological order of all operations", startTime);
                writeHeader(copyFile, "COPY Operations", startTime);
                writeHeader(pasteFile, "PASTE Operations", startTime);
                writeHeader(flipFile, "FLIP / ROTATE Operations", startTime);
                writeHeader(undoFile, "UNDO / REDO Operations", startTime);
                writeHeader(previewFile, "PREVIEW Operations", startTime);
            }
        } catch (IOException e) {
            System.err.println("[IslandiumEdit-Debug] Failed to create debug logs: " + e.getMessage());
            enabled = false;
        }
    }

    private void writeHeader(@NotNull Path file, @NotNull String title, @NotNull String startTime) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file.toFile(), false)))) {
            pw.println(SEPARATOR);
            pw.println(title);
            pw.println("Started: " + startTime);
            pw.println(SEPARATOR);
            pw.println();
        }
    }

    /**
     * Initialise le debug logger.
     * @param pluginDir répertoire du plugin (ex: plugins/IslandiumEdit/)
     */
    public static void init(@NotNull Path pluginDir) {
        instance = new DebugLogger(pluginDir);
    }

    @Nullable
    public static DebugLogger get() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.logMaster("SHUTDOWN", "Debug logger stopped");
            instance = null;
        }
    }

    // === Filtre de blocs ===

    public void setBlockFilters(@NotNull Set<String> patterns) {
        blockFilters.clear();
        blockFilters.addAll(patterns);
        logMaster("FILTER", "Block filter set: " + (patterns.isEmpty() ? "(disabled)" : String.join(", ", patterns)));
    }

    public void clearBlockFilters() {
        blockFilters.clear();
        logMaster("FILTER", "Block filter cleared (all blocks)");
    }

    @NotNull
    public Set<String> getBlockFilters() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blockFilters));
    }

    public boolean matchesBlockFilter(@NotNull String blockType) {
        if (blockFilters.isEmpty()) return true;
        for (String pattern : blockFilters) {
            if (blockType.contains(pattern)) return true;
        }
        return false;
    }

    // === Méthodes de log génériques (redirige vers le bon fichier) ===

    /**
     * Log dans le fichier correspondant au tag + dans master.
     */
    public void log(@NotNull String tag, @NotNull String message) {
        if (!enabled) return;
        String line = "[" + now() + "] [" + tag + "] " + message;
        Path target = resolveFile(tag);
        write(target, line);
    }

    public void logSection(@NotNull String title) {
        if (!enabled) return;
        String tag = extractTag(title);
        Path target = resolveFile(tag);
        write(target, "");
        write(target, SEPARATOR);
        write(target, "[" + now() + "] " + title);
        write(target, SEPARATOR);
    }

    public void logSubSection(@NotNull String title) {
        if (!enabled) return;
        String tag = extractTag(title);
        Path target = resolveFile(tag);
        write(target, SUBSEP);
        write(target, "[" + now() + "] " + title);
    }

    // === Fichier MASTER ===

    private void logMaster(@NotNull String tag, @NotNull String summary) {
        if (!enabled) return;
        int opNum = operationCounter.incrementAndGet();
        String line = "[" + now() + "] #" + opNum + " [" + tag + "] " + summary;
        write(masterFile, line);
    }

    // === Logs spécifiques aux opérations ===

    public void logCopy(@NotNull String playerName, int playerX, int playerY, int playerZ,
                        int[] bounds, int width, int height, int depth,
                        int offsetX, int offsetY, int offsetZ, int blockCount) {
        // Master
        logMaster("COPY", playerName + " - " + blockCount + " blocks, " + width + "x" + height + "x" + depth
                + " at (" + playerX + "," + playerY + "," + playerZ + ")");
        // Détail dans copy.log
        write(copyFile, "");
        write(copyFile, SEPARATOR);
        write(copyFile, "[" + now() + "] COPY - " + playerName);
        write(copyFile, SEPARATOR);
        write(copyFile, "  Player pos: (" + playerX + ", " + playerY + ", " + playerZ + ")");
        write(copyFile, "  Selection bounds: (" + bounds[0] + ", " + bounds[1] + ", " + bounds[2]
                + ") -> (" + bounds[3] + ", " + bounds[4] + ", " + bounds[5] + ")");
        write(copyFile, "  Dimensions: " + width + " x " + height + " x " + depth);
        write(copyFile, "  Offset from player: (" + offsetX + ", " + offsetY + ", " + offsetZ + ")");
        write(copyFile, "  Blocks copied: " + blockCount);
    }

    public void logRotate(@NotNull String playerName, int degrees,
                          @NotNull AffineTransform oldTransform,
                          @NotNull AffineTransform newTransform) {
        logMaster("ROTATE", playerName + " - " + degrees + " deg"
                + " flipX=" + newTransform.isFlipX() + " flipZ=" + newTransform.isFlipZ());
        write(flipFile, "");
        write(flipFile, SEPARATOR);
        write(flipFile, "[" + now() + "] ROTATE - " + playerName + " (" + degrees + " deg)");
        write(flipFile, SEPARATOR);
        write(flipFile, "  Old transform: " + oldTransform);
        write(flipFile, "  New transform: " + newTransform);
        write(flipFile, "  New Y rotation: " + newTransform.getYRotation() + " deg");
        write(flipFile, "  Is flip X: " + newTransform.isFlipX());
        write(flipFile, "  Is flip Z: " + newTransform.isFlipZ());
        write(flipFile, "  Is vertical flip: " + newTransform.isVerticalFlip());
        write(flipFile, "  Is identity: " + newTransform.isIdentity());
        write(flipFile, "  Is horizontal flip: " + newTransform.isHorizontalFlip());
    }

    public void logFlip(@NotNull String playerName, @NotNull String axis,
                        @NotNull AffineTransform oldTransform,
                        @NotNull AffineTransform newTransform) {
        logMaster("FLIP", playerName + " - axis=" + axis
                + " flipX=" + newTransform.isFlipX() + " flipZ=" + newTransform.isFlipZ());
        write(flipFile, "");
        write(flipFile, SEPARATOR);
        write(flipFile, "[" + now() + "] FLIP - " + playerName + " (axis=" + axis + ")");
        write(flipFile, SEPARATOR);
        write(flipFile, "  Old transform: " + oldTransform);
        write(flipFile, "  New transform: " + newTransform);
        write(flipFile, "  New Y rotation: " + newTransform.getYRotation() + " deg");
        write(flipFile, "  Is flip X: " + newTransform.isFlipX());
        write(flipFile, "  Is flip Z: " + newTransform.isFlipZ());
        write(flipFile, "  Is vertical flip: " + newTransform.isVerticalFlip());
        write(flipFile, "  Is identity: " + newTransform.isIdentity());
        write(flipFile, "  Is horizontal flip: " + newTransform.isHorizontalFlip());
    }

    public void logFlipByLook(@NotNull String playerName, float pitch, float yaw,
                              double dirX, double dirY, double dirZ,
                              @NotNull String axis, @NotNull String direction,
                              @NotNull AffineTransform oldTransform,
                              @NotNull AffineTransform newTransform) {
        logMaster("FLIP", playerName + " - by look, axis=" + axis + " (" + direction + ")"
                + " flipX=" + newTransform.isFlipX() + " flipZ=" + newTransform.isFlipZ());
        write(flipFile, "");
        write(flipFile, SEPARATOR);
        write(flipFile, "[" + now() + "] FLIP BY LOOK - " + playerName);
        write(flipFile, SEPARATOR);
        write(flipFile, "  Head rotation: pitch=" + pitch + " yaw=" + yaw);
        write(flipFile, "  Direction vector: (" + String.format("%.4f", dirX) + ", "
                + String.format("%.4f", dirY) + ", " + String.format("%.4f", dirZ) + ")");
        write(flipFile, "  Abs components: X=" + String.format("%.4f", Math.abs(dirX))
                + " Y=" + String.format("%.4f", Math.abs(dirY))
                + " Z=" + String.format("%.4f", Math.abs(dirZ)));
        write(flipFile, "  Chosen axis: " + axis + " (direction: " + direction + ")");
        write(flipFile, "  Old transform: " + oldTransform);
        write(flipFile, "  New transform: " + newTransform);
        write(flipFile, "  New Y rotation: " + newTransform.getYRotation() + " deg");
        write(flipFile, "  Is flip X: " + newTransform.isFlipX());
        write(flipFile, "  Is flip Z: " + newTransform.isFlipZ());
    }

    public void logPasteStart(@NotNull String playerName, int playerX, int playerY, int playerZ,
                              boolean skipAir, @NotNull ClipboardData clipboard,
                              @NotNull AffineTransform transform) {
        logMaster("PASTE", playerName + " - " + clipboard.getBlockCount() + " blocks, "
                + clipboard.getWidth() + "x" + clipboard.getHeight() + "x" + clipboard.getDepth()
                + " at (" + playerX + "," + playerY + "," + playerZ + ")"
                + (skipAir ? " (skip air)" : "")
                + " flipX=" + transform.isFlipX() + " flipZ=" + transform.isFlipZ());
        write(pasteFile, "");
        write(pasteFile, SEPARATOR);
        write(pasteFile, "[" + now() + "] PASTE - " + playerName + (skipAir ? " (skip air)" : ""));
        write(pasteFile, SEPARATOR);
        write(pasteFile, "  Player pos: (" + playerX + ", " + playerY + ", " + playerZ + ")");
        write(pasteFile, "  Clipboard dimensions: " + clipboard.getWidth() + " x "
                + clipboard.getHeight() + " x " + clipboard.getDepth());
        write(pasteFile, "  Clipboard offset: (" + clipboard.getOffsetX() + ", "
                + clipboard.getOffsetY() + ", " + clipboard.getOffsetZ() + ")");
        write(pasteFile, "  Transform: " + transform);
        write(pasteFile, "  Transform Y rotation: " + transform.getYRotation() + " deg");
        write(pasteFile, "  Is flip X: " + transform.isFlipX());
        write(pasteFile, "  Is flip Z: " + transform.isFlipZ());
        write(pasteFile, "  Is vertical flip: " + transform.isVerticalFlip());
        write(pasteFile, "  Is identity: " + transform.isIdentity());
        write(pasteFile, "  Block count in clipboard: " + clipboard.getBlockCount());
    }

    public void logPasteBlock(int index, int clipX, int clipY, int clipZ,
                              double relX, double relY, double relZ,
                              double transformedX, double transformedY, double transformedZ,
                              int worldX, int worldY, int worldZ,
                              @NotNull String blockType,
                              int originalRotation, int transformedRotation) {
        write(pasteFile, "  Block[" + index + "]: clip=(" + clipX + "," + clipY + "," + clipZ + ")"
                + " rel=(" + String.format("%.1f", relX) + "," + String.format("%.1f", relY) + "," + String.format("%.1f", relZ) + ")"
                + " -> transformed=(" + String.format("%.2f", transformedX) + "," + String.format("%.2f", transformedY) + "," + String.format("%.2f", transformedZ) + ")"
                + " -> world=(" + worldX + "," + worldY + "," + worldZ + ")"
                + " type=" + blockType
                + " rot=" + originalRotation + "->" + transformedRotation);
    }

    public void logPasteEnd(int totalBlocks, int processed, int failed) {
        write(pasteFile, SUBSEP);
        write(pasteFile, "[" + now() + "] PASTE COMPLETE");
        write(pasteFile, "  Total blocks: " + totalBlocks);
        write(pasteFile, "  Processed: " + processed);
        write(pasteFile, "  Failed: " + failed);
    }

    public void logRotationTransform(int originalIndex, int yaw, int pitch, int roll,
                                     int yRotation, boolean flipX, boolean flipZ, boolean vFlip,
                                     int newYaw, int newPitch, int newRoll, int newIndex) {
        write(pasteFile, "    RotTransform: idx=" + originalIndex
                + " decomp=(y=" + yaw + " p=" + pitch + " r=" + roll + ")"
                + " transform=(rotY=" + yRotation + " flipX=" + flipX + " flipZ=" + flipZ + " vFlip=" + vFlip + ")"
                + " -> new=(y=" + newYaw + " p=" + newPitch + " r=" + newRoll + ")"
                + " -> idx=" + newIndex);
    }

    public void logBlockStateTransform(@NotNull String originalBlockId, @NotNull String transformedBlockId,
                                       int yRotation, boolean flipX, boolean flipZ, boolean vFlip) {
        if (!originalBlockId.equals(transformedBlockId)) {
            write(pasteFile, "    BlockState: \"" + originalBlockId + "\" -> \"" + transformedBlockId + "\""
                    + " (rotY=" + yRotation + " flipX=" + flipX + " flipZ=" + flipZ + " vFlip=" + vFlip + ")");
        }
    }

    public void logMatrixOperation(@NotNull String operation,
                                   @NotNull AffineTransform before,
                                   @NotNull AffineTransform after) {
        write(flipFile, "  Matrix " + operation + ":");
        write(flipFile, "    Before: " + before);
        write(flipFile, "    After:  " + after);
    }

    public void logClipboardState(@NotNull String context, @NotNull ClipboardHolder holder) {
        ClipboardData clip = holder.getClipboard();
        AffineTransform transform = holder.getTransform();
        // Écrire dans flip.log (c'est utilisé après flip/rotate)
        write(flipFile, "  [" + context + "] Clipboard state:");
        write(flipFile, "    Dimensions: " + clip.getWidth() + "x" + clip.getHeight() + "x" + clip.getDepth());
        write(flipFile, "    Offset: (" + clip.getOffsetX() + ", " + clip.getOffsetY() + ", " + clip.getOffsetZ() + ")");
        write(flipFile, "    OriginalOffset: (" + clip.getOriginalOffsetX() + ", " + clip.getOriginalOffsetY() + ", " + clip.getOriginalOffsetZ() + ")");
        write(flipFile, "    Block count: " + clip.getBlockCount());
        write(flipFile, "    Transform: " + transform);
        write(flipFile, "    Transform identity: " + transform.isIdentity());
    }

    public void logPreview(@NotNull String playerName, int playerX, int playerY, int playerZ,
                           boolean isTransformed, boolean persistent, int blockCount) {
        write(previewFile, "[" + now() + "] " + playerName + " pos=(" + playerX + "," + playerY + "," + playerZ + ")"
                + " transformed=" + isTransformed + " persistent=" + persistent
                + " blocks=" + blockCount);
    }

    public void logPointTransform(@NotNull String context,
                                  double inX, double inY, double inZ,
                                  double outX, double outY, double outZ) {
        write(pasteFile, "  [" + context + "] Point (" + String.format("%.2f", inX) + ", "
                + String.format("%.2f", inY) + ", " + String.format("%.2f", inZ) + ") -> ("
                + String.format("%.2f", outX) + ", " + String.format("%.2f", outY) + ", "
                + String.format("%.2f", outZ) + ")");
    }

    public void logError(@NotNull String tag, @NotNull String message, @Nullable Throwable error) {
        Path target = resolveFile(tag);
        write(target, "[" + now() + "] [ERROR/" + tag + "] " + message);
        if (error != null) {
            write(target, "  Exception: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            StackTraceElement[] stack = error.getStackTrace();
            int max = Math.min(5, stack.length);
            for (int i = 0; i < max; i++) {
                write(target, "    at " + stack[i]);
            }
        }
        logMaster("ERROR", "[" + tag + "] " + message);
    }

    // === ZIP ===

    /**
     * Crée un fichier ZIP contenant tous les fichiers de log.
     * @return le chemin du fichier ZIP créé, ou null en cas d'erreur
     */
    @Nullable
    public Path zipLogs() {
        if (!enabled) return null;
        String timestamp = LocalDateTime.now().format(FILE_TIME_FMT);
        Path zipFile = logsDir.resolve("logs_" + timestamp + ".zip");

        Path[] logFiles = { masterFile, copyFile, pasteFile, flipFile, undoFile, previewFile };

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            for (Path logFile : logFiles) {
                if (Files.exists(logFile) && Files.size(logFile) > 0) {
                    zos.putNextEntry(new ZipEntry(logFile.getFileName().toString()));
                    Files.copy(logFile, zos);
                    zos.closeEntry();
                }
            }
            logMaster("ZIP", "Logs zipped to " + zipFile.getFileName());
            return zipFile;
        } catch (IOException e) {
            System.err.println("[IslandiumEdit-Debug] Failed to zip logs: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retourne le répertoire des logs.
     */
    @NotNull
    public Path getLogsDir() {
        return logsDir;
    }

    // === Internal ===

    /**
     * Détermine le fichier cible en fonction du tag.
     */
    private Path resolveFile(@NotNull String tag) {
        String upper = tag.toUpperCase();
        if (upper.startsWith("COPY")) return copyFile;
        if (upper.startsWith("PASTE")) return pasteFile;
        if (upper.startsWith("FLIP") || upper.startsWith("ROTATE")) return flipFile;
        if (upper.startsWith("UNDO") || upper.startsWith("REDO")) return undoFile;
        if (upper.startsWith("PREVIEW")) return previewFile;
        if (upper.startsWith("FILTER")) return masterFile;
        return masterFile;
    }

    /**
     * Extrait le tag d'un titre de section (ex: "COPY - PlayerName" -> "COPY").
     */
    private String extractTag(@NotNull String title) {
        int dashIdx = title.indexOf(" - ");
        if (dashIdx > 0) return title.substring(0, dashIdx).trim();
        int spaceIdx = title.indexOf(' ');
        if (spaceIdx > 0) return title.substring(0, spaceIdx).trim();
        return title.trim();
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    private void write(@NotNull Path file, @NotNull String line) {
        synchronized (lock) {
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file.toFile(), true)))) {
                pw.println(line);
            } catch (IOException e) {
                // Silently ignore write errors to avoid spam
            }
        }
    }
}
