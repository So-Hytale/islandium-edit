package com.islandium.edit.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.util.ColorUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.debug.DebugLogger;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.math.BlockSizeHelper;
import com.islandium.edit.math.RotationOverrides;
import com.islandium.edit.operation.BlockOperations;
import com.islandium.edit.shape.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Commandes directes style WorldEdit (eset, eundo, ecopy, etc.)
 * Ces commandes sont des raccourcis vers les sous-commandes de /edit.
 */
public class DirectCommands {

    /**
     * Enregistre toutes les commandes directes dans le plugin.
     */
    public static void registerAll(@NotNull EditPlugin plugin) {
        var registry = plugin.getCommandRegistry();

        // Sélection
        registry.registerCommand(new WandCommand(plugin));
        registry.registerCommand(new Pos1Command(plugin));
        registry.registerCommand(new Pos2Command(plugin));

        // Opérations de base
        registry.registerCommand(new SetCommand(plugin));
        registry.registerCommand(new ReplaceCommand(plugin));
        registry.registerCommand(new ClearCommand(plugin));

        // Murs/Sol/Plafond
        registry.registerCommand(new WallsCommand(plugin));
        registry.registerCommand(new FloorCommand(plugin));
        registry.registerCommand(new CeilingCommand(plugin));
        registry.registerCommand(new OutlineCommand(plugin));

        // Clipboard
        registry.registerCommand(new CopyCommand(plugin));
        registry.registerCommand(new CutCommand(plugin));
        registry.registerCommand(new PasteCommand(plugin));
        registry.registerCommand(new RotateCommand(plugin));
        registry.registerCommand(new FlipCommand(plugin));

        // Undo/Redo
        registry.registerCommand(new UndoCommand(plugin));
        registry.registerCommand(new RedoCommand(plugin));

        // Formes
        registry.registerCommand(new SphereCommand(plugin, false));
        registry.registerCommand(new SphereCommand(plugin, true));
        registry.registerCommand(new CylCommand(plugin, false));
        registry.registerCommand(new CylCommand(plugin, true));
        registry.registerCommand(new PyramidCommand(plugin, false));
        registry.registerCommand(new PyramidCommand(plugin, true));
        registry.registerCommand(new ConeCommand(plugin, false));
        registry.registerCommand(new ConeCommand(plugin, true));
        registry.registerCommand(new DomeCommand(plugin, false));
        registry.registerCommand(new DomeCommand(plugin, true));

        // Nouvelles commandes
        registry.registerCommand(new FillCommand(plugin));
        registry.registerCommand(new FillAirCommand(plugin));
        registry.registerCommand(new ReplaceNearCommand(plugin));

        // Info
        registry.registerCommand(new SizeCommand(plugin));
        registry.registerCommand(new DebugDirCommand(plugin));
        registry.registerCommand(new RotDebugCommand(plugin));

        // Preview
        registry.registerCommand(new PreviewCommand(plugin));
        registry.registerCommand(new PreviewStopCommand(plugin));
        registry.registerCommand(new PreviewInfoCommand(plugin));

        // Freeze
        registry.registerCommand(new FreezeCommand(plugin));

        // Reset
        registry.registerCommand(new NoneCommand(plugin));

        // Admin
        registry.registerCommand(new ReloadCommand(plugin));
        registry.registerCommand(new DebugFilterCommand());
        registry.registerCommand(new BlockInfoCommand(plugin));
    }

    // === Helper ===

    static void sendResult(CommandContext ctx, BlockOperations.OperationResult result) {
        if (result.success()) {
            ctx.sendMessage(ColorUtil.parse("&a" + result.message() + " &7(" + result.blocksAffected() + " blocs)"));
        } else {
            ctx.sendMessage(ColorUtil.parse("&c" + result.message()));
        }
    }

    // === Sélection ===

    public static class WandCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public WandCommand(EditPlugin plugin) {
            super("ewand", "Obtenir la wand de selection");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            try {
                var inventory = player.getInventory();
                if (inventory == null) {
                    ctx.sendMessage(ColorUtil.parse("&cErreur: inventaire introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                var hotbar = inventory.getHotbar();
                if (hotbar == null) {
                    ctx.sendMessage(ColorUtil.parse("&cErreur: hotbar introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                var wand = new com.hypixel.hytale.server.core.inventory.ItemStack(EditPlugin.WAND_ITEM_ID, 1);
                var transaction = hotbar.addItemStack(wand);

                if (transaction.succeeded()) {
                    ctx.sendMessage(ColorUtil.parse("&aWand ajoutee! &7(Clic gauche = Pos1, Clic droit = Pos2)"));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&cHotbar pleine!"));
                }
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class Pos1Command extends AbstractCommand {
        private final EditPlugin plugin;

        public Pos1Command(EditPlugin plugin) {
            super("epos1", "Definir pos1 a votre position");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            try {
                var transform = player.getTransformComponent();
                var pos = transform.getPosition();
                var vec = new com.hypixel.hytale.math.vector.Vector3i(
                        (int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

                if (plugin.getSelectionManager().setPos1(player, vec)) {
                    ctx.sendMessage(ColorUtil.parse("&aPos1: &f" + vec.getX() + ", " + vec.getY() + ", " + vec.getZ()));
                    if (plugin.getSelectionManager().hasValidSelection(player)) {
                        long volume = plugin.getSelectionManager().getVolume(player);
                        ctx.sendMessage(ColorUtil.parse("&7Selection: " + volume + " blocs"));
                    }
                }
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class Pos2Command extends AbstractCommand {
        private final EditPlugin plugin;

        public Pos2Command(EditPlugin plugin) {
            super("epos2", "Definir pos2 a votre position");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            try {
                var transform = player.getTransformComponent();
                var pos = transform.getPosition();
                var vec = new com.hypixel.hytale.math.vector.Vector3i(
                        (int) pos.getX(), (int) pos.getY(), (int) pos.getZ());

                if (plugin.getSelectionManager().setPos2(player, vec)) {
                    ctx.sendMessage(ColorUtil.parse("&aPos2: &f" + vec.getX() + ", " + vec.getY() + ", " + vec.getZ()));
                    if (plugin.getSelectionManager().hasValidSelection(player)) {
                        long volume = plugin.getSelectionManager().getVolume(player);
                        ctx.sendMessage(ColorUtil.parse("&7Selection: " + volume + " blocs"));
                    }
                }
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // === Opérations de base ===

    public static class SetCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public SetCommand(EditPlugin plugin) {
            super("eset", "Remplir la selection (supporte 20%a,80%b)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Remplissage avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().fill(player, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ReplaceCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> fromArg;
        private final RequiredArg<String> toArg;

        public ReplaceCommand(EditPlugin plugin) {
            super("ereplace", "Remplacer des blocs (to: patterns %)");
            this.plugin = plugin;
            fromArg = withRequiredArg("from", "Bloc source", ArgTypes.STRING);
            toArg = withRequiredArg("to", "Pattern destination", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolvedFrom = plugin.getBlockOperations().resolveBlockTypeWithError(player, ctx.get(fromArg));
            if (!resolvedFrom.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c[from] " + resolvedFrom.error()));
                return CompletableFuture.completedFuture(null);
            }

            var resolvedTo = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(toArg));
            if (!resolvedTo.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c[to] " + resolvedTo.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Remplacement " + resolvedFrom.blockType() + " -> " + resolvedTo.pattern() + "..."));
            return plugin.getBlockOperations().replace(player, resolvedFrom.blockType(), resolvedTo.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ClearCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public ClearCommand(EditPlugin plugin) {
            super("eclear", "Vider la selection (air)");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Vidage..."));
            return plugin.getBlockOperations().fill(player, "air")
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    // === Murs/Sol/Plafond ===

    public static class WallsCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public WallsCommand(EditPlugin plugin) {
            super("ewalls", "Creer les murs (patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Creation des murs..."));
            return plugin.getBlockOperations().walls(player, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class FloorCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public FloorCommand(EditPlugin plugin) {
            super("efloor", "Creer le sol (patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Creation du sol..."));
            return plugin.getBlockOperations().floor(player, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class CeilingCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public CeilingCommand(EditPlugin plugin) {
            super("eceiling", "Creer le plafond (patterns %)");
            addAliases("eceil");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Creation du plafond..."));
            return plugin.getBlockOperations().ceiling(player, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class OutlineCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public OutlineCommand(EditPlugin plugin) {
            super("eoutline", "Creer le contour (patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Creation du contour..."));
            return plugin.getBlockOperations().outline(player, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    // === Clipboard ===

    public static class CopyCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public CopyCommand(EditPlugin plugin) {
            super("ecopy", "Copier la selection");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Copie..."));
            return plugin.getClipboardOperations().copy(player)
                    .thenCompose(result -> {
                        sendResult(ctx, result);
                        if (result.success()) {
                            // Afficher le HUD Edit
                            plugin.getEditHudManager().showHud(player);
                            // Lancer la preview persistante automatiquement apres copie
                            return plugin.getPreviewManager().startPersistentPreview(player)
                                    .thenAccept(previewResult -> {
                                        if (previewResult.success()) {
                                            ctx.sendMessage(ColorUtil.parse("&7Preview active - /epaste pour coller, /estop pour annuler"));
                                        }
                                    })
                                    .thenApply(v -> null);
                        }
                        return CompletableFuture.completedFuture(null);
                    });
        }
    }

    public static class CutCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public CutCommand(EditPlugin plugin) {
            super("ecut", "Couper la selection");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Coupe..."));
            return plugin.getClipboardOperations().copy(player)
                    .thenCompose(copyResult -> {
                        if (!copyResult.success()) {
                            sendResult(ctx, copyResult);
                            return CompletableFuture.completedFuture((Void) null);
                        }
                        int copied = copyResult.blocksAffected();
                        return plugin.getBlockOperations().fill(player, "air")
                                .thenCompose(clearResult -> {
                                    if (clearResult.success()) {
                                        ctx.sendMessage(ColorUtil.parse("&aCoupe: " + copied + " blocs"));
                                        // Afficher le HUD Edit
                                        plugin.getEditHudManager().showHud(player);
                                    } else {
                                        sendResult(ctx, clearResult);
                                    }
                                    return CompletableFuture.completedFuture((Void) null);
                                });
                    });
        }
    }

    public static class PasteCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<Boolean> skipAirArg;

        public PasteCommand(EditPlugin plugin) {
            super("epaste", "Coller le clipboard (--a true pour ignorer l'air)");
            this.plugin = plugin;
            skipAirArg = withOptionalArg("a", "Ignorer l'air", ArgTypes.BOOLEAN);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getClipboardOperations().hasClipboard(player)) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide"));
                return CompletableFuture.completedFuture(null);
            }

            // Recuperer la position figee avant de stopper la preview
            int[] frozenPos = plugin.getPreviewManager().getFrozenPosition(player);

            // Arreter la preview et masquer le HUD
            if (plugin.getPreviewManager().hasActivePreview(player)) {
                plugin.getPreviewManager().stopPreview(player);
            }
            plugin.getEditHudManager().hideHud(player);

            // /epaste --a true  -> skip air
            boolean skipAir = Boolean.TRUE.equals(ctx.get(skipAirArg));
            ctx.sendMessage(ColorUtil.parse("&7Collage" + (skipAir ? " (sans air)" : "") + "..."));

            // Utiliser la position figee si disponible, sinon position actuelle
            return plugin.getClipboardOperations().paste(player, skipAir, frozenPos)
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class RotateCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<Integer> degreesArg;

        public RotateCommand(EditPlugin plugin) {
            super("erotate", "Rotation du clipboard (90, 180, 270)");
            this.plugin = plugin;
            degreesArg = withRequiredArg("degrees", "Angle", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            var result = plugin.getClipboardOperations().rotate(player, ctx.get(degreesArg));
            sendResult(ctx, result);

            // Lancer la preview persistante automatiquement apres rotation
            if (result.success()) {
                return plugin.getPreviewManager().startPersistentPreview(player)
                        .thenAccept(previewResult -> {
                            if (previewResult.success()) {
                                ctx.sendMessage(ColorUtil.parse("&7Preview active - /epaste pour coller, /epreview stop pour annuler"));
                            }
                        })
                        .thenApply(v -> null);
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    public static class FlipCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<String> axisArg;

        public FlipCommand(EditPlugin plugin) {
            super("eflip", "Miroir du clipboard (direction regard ou x/y/z) + preview");
            this.plugin = plugin;
            axisArg = withOptionalArg("axis", "Axe (x, y, z) - direction regard si vide", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            String axis = ctx.get(axisArg);
            BlockOperations.OperationResult result;

            // Si axe spécifié, l'utiliser directement
            if (axis != null && !axis.isEmpty()) {
                String axisLower = axis.toLowerCase();

                // Axes standards
                if (!axisLower.equals("x") && !axisLower.equals("y") && !axisLower.equals("z")) {
                    ctx.sendMessage(ColorUtil.parse("&cAxe invalide! Utiliser: x, y ou z"));
                    return CompletableFuture.completedFuture(null);
                }
                result = plugin.getClipboardOperations().flip(player, axisLower);
            } else {
                // Sans paramètre = flip basé sur la direction du regard
                result = plugin.getClipboardOperations().flipByLookDirection(player);
            }

            sendResult(ctx, result);

            // Lancer la preview persistante automatiquement si le flip a réussi
            if (result.success()) {
                return plugin.getPreviewManager().startPersistentPreview(player)
                        .thenAccept(previewResult -> {
                            if (previewResult.success()) {
                                ctx.sendMessage(ColorUtil.parse("&7Preview active - /epaste pour coller, /epreview stop pour annuler"));
                            }
                        })
                        .thenApply(v -> null);
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    // === Undo/Redo ===

    public static class UndoCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public UndoCommand(EditPlugin plugin) {
            super("eundo", "Annuler la derniere action");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            ctx.sendMessage(ColorUtil.parse("&7Annulation..."));
            return plugin.getBlockOperations().undo(player)
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class RedoCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public RedoCommand(EditPlugin plugin) {
            super("eredo", "Refaire l'action annulee");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            ctx.sendMessage(ColorUtil.parse("&7Refaire..."));
            return plugin.getBlockOperations().redo(player)
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    // === Formes ===

    public static class SphereCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public SphereCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "ehsphere" : "esphere", (hollow ? "Sphere creuse" : "Sphere pleine"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            if (radius < 1 || radius > 100) {
                ctx.sendMessage(ColorUtil.parse("&cRayon invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            Shape shape = new SphereShape(radius, hollow);
            return placeShape(ctx, player, shape, ctx.get(blockArg), plugin);
        }
    }

    public static class CylCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<Integer> heightArg;

        public CylCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "ehcyl" : "ecyl", (hollow ? "Cylindre creux" : "Cylindre plein"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
            heightArg = withRequiredArg("height", "Hauteur (1-256)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            int height = ctx.get(heightArg);

            if (radius < 1 || radius > 100 || height < 1 || height > 256) {
                ctx.sendMessage(ColorUtil.parse("&cValeurs invalides (rayon: 1-100, hauteur: 1-256)"));
                return CompletableFuture.completedFuture(null);
            }

            Shape shape = new CylinderShape(radius, height, hollow);
            return placeShape(ctx, player, shape, ctx.get(blockArg), plugin);
        }
    }

    public static class PyramidCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> sizeArg;

        public PyramidCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "ehpyramid" : "epyramid", (hollow ? "Pyramide creuse" : "Pyramide pleine"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            sizeArg = withRequiredArg("size", "Taille (1-100)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int size = ctx.get(sizeArg);
            if (size < 1 || size > 100) {
                ctx.sendMessage(ColorUtil.parse("&cTaille invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            Shape shape = new PyramidShape(size, hollow);
            return placeShape(ctx, player, shape, ctx.get(blockArg), plugin);
        }
    }

    public static class ConeCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<Integer> heightArg;

        public ConeCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "ehcone" : "econe", (hollow ? "Cone creux" : "Cone plein"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
            heightArg = withRequiredArg("height", "Hauteur (1-256)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            int height = ctx.get(heightArg);

            if (radius < 1 || radius > 100 || height < 1 || height > 256) {
                ctx.sendMessage(ColorUtil.parse("&cValeurs invalides (rayon: 1-100, hauteur: 1-256)"));
                return CompletableFuture.completedFuture(null);
            }

            Shape shape = new ConeShape(radius, height, hollow);
            return placeShape(ctx, player, shape, ctx.get(blockArg), plugin);
        }
    }

    public static class DomeCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public DomeCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "ehdome" : "edome", (hollow ? "Dome creux" : "Dome plein"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            if (radius < 1 || radius > 100) {
                ctx.sendMessage(ColorUtil.parse("&cRayon invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            Shape shape = new DomeShape(radius, hollow);
            return placeShape(ctx, player, shape, ctx.get(blockArg), plugin);
        }
    }

    // === Nouvelles commandes ===

    public static class FillCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public FillCommand(EditPlugin plugin) {
            super("efill", "Remplir l'air autour du joueur au niveau des pieds");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            if (radius < 1 || radius > 100) {
                ctx.sendMessage(ColorUtil.parse("&cRayon invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int centerX = (int) pos.getX();
            int yLevel = (int) pos.getY();
            int centerZ = (int) pos.getZ();

            ctx.sendMessage(ColorUtil.parse("&7Remplissage air (rayon " + radius + ", Y=" + yLevel + ")..."));
            return plugin.getBlockOperations().fillAirAroundPlayer(player, resolved.pattern().toString(), centerX, yLevel, centerZ, radius)
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class FillAirCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public FillAirCommand(EditPlugin plugin) {
            super("efillr", "Remplir l'air sous les pieds (sans traverser les murs)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern", ArgTypes.STRING);
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            if (radius < 1 || radius > 100) {
                ctx.sendMessage(ColorUtil.parse("&cRayon invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(blockArg));
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int centerX = (int) pos.getX();
            int yStart = (int) pos.getY();
            int centerZ = (int) pos.getZ();

            ctx.sendMessage(ColorUtil.parse("&7Remplissage air sous les pieds (rayon " + radius + ")..."));
            return plugin.getBlockOperations().fillAirBelowPlayer(player, resolved.pattern().toString(), centerX, yStart, centerZ, radius)
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ReplaceNearCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<String> fromArg;
        private final RequiredArg<String> toArg;

        public ReplaceNearCommand(EditPlugin plugin) {
            super("ereplacenear", "Remplacer dans un rayon (to: patterns %)");
            this.plugin = plugin;
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
            fromArg = withRequiredArg("from", "Bloc source", ArgTypes.STRING);
            toArg = withRequiredArg("to", "Pattern destination", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            int radius = ctx.get(radiusArg);
            if (radius < 1 || radius > 100) {
                ctx.sendMessage(ColorUtil.parse("&cRayon invalide (1-100)"));
                return CompletableFuture.completedFuture(null);
            }

            var resolvedFrom = plugin.getBlockOperations().resolveBlockTypeWithError(player, ctx.get(fromArg));
            if (!resolvedFrom.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c[from] " + resolvedFrom.error()));
                return CompletableFuture.completedFuture(null);
            }

            var resolvedTo = plugin.getBlockOperations().resolvePatternWithError(player, ctx.get(toArg));
            if (!resolvedTo.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c[to] " + resolvedTo.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Remplacement (rayon " + radius + ")..."));
            return plugin.getBlockOperations().replaceNear(player, radius, resolvedFrom.blockType(), resolvedTo.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    // === Info ===

    public static class SizeCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public SizeCommand(EditPlugin plugin) {
            super("esize", "Taille de la selection");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            long volume = plugin.getSelectionManager().getVolume(player);
            int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);

            if (bounds != null) {
                int width = bounds[3] - bounds[0] + 1;
                int height = bounds[4] - bounds[1] + 1;
                int depth = bounds[5] - bounds[2] + 1;
                ctx.sendMessage(ColorUtil.parse("&aSelection: &f" + width + " x " + height + " x " + depth));
                ctx.sendMessage(ColorUtil.parse("&aVolume: &f" + volume + " blocs"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // === Preview ===

    public static class PreviewCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<String> modeArg;

        public PreviewCommand(EditPlugin plugin) {
            super("epreview", "Preview du clipboard (start=persistant, stop=arreter, vide=5sec)");
            this.plugin = plugin;
            modeArg = withOptionalArg("mode", "start/stop (vide = preview 5 secondes)", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            String mode = ctx.get(modeArg);

            // Mode stop: arreter la preview
            if (mode != null && mode.equalsIgnoreCase("stop")) {
                if (plugin.getPreviewManager().hasActivePreview(player)) {
                    plugin.getPreviewManager().stopPreview(player);
                    ctx.sendMessage(ColorUtil.parse("&aPreview arretee"));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&cAucune preview active"));
                }
                return CompletableFuture.completedFuture(null);
            }

            if (!plugin.getClipboardOperations().hasClipboard(player)) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide"));
                return CompletableFuture.completedFuture(null);
            }

            // Mode start: preview persistante
            if (mode != null && mode.equalsIgnoreCase("start")) {
                ctx.sendMessage(ColorUtil.parse("&7Demarrage de la preview persistante..."));
                return plugin.getPreviewManager().startPersistentPreview(player)
                        .thenAccept(result -> {
                            if (result.success()) {
                                ctx.sendMessage(ColorUtil.parse("&a" + result.message() + " &7(" + result.blockCount() + " blocs)"));
                            } else {
                                ctx.sendMessage(ColorUtil.parse("&c" + result.message()));
                            }
                        })
                        .thenApply(v -> null);
            }

            // Mode par defaut: preview temporaire 5 secondes
            if (plugin.getPreviewManager().hasActivePreview(player)) {
                ctx.sendMessage(ColorUtil.parse("&cPreview deja active - /epreview stop pour arreter"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Affichage de la preview..."));
            return plugin.getPreviewManager().showPreview(player)
                    .thenAccept(result -> {
                        if (result.success()) {
                            ctx.sendMessage(ColorUtil.parse("&a" + result.message() + " &7(" + result.blockCount() + " blocs)"));
                            ctx.sendMessage(ColorUtil.parse("&7Les blocs de verre disparaitront automatiquement."));
                        } else {
                            ctx.sendMessage(ColorUtil.parse("&c" + result.message()));
                        }
                    })
                    .thenApply(v -> null);
        }
    }

    public static class PreviewStopCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public PreviewStopCommand(EditPlugin plugin) {
            super("epreviewstop", "Arreter la preview du clipboard");
            addAliases("estop");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (plugin.getPreviewManager().hasActivePreview(player)) {
                plugin.getPreviewManager().stopPreview(player);
                plugin.getEditHudManager().hideHud(player);
                ctx.sendMessage(ColorUtil.parse("&aPreview arretee"));
            } else {
                ctx.sendMessage(ColorUtil.parse("&cAucune preview active"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class PreviewInfoCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public PreviewInfoCommand(EditPlugin plugin) {
            super("epreviewinfo", "Afficher les infos du clipboard (texte)");
            addAliases("einfo");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getClipboardOperations().hasClipboard(player)) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide"));
                return CompletableFuture.completedFuture(null);
            }

            // Obtenir le clipboard
            ClipboardData clipboard = plugin.getClipboardOperations().getClipboard(player);
            if (clipboard == null || clipboard.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide"));
                return CompletableFuture.completedFuture(null);
            }

            // Position du joueur
            var transform = player.getTransformComponent();
            var playerPos = transform.getPosition();
            int playerX = (int) playerPos.getX();
            int playerY = (int) playerPos.getY();
            int playerZ = (int) playerPos.getZ();

            // Calculer la zone de paste
            int minX = playerX + clipboard.getOffsetX();
            int minY = playerY + clipboard.getOffsetY();
            int minZ = playerZ + clipboard.getOffsetZ();
            int maxX = minX + clipboard.getWidth() - 1;
            int maxY = minY + clipboard.getHeight() - 1;
            int maxZ = minZ + clipboard.getDepth() - 1;

            // Compter les blocs non-air
            int solidBlocks = 0;
            for (String blockType : clipboard.getBlocks().values()) {
                if (!"air".equalsIgnoreCase(blockType)) {
                    solidBlocks++;
                }
            }

            // Afficher les infos
            ctx.sendMessage(ColorUtil.parse("&6=== Info Clipboard ==="));
            ctx.sendMessage(ColorUtil.parse("&eDimensions: &f" + clipboard.getWidth() + " x " + clipboard.getHeight() + " x " + clipboard.getDepth()));
            ctx.sendMessage(ColorUtil.parse("&eBlocs: &f" + solidBlocks + " solides / " + clipboard.getBlockCount() + " total"));
            ctx.sendMessage(ColorUtil.parse("&eOffset: &f" + clipboard.getOffsetX() + ", " + clipboard.getOffsetY() + ", " + clipboard.getOffsetZ()));
            ctx.sendMessage(ColorUtil.parse("&eAxe dominant: &f" + clipboard.getDominantAxis().toUpperCase() + " (pour /eflip auto)"));
            ctx.sendMessage(ColorUtil.parse(""));
            ctx.sendMessage(ColorUtil.parse("&aSi vous collez maintenant:"));
            ctx.sendMessage(ColorUtil.parse("&7Position: &f" + playerX + ", " + playerY + ", " + playerZ));
            ctx.sendMessage(ColorUtil.parse("&7Zone: &f(" + minX + ", " + minY + ", " + minZ + ") -> (" + maxX + ", " + maxY + ", " + maxZ + ")"));

            return CompletableFuture.completedFuture(null);
        }
    }

    // === Debug ===

    public static class DebugDirCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public DebugDirCommand(EditPlugin plugin) {
            super("edir", "Affiche la direction du regard (N/S/E/W)");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            var transform = player.getTransformComponent();
            if (transform == null) {
                ctx.sendMessage(ColorUtil.parse("&cPosition introuvable"));
                return CompletableFuture.completedFuture(null);
            }

            var rotation = transform.getRotation();
            float rotX = rotation.getX();  // Pitch
            float rotY = rotation.getY();  // Yaw (radians)

            double yawDeg = Math.toDegrees(rotY);
            double pitchDeg = Math.toDegrees(rotX);

            // Normaliser entre 0 et 360
            while (yawDeg < 0) yawDeg += 360;
            while (yawDeg >= 360) yawDeg -= 360;

            // Déterminer la direction cardinale
            String direction;
            String axis;

            // Hytale: N≈326°, E≈50° ou 253°, S≈146°, W≈227° ou 50°?
            // D'après les tests: E=253°, donc E est autour de 230-280°
            // Recalcul: N≈326°, S≈146°, W≈50°, E≈230°
            if (pitchDeg < -60) {
                direction = "UP (Haut)";
                axis = "Y";
            } else if (pitchDeg > 60) {
                direction = "DOWN (Bas)";
                axis = "Y";
            } else if (yawDeg >= 280 || yawDeg < 10) {
                direction = "NORTH (Nord)";
                axis = "Z";
            } else if (yawDeg >= 10 && yawDeg < 100) {
                direction = "WEST (Ouest)";
                axis = "X";
            } else if (yawDeg >= 100 && yawDeg < 190) {
                direction = "SOUTH (Sud)";
                axis = "Z";
            } else {
                direction = "EAST (Est)";
                axis = "X";
            }

            ctx.sendMessage(ColorUtil.parse("&6=== Direction du regard ==="));
            ctx.sendMessage(ColorUtil.parse("&eDirection: &f" + direction));
            ctx.sendMessage(ColorUtil.parse("&eAxe de flip: &f" + axis));
            ctx.sendMessage(ColorUtil.parse("&7Yaw: &f" + String.format("%.1f", yawDeg) + "°"));
            ctx.sendMessage(ColorUtil.parse("&7Pitch: &f" + String.format("%.1f", pitchDeg) + "°"));

            return CompletableFuture.completedFuture(null);
        }
    }

    // === Debug rotation ===

    /**
     * Commande /erotdebug - Reverse-engineering des indices de rotation Hytale.
     *
     * Usage:
     *   /erotdebug x y z     - Affiche la rotation du bloc aux coordonnées
     *   /erotdebug            - Scanne une zone 3x3x3 autour du joueur
     *   /erotdebug scan <r>   - Scanne un rayon r autour du joueur
     *
     * Affiche: blockType, rotationIndex (0-63), décomposition yaw/pitch/roll
     */
    public static class RotDebugCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<Integer> xArg;
        private final OptionalArg<Integer> yArg;
        private final OptionalArg<Integer> zArg;

        public RotDebugCommand(EditPlugin plugin) {
            super("erotdebug", "Debug rotation index d'un bloc (reverse-engineering)");
            addAliases("erot");
            this.plugin = plugin;
            xArg = withOptionalArg("x", "Coord X (ou 'scan')", ArgTypes.INTEGER);
            yArg = withOptionalArg("y", "Coord Y (ou rayon pour scan)", ArgTypes.INTEGER);
            zArg = withOptionalArg("z", "Coord Z", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            var world = player.getWorld();
            if (world == null) {
                ctx.sendMessage(ColorUtil.parse("&cMonde introuvable"));
                return CompletableFuture.completedFuture(null);
            }

            Integer xVal = ctx.get(xArg);
            Integer yVal = ctx.get(yArg);
            Integer zVal = ctx.get(zArg);

            // Si les 3 coordonnées sont fournies, debug ce bloc précis
            if (xVal != null && yVal != null && zVal != null) {
                debugSingleBlock(ctx, world, xVal, yVal, zVal);
                return CompletableFuture.completedFuture(null);
            }

            // Sinon, scan autour du joueur
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int px = (int) Math.floor(pos.getX());
            int py = (int) Math.floor(pos.getY());
            int pz = (int) Math.floor(pos.getZ());

            int radius = (xVal != null) ? xVal : 2;  // rayon par défaut 2

            ctx.sendMessage(ColorUtil.parse("&6=== RotDebug Scan (rayon " + radius + ") ==="));
            ctx.sendMessage(ColorUtil.parse("&7Centre: &f" + px + ", " + py + ", " + pz));
            ctx.sendMessage(ColorUtil.parse("&7Format: &f[x,y,z] type rot=INDEX (yaw=Y pitch=P roll=R)"));
            ctx.sendMessage(ColorUtil.parse(""));

            int found = 0;
            for (int x = px - radius; x <= px + radius; x++) {
                for (int y = py - radius; y <= py + radius; y++) {
                    for (int z = pz - radius; z <= pz + radius; z++) {
                        try {
                            var bt = world.getBlockType(x, y, z);
                            if (bt == null || bt == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                                    || "air".equalsIgnoreCase(bt.getId())) {
                                continue;
                            }

                            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
                            var chunk = world.getChunkIfLoaded(chunkIndex);
                            int rotation = 0;
                            if (chunk != null) {
                                rotation = chunk.getRotationIndex(x, y, z);
                            }

                            // Seulement afficher les blocs avec rotation != 0
                            // OU tous si c'est un scan petit rayon
                            if (rotation != 0 || radius <= 2) {
                                int yaw = rotation % 4;
                                int pitch = (rotation / 4) % 4;
                                int roll = (rotation / 16) % 4;

                                String color = rotation != 0 ? "&e" : "&7";
                                ctx.sendMessage(ColorUtil.parse(color + "[" + x + "," + y + "," + z + "] &f"
                                        + bt.getId() + " &arot=" + rotation
                                        + " &7(yaw=" + yaw + " pitch=" + pitch + " roll=" + roll + ")"));
                                found++;
                            }

                            if (found >= 50) {
                                ctx.sendMessage(ColorUtil.parse("&c... (limite 50 blocs atteinte)"));
                                return CompletableFuture.completedFuture(null);
                            }
                        } catch (Exception e) {
                            // Skip silently
                        }
                    }
                }
            }

            if (found == 0) {
                ctx.sendMessage(ColorUtil.parse("&7Aucun bloc avec rotation trouve dans la zone"));
            } else {
                ctx.sendMessage(ColorUtil.parse(""));
                ctx.sendMessage(ColorUtil.parse("&6Total: &f" + found + " blocs"));
            }

            return CompletableFuture.completedFuture(null);
        }

        private void debugSingleBlock(CommandContext ctx, com.hypixel.hytale.server.core.universe.world.World world,
                                       int x, int y, int z) {
            try {
                var bt = world.getBlockType(x, y, z);
                if (bt == null || bt == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
                        || "air".equalsIgnoreCase(bt.getId())) {
                    ctx.sendMessage(ColorUtil.parse("&7Bloc a (" + x + ", " + y + ", " + z + "): &fair (pas de rotation)"));
                    return;
                }

                long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
                var chunk = world.getChunkIfLoaded(chunkIndex);
                int rotation = 0;
                int blockId = 0;
                if (chunk != null) {
                    rotation = chunk.getRotationIndex(x, y, z);
                    blockId = chunk.getBlock(x, y, z);
                }

                int yaw = rotation % 4;
                int pitch = (rotation / 4) % 4;
                int roll = (rotation / 16) % 4;

                ctx.sendMessage(ColorUtil.parse("&6=== RotDebug ==="));
                ctx.sendMessage(ColorUtil.parse("&ePosition: &f" + x + ", " + y + ", " + z));
                ctx.sendMessage(ColorUtil.parse("&eBlock: &f" + bt.getId() + " &7(id=" + blockId + ")"));
                ctx.sendMessage(ColorUtil.parse("&eRotation Index: &a" + rotation + " &7/ 63"));
                ctx.sendMessage(ColorUtil.parse(""));
                ctx.sendMessage(ColorUtil.parse("&eDecomposition (hypothese yaw+pitch*4+roll*16):"));
                ctx.sendMessage(ColorUtil.parse("  &eYaw:   &f" + yaw + " &7(0=0d, 1=90d, 2=180d, 3=270d)"));
                ctx.sendMessage(ColorUtil.parse("  &ePitch: &f" + pitch + " &7(0=0d, 1=90d, 2=180d, 3=270d)"));
                ctx.sendMessage(ColorUtil.parse("  &eRoll:  &f" + roll + " &7(0=0d, 1=90d, 2=180d, 3=270d)"));
                ctx.sendMessage(ColorUtil.parse(""));
                ctx.sendMessage(ColorUtil.parse("&7Binaire: &f" + String.format("%6s", Integer.toBinaryString(rotation)).replace(' ', '0')
                        + " &7(roll[5:4] pitch[3:2] yaw[1:0])"));
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            }
        }
    }

    // === Freeze ===

    public static class FreezeCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public FreezeCommand(EditPlugin plugin) {
            super("efig", "Figer/defiger la position de la preview");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            // Toggle: si deja fige -> defiger
            if (plugin.getPreviewManager().isFrozen(player)) {
                plugin.getPreviewManager().unfreeze(player);
                ctx.sendMessage(ColorUtil.parse("&aPreview defigee! &7(suit votre position)"));
                return CompletableFuture.completedFuture(null);
            }

            // Sinon -> figer a la position actuelle
            if (!plugin.getClipboardOperations().hasClipboard(player)) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide"));
                return CompletableFuture.completedFuture(null);
            }

            // Lancer la preview persistante si pas active
            if (!plugin.getPreviewManager().hasActivePreview(player)) {
                plugin.getPreviewManager().startPersistentPreview(player);
            }

            plugin.getPreviewManager().freezeAt(player);
            ctx.sendMessage(ColorUtil.parse("&aPreview figee! &7(cubes verts = position fixe)"));
            ctx.sendMessage(ColorUtil.parse("&7/efig pour defiger, /epaste pour coller ici."));
            return CompletableFuture.completedFuture(null);
        }
    }

    // === Reset ===

    public static class NoneCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public NoneCommand(EditPlugin plugin) {
            super("enone", "Annuler tout (clipboard, preview, rotation, flip)");
            addAliases("eclear-clipboard");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            // Arreter la preview (defige automatiquement)
            if (plugin.getPreviewManager().hasActivePreview(player)) {
                plugin.getPreviewManager().stopPreview(player);
            }

            // Masquer le HUD
            plugin.getEditHudManager().hideHud(player);

            // Effacer le clipboard
            plugin.getClipboardOperations().clearClipboard(player);

            ctx.sendMessage(ColorUtil.parse("&aClipboard efface, preview arretee. Remis a zero."));
            return CompletableFuture.completedFuture(null);
        }
    }

    // === Admin ===

    public static class ReloadCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public ReloadCommand(EditPlugin plugin) {
            super("ereload", "Recharger la config (rotation-overrides.json)");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);

            try {
                RotationOverrides overrides = RotationOverrides.get();
                if (overrides != null) {
                    overrides.reload(java.nio.file.Path.of("mods"));
                    ctx.sendMessage(ColorUtil.parse("&aRotation overrides recharges!"));
                    ctx.sendMessage(ColorUtil.parse("&7Native flip (API Hytale): "
                            + (overrides.isUseNativeFlip() ? "&aACTIF" : "&cDESACTIVE (overrides manuels)")));
                } else {
                    RotationOverrides.init(java.nio.file.Path.of("mods"));
                    ctx.sendMessage(ColorUtil.parse("&aRotation overrides initialises!"));
                }
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur reload: " + e.getMessage()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /edebug <pattern1,pattern2,...> - Filtre les logs pour n'afficher que certains blocs
     * /edebug clear - Supprime le filtre (tous les blocs)
     * /edebug - Affiche les filtres actifs
     */
    public static class DebugFilterCommand extends AbstractCommand {
        private final OptionalArg<String> patternsArg;

        public DebugFilterCommand() {
            super("edebug", "Filtrer les logs debug par type de bloc");
            patternsArg = withOptionalArg("patterns", "Patterns (virgules) ou 'clear'", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);

            DebugLogger dbg = DebugLogger.get();
            if (dbg == null) {
                ctx.sendMessage(ColorUtil.parse("&cDebug logger non initialise!"));
                return CompletableFuture.completedFuture(null);
            }

            String input = ctx.get(patternsArg);

            if (input == null || input.isEmpty()) {
                // Afficher les filtres actifs
                Set<String> filters = dbg.getBlockFilters();
                if (filters.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&7Filtre debug: &fdesactive &7(tous les blocs)"));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&7Filtre debug actif: &e" + String.join("&7, &e", filters)));
                }
            } else if ("clear".equalsIgnoreCase(input.trim())) {
                dbg.clearBlockFilters();
                ctx.sendMessage(ColorUtil.parse("&aFiltre debug supprime (tous les blocs seront logges)"));
            } else {
                // Parser les patterns séparés par virgules
                String[] parts = input.split(",");
                Set<String> patterns = new LinkedHashSet<>();
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) patterns.add(trimmed);
                }
                dbg.setBlockFilters(patterns);
                ctx.sendMessage(ColorUtil.parse("&aFiltre debug: &e" + String.join("&7, &e", patterns)));
                ctx.sendMessage(ColorUtil.parse("&7Seuls les blocs contenant ces patterns seront logges."));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    // === Block Info ===

    /**
     * /eblockinfo [blockId] - Inspecter les proprietes d'un bloc.
     * Sans argument: inspecte le bloc vise par le joueur.
     * Avec argument: inspecte le bloc par son ID.
     */
    public static class BlockInfoCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<String> blockIdArg;

        public BlockInfoCommand(EditPlugin plugin) {
            super("eblockinfo", "Inspecter les proprietes d'un bloc (taille, flip, hitbox)");
            this.plugin = plugin;
            blockIdArg = withOptionalArg("block", "ID du bloc (ou vide = bloc vise)", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            String blockId = ctx.get(blockIdArg);

            if (blockId != null && !blockId.isEmpty()) {
                // Inspecter par ID
                showBlockInfo(ctx, blockId, -1);
            } else {
                // Inspecter le bloc vise via la selection pos1
                int[] bounds = plugin.getSelectionManager().getSelectionBounds(player);
                if (bounds == null) {
                    ctx.sendMessage(ColorUtil.parse("&cAucune selection. Utilisez /pos1 sur un bloc ou /eblockinfo --block <id>"));
                    return CompletableFuture.completedFuture(null);
                }

                // Utiliser pos1 (premier coin de la selection)
                int bx = bounds[0];
                int by = bounds[1];
                int bz = bounds[2];

                World world;
                try {
                    world = player.getWorld();
                } catch (Exception e) {
                    ctx.sendMessage(ColorUtil.parse("&cMonde introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                if (world == null) {
                    ctx.sendMessage(ColorUtil.parse("&cMonde introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                BlockType bt = world.getBlockType(bx, by, bz);
                if (bt == null || bt == BlockType.EMPTY) {
                    ctx.sendMessage(ColorUtil.parse("&cAucun bloc a la position (" + bx + ", " + by + ", " + bz + ")"));
                    return CompletableFuture.completedFuture(null);
                }

                // Recuperer la rotation
                int rotation = 0;
                try {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
                    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                    if (chunk != null) {
                        rotation = chunk.getRotationIndex(bx, by, bz);
                    }
                } catch (Exception e) {
                    // Ignorer
                }

                ctx.sendMessage(ColorUtil.parse("&7Bloc a la position &f(" + bx + ", " + by + ", " + bz + ")&7:"));
                showBlockInfo(ctx, bt.getId(), rotation);
            }

            return CompletableFuture.completedFuture(null);
        }

        private void showBlockInfo(CommandContext ctx, String blockId, int rotationIndex) {
            if (rotationIndex < 0) rotationIndex = 0;

            BlockSizeHelper.BlockSizeInfo info = BlockSizeHelper.getBlockSize(blockId, rotationIndex);
            if (info == null) {
                ctx.sendMessage(ColorUtil.parse("&cBloc introuvable: " + blockId));
                return;
            }

            ctx.sendMessage(ColorUtil.parse("&6--- Block Info: &e" + blockId + " &6---"));
            ctx.sendMessage(ColorUtil.parse("&7Taille hitbox: &f"
                    + String.format("%.2f", info.width()) + " x "
                    + String.format("%.2f", info.height()) + " x "
                    + String.format("%.2f", info.depth())));
            ctx.sendMessage(ColorUtil.parse("&7Grille (positions): &f"
                    + info.gridWidth() + " x " + info.gridHeight() + " x " + info.gridDepth()
                    + " &7(" + info.totalGridPositions() + " positions)"));
            ctx.sendMessage(ColorUtil.parse("&7Depasse 1x1x1: " + (info.protrudesUnitBox() ? "&aOUI" : "&7Non")));
            ctx.sendMessage(ColorUtil.parse("&7Multi-part: " + (info.isMultiPart() ? "&aOUI" : "&7Non")));
            ctx.sendMessage(ColorUtil.parse("&7Flip type: &f" + (info.flipType() != null ? info.flipType().name() : "null")));
            ctx.sendMessage(ColorUtil.parse("&7State (multi-bloc): " + (info.isState() ? "&aOUI" : "&7Non")));

            if (rotationIndex > 0) {
                int yaw = rotationIndex % 4;
                int pitch = (rotationIndex / 4) % 4;
                int roll = (rotationIndex / 16) % 4;
                ctx.sendMessage(ColorUtil.parse("&7Rotation: &f" + rotationIndex
                        + " &7(yaw=" + yaw + " pitch=" + pitch + " roll=" + roll + ")"));
            }

            // Log dans le fichier debug aussi
            DebugLogger dbg = DebugLogger.get();
            if (dbg != null) {
                dbg.logSection("BLOCK INFO - " + blockId);
                dbg.log("BLOCKINFO", info.toString());
            }
        }
    }

    // === Helper pour les formes ===

    private static CompletableFuture<Void> placeShape(CommandContext ctx, Player player, Shape shape, String blockTypeArg, EditPlugin plugin) {
        try {
            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, blockTypeArg);
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int centerX = (int) pos.getX();
            int centerY = (int) pos.getY();
            int centerZ = (int) pos.getZ();

            ctx.sendMessage(ColorUtil.parse("&7Placement de " + shape.getDescription() + "..."));
            return plugin.getBlockOperations().placeShape(player, shape, centerX, centerY, centerZ, resolved.pattern().toString())
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
        } catch (Exception e) {
            ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
    }
}
