package com.islandium.edit;

import com.hypixel.hytale.builtin.hytalegenerator.LoggerUtil;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.islandium.core.IslandiumPlugin;
import com.islandium.edit.command.DirectCommands;
import com.islandium.edit.command.EditCommand;
import com.islandium.edit.debug.DebugLogger;
import com.islandium.edit.history.EditHistory;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.islandium.edit.interaction.WandInteraction;
import com.islandium.edit.math.RotationOverrides;
import com.islandium.edit.operation.BlockOperations;
import com.islandium.edit.operation.ClipboardOperations;
import com.islandium.edit.preview.PreviewManager;
import com.islandium.edit.selection.SelectionManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin WorldEdit pour Hytale.
 * Fournit des outils d'édition de terrain avancés avec sélection visuelle.
 *
 * Fonctionnalités:
 * - Wand (edit_wand) pour sélection visuelle pos1/pos2
 * - Opérations: set, replace, walls, floor, ceiling, outline, clear
 * - Formes: sphere, cylinder, pyramid, cone (pleins ou creux)
 * - Clipboard: copy, paste, rotate, flip
 * - Historique: undo, redo
 */
public class EditPlugin extends JavaPlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger();

    /** ID de l'item wand custom */
    public static final String WAND_ITEM_ID = "Edit_Wand";

    /** Blocs traités par batch */
    public static final int BLOCKS_PER_BATCH = 5000;

    /** Délai entre les batches en ms */
    public static final int BATCH_DELAY_MS = 50;

    private static volatile EditPlugin instance;
    private static volatile boolean initialized = false;

    private IslandiumPlugin corePlugin;
    private SelectionManager selectionManager;
    private BlockOperations blockOperations;
    private ClipboardOperations clipboardOperations;
    private EditHistory editHistory;
    private PreviewManager previewManager;

    public EditPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;
        log(Level.INFO, "Initializing IslandiumEdit (WorldEdit for Hytale)...");

        try {
            // 1. Vérifier que islandium-core est chargé
            if (!IslandiumPlugin.isInitialized()) {
                throw new IllegalStateException("IslandiumCore must be loaded before IslandiumEdit!");
            }
            this.corePlugin = IslandiumPlugin.get();

            // 1b. Initialiser le debug logger et les rotation overrides
            log(Level.INFO, "Initializing debug logger...");
            DebugLogger.init(Path.of("mods"));
            log(Level.INFO, "Loading rotation overrides...");
            RotationOverrides.init(Path.of("mods"));

            // 2. Initialiser les managers
            log(Level.INFO, "Initializing managers...");
            this.selectionManager = new SelectionManager();
            this.editHistory = new EditHistory(50); // 50 actions max par joueur
            this.blockOperations = new BlockOperations(this);
            this.clipboardOperations = new ClipboardOperations(this);
            this.previewManager = new PreviewManager(this);

            // 3. Enregistrer les commandes
            log(Level.INFO, "Registering commands...");
            getCommandRegistry().registerCommand(new EditCommand(this));

            // 4. Enregistrer les commandes directes (/eset, /eundo, /ecopy, etc.)
            log(Level.INFO, "Registering direct commands (/eset, /eundo, /ecopy, etc.)...");
            DirectCommands.registerAll(this);

            initialized = true;
            log(Level.INFO, "IslandiumEdit initialized successfully!");
            log(Level.INFO, "Use /edit help for command list.");
            log(Level.INFO, "Use /edit wand to get the selection wand.");

        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize IslandiumEdit: " + e.getMessage());
            e.printStackTrace();
        }

        // Enregistrer l'interaction custom pour la wand
        log(Level.INFO, "Registering wand interaction...");
        this.getCodecRegistry(Interaction.CODEC)
            .register("Edit_Wand", WandInteraction.class, WandInteraction.CODEC);
        log(Level.INFO, "Wand interaction registered!");
    }

    @Override
    protected void start() {

    }

    /**
     * Appelé lors de l'arrêt du serveur.
     */
    public void shutdown() {
        log(Level.INFO, "Shutting down IslandiumEdit...");

        try {
            // Shutdown debug logger
            DebugLogger.shutdown();

            if (blockOperations != null) {
                blockOperations.shutdown();
            }

            if (previewManager != null) {
                previewManager.shutdown();
            }

            if (editHistory != null) {
                editHistory.clearAll();
            }

            log(Level.INFO, "IslandiumEdit shut down successfully!");

        } catch (Exception e) {
            log(Level.SEVERE, "Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }

        initialized = false;
        instance = null;
    }

    // === Getters ===

    @NotNull
    public static EditPlugin get() {
        if (instance == null || !initialized) {
            throw new IllegalStateException("IslandiumEdit is not yet initialized!");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null && initialized;
    }

    @NotNull
    public IslandiumPlugin getCore() {
        return corePlugin;
    }

    @NotNull
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @NotNull
    public BlockOperations getBlockOperations() {
        return blockOperations;
    }

    @NotNull
    public ClipboardOperations getClipboardOperations() {
        return clipboardOperations;
    }

    @NotNull
    public EditHistory getEditHistory() {
        return editHistory;
    }

    @NotNull
    public PreviewManager getPreviewManager() {
        return previewManager;
    }

    // === Logging ===

    public void log(Level level, String message) {
        LOGGER.log(level, "[IslandiumEdit] " + message);
    }
}
