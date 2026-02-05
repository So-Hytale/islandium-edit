package com.islandium.edit.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.edit.operation.ClipboardData;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.operation.BlockOperations;
import com.islandium.edit.shape.*;
import org.jetbrains.annotations.NotNull;

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

        // Preview
        registry.registerCommand(new PreviewCommand(plugin));
        registry.registerCommand(new PreviewInfoCommand(plugin));
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
                    .thenAccept(result -> sendResult(ctx, result))
                    .thenApply(v -> null);
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
                                .thenAccept(clearResult -> {
                                    if (clearResult.success()) {
                                        ctx.sendMessage(ColorUtil.parse("&aCoupe: " + copied + " blocs"));
                                    } else {
                                        sendResult(ctx, clearResult);
                                    }
                                })
                                .thenApply(v -> null);
                    });
        }
    }

    public static class PasteCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<Boolean> skipAirArg;

        public PasteCommand(EditPlugin plugin) {
            super("epaste", "Coller le clipboard (-a pour ignorer l'air)");
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

            // Arreter la preview si active
            if (plugin.getPreviewManager().hasActivePreview(player)) {
                plugin.getPreviewManager().stopPreview(player);
            }

            boolean skipAir = Boolean.TRUE.equals(ctx.get(skipAirArg));
            ctx.sendMessage(ColorUtil.parse("&7Collage" + (skipAir ? " (sans air)" : "") + "..."));
            return plugin.getClipboardOperations().paste(player, skipAir)
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

            int estimate = shape.estimateBlockCount();
            if (estimate > EditPlugin.MAX_BLOCKS_PER_OPERATION) {
                ctx.sendMessage(ColorUtil.parse("&cForme trop grande: ~" + estimate + " blocs (max: " + EditPlugin.MAX_BLOCKS_PER_OPERATION + ")"));
                return CompletableFuture.completedFuture(null);
            }

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
