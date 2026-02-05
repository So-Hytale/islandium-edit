package com.islandium.edit.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.operation.BlockOperations;
import com.islandium.edit.shape.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Commande principale /edit avec sous-commandes.
 * Usage: /edit <subcommand> [args...]
 * Exemple: /edit cyl Rock_Stone 2 2
 */
public class EditCommand extends AbstractCommand {

    private final EditPlugin plugin;

    public EditCommand(@NotNull EditPlugin plugin) {
        super("edit", "Commandes d'edition de terrain (WorldEdit)");
        this.plugin = plugin;

        addAliases("we", "worldedit");

        // Ajouter toutes les sous-commandes
        addSubCommand(new WandSubCommand(plugin));
        addSubCommand(new Pos1SubCommand(plugin));
        addSubCommand(new Pos2SubCommand(plugin));
        addSubCommand(new SetSubCommand(plugin));
        addSubCommand(new ReplaceSubCommand(plugin));
        addSubCommand(new WallsSubCommand(plugin));
        addSubCommand(new FloorSubCommand(plugin));
        addSubCommand(new CeilingSubCommand(plugin));
        addSubCommand(new OutlineSubCommand(plugin));
        addSubCommand(new ClearSubCommand(plugin));
        addSubCommand(new CopySubCommand(plugin));
        addSubCommand(new CutSubCommand(plugin));
        addSubCommand(new PasteSubCommand(plugin));
        addSubCommand(new RotateSubCommand(plugin));
        addSubCommand(new FlipSubCommand(plugin));
        addSubCommand(new UndoSubCommand(plugin));
        addSubCommand(new RedoSubCommand(plugin));
        addSubCommand(new SphereSubCommand(plugin, false));
        addSubCommand(new SphereSubCommand(plugin, true));
        addSubCommand(new CylinderSubCommand(plugin, false));
        addSubCommand(new CylinderSubCommand(plugin, true));
        addSubCommand(new PyramidSubCommand(plugin, false));
        addSubCommand(new PyramidSubCommand(plugin, true));
        addSubCommand(new ConeSubCommand(plugin, false));
        addSubCommand(new ConeSubCommand(plugin, true));
        addSubCommand(new DomeSubCommand(plugin, false));
        addSubCommand(new DomeSubCommand(plugin, true));
        addSubCommand(new SizeSubCommand(plugin));
        addSubCommand(new FillSubCommand(plugin));
        addSubCommand(new FillAirSubCommand(plugin));
        addSubCommand(new ReplaceNearSubCommand(plugin));
        addSubCommand(new HelpSubCommand());
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        // Afficher l'aide si aucune sous-commande
        return showHelp(ctx);
    }

    private CompletableFuture<Void> showHelp(CommandContext ctx) {
        List<String> help = Arrays.asList(
                "&6=== IslandiumEdit - Aide ===",
                "",
                "&e/edit wand &7- Obtenir la wand de selection",
                "&e/edit pos1 &7- Definir pos1 a votre position",
                "&e/edit pos2 &7- Definir pos2 a votre position",
                "",
                "&e/edit set <block> &7- Remplir la selection",
                "&e/edit fill <block> &7- Remplir l'air au niveau des pieds",
                "&e/edit fillair <block> <depth> &7- Remplir l'air sur profondeur",
                "&e/edit replace <from> <to> &7- Remplacer des blocs",
                "&e/edit replacenear <r> <from> <to> &7- Remplacer autour de soi",
                "&e/edit walls <block> &7- Creer les murs",
                "&e/edit floor <block> &7- Creer le sol",
                "&e/edit ceiling <block> &7- Creer le plafond",
                "&e/edit outline <block> &7- Creer le contour",
                "&e/edit clear &7- Vider la selection (air)",
                "",
                "&e/edit copy &7- Copier la selection",
                "&e/edit paste &7- Coller le clipboard",
                "&e/edit rotate <degrees> &7- Rotation du clipboard",
                "&e/edit flip <x|y|z> &7- Miroir du clipboard",
                "",
                "&e/edit undo &7- Annuler la derniere action",
                "&e/edit redo &7- Refaire l'action annulee",
                "",
                "&e/edit sphere <block> <r> &7- Sphere pleine",
                "&e/edit hsphere <block> <r> &7- Sphere creuse",
                "&e/edit cyl <block> <r> <h> &7- Cylindre plein",
                "&e/edit hcyl <block> <r> <h> &7- Cylindre creux",
                "&e/edit pyramid <block> <size> &7- Pyramide pleine",
                "&e/edit hpyramid <block> <size> &7- Pyramide creuse",
                "&e/edit cone <block> <r> <h> &7- Cone plein",
                "&e/edit hcone <block> <r> <h> &7- Cone creux",
                "&e/edit dome <block> <r> &7- Dome plein",
                "&e/edit hdome <block> <r> &7- Dome creux",
                "",
                "&e/edit size &7- Taille de la selection"
        );

        for (String line : help) {
            ctx.sendMessage(ColorUtil.parse(line));
        }

        return CompletableFuture.completedFuture(null);
    }

    // ==================== HELPER METHODS ====================

    static boolean checkEditPermission(Player player, String permission) {
        try {
            var permsModule = PermissionsModule.get();
            if (permsModule.getGroupsForUser(player.getUuid()).contains("OP")) {
                return true;
            }
            return permsModule.hasPermission(player.getUuid(), permission);
        } catch (Exception e) {
            return true; // fail-open pour le dev
        }
    }

    static void sendOperationResult(CommandContext ctx, BlockOperations.OperationResult result) {
        if (result.success()) {
            ctx.sendMessage(ColorUtil.parse("&a" + result.message() + " &7(" + result.blocksAffected() + " blocs)"));
        } else {
            ctx.sendMessage(ColorUtil.parse("&c" + result.message()));
        }
    }

    // ==================== SUB-COMMANDS ====================

    public static class WandSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public WandSubCommand(EditPlugin plugin) {
            super("wand", "Obtenir la wand de selection");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!checkEditPermission(player, "islandium.edit.wand")) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'avez pas la permission"));
                return CompletableFuture.completedFuture(null);
            }

            try {
                var inventory = player.getInventory();
                if (inventory == null) {
                    ctx.sendMessage(ColorUtil.parse("&cErreur: inventaire introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                // Utiliser la hotbar au lieu du storage
                var hotbar = inventory.getHotbar();
                if (hotbar == null) {
                    ctx.sendMessage(ColorUtil.parse("&cErreur: hotbar introuvable"));
                    return CompletableFuture.completedFuture(null);
                }

                // Créer la wand
                ItemStack wand = new ItemStack(EditPlugin.WAND_ITEM_ID, 1);

                // Ajouter à la hotbar
                var transaction = hotbar.addItemStack(wand);

                if (transaction.succeeded()) {
                    ctx.sendMessage(ColorUtil.parse("&aWand ajoutee a votre hotbar!"));
                    ctx.sendMessage(ColorUtil.parse("&7Clic gauche = Pos1, Clic droit = Pos2"));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&cHotbar pleine! Liberez un slot."));
                }
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
                e.printStackTrace();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class Pos1SubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public Pos1SubCommand(EditPlugin plugin) {
            super("pos1", "Definir pos1 a votre position");
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
                    ctx.sendMessage(ColorUtil.parse("&aPosition 1 definie: &f" + vec.getX() + ", " + vec.getY() + ", " + vec.getZ()));
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

    public static class Pos2SubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public Pos2SubCommand(EditPlugin plugin) {
            super("pos2", "Definir pos2 a votre position");
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
                    ctx.sendMessage(ColorUtil.parse("&aPosition 2 definie: &f" + vec.getX() + ", " + vec.getY() + ", " + vec.getZ()));
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

    public static class SetSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public SetSubCommand(EditPlugin plugin) {
            super("set", "Remplir la selection (supporte 20%block1,80%block2)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%grass,80%dirt)", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);
            String blockTypeArg = ctx.get(blockArg);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            // Résoudre le pattern (supporte 20%grass,80%stone)
            var resolved = plugin.getBlockOperations().resolvePatternWithError(player, blockTypeArg);
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(ColorUtil.parse("&7Remplissage avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().fill(player, resolved.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ReplaceSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> fromArg;
        private final RequiredArg<String> toArg;

        public ReplaceSubCommand(EditPlugin plugin) {
            super("replace", "Remplacer des blocs (to: supporte patterns %)");
            this.plugin = plugin;
            fromArg = withRequiredArg("from", "Bloc a remplacer (ou hand/*)", ArgTypes.STRING);
            toArg = withRequiredArg("to", "Nouveau bloc (ou hand/*)", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getSelectionManager().hasValidSelection(player)) {
                ctx.sendMessage(ColorUtil.parse("&cAucune selection definie"));
                return CompletableFuture.completedFuture(null);
            }

            // Résoudre les types de blocs (from = simple, to = pattern)
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
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class WallsSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public WallsSubCommand(EditPlugin plugin) {
            super("walls", "Creer les murs (supporte patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            ctx.sendMessage(ColorUtil.parse("&7Creation des murs avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().walls(player, resolved.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class FloorSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public FloorSubCommand(EditPlugin plugin) {
            super("floor", "Creer le sol (supporte patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            ctx.sendMessage(ColorUtil.parse("&7Creation du sol avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().floor(player, resolved.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class CeilingSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public CeilingSubCommand(EditPlugin plugin) {
            super("ceiling", "Creer le plafond (supporte patterns %)");
            addAliases("ceil");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            ctx.sendMessage(ColorUtil.parse("&7Creation du plafond avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().ceiling(player, resolved.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class OutlineSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public OutlineSubCommand(EditPlugin plugin) {
            super("outline", "Creer le contour (supporte patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            ctx.sendMessage(ColorUtil.parse("&7Creation du contour avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().outline(player, resolved.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ClearSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public ClearSubCommand(EditPlugin plugin) {
            super("clear", "Vider la selection (remplir d'air)");
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

            ctx.sendMessage(ColorUtil.parse("&7Vidage en cours..."));
            return plugin.getBlockOperations().fill(player, "air")
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class CopySubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public CopySubCommand(EditPlugin plugin) {
            super("copy", "Copier la selection dans le clipboard");
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

            ctx.sendMessage(ColorUtil.parse("&7Copie en cours..."));
            return plugin.getClipboardOperations().copy(player)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class CutSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public CutSubCommand(EditPlugin plugin) {
            super("cut", "Couper la selection (copie + vide)");
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

            ctx.sendMessage(ColorUtil.parse("&7Coupe en cours..."));

            // D'abord copier (async), puis vider (async)
            return plugin.getClipboardOperations().copy(player)
                    .thenCompose(copyResult -> {
                        if (!copyResult.success()) {
                            sendOperationResult(ctx, copyResult);
                            return CompletableFuture.completedFuture((Void) null);
                        }

                        int copiedBlocks = copyResult.blocksAffected();

                        // Puis vider la sélection
                        return plugin.getBlockOperations().fill(player, "air")
                                .thenAccept(clearResult -> {
                                    if (clearResult.success()) {
                                        ctx.sendMessage(ColorUtil.parse("&aCoupe terminee: " + copiedBlocks + " blocs"));
                                    } else {
                                        sendOperationResult(ctx, clearResult);
                                    }
                                })
                                .thenApply(v -> null);
                    });
        }
    }

    public static class PasteSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<Boolean> skipAirArg;

        public PasteSubCommand(EditPlugin plugin) {
            super("paste", "Coller le clipboard (-a pour ignorer l'air)");
            this.plugin = plugin;
            skipAirArg = withOptionalArg("a", "Ignorer les blocs d'air", ArgTypes.BOOLEAN);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            if (!plugin.getClipboardOperations().hasClipboard(player)) {
                ctx.sendMessage(ColorUtil.parse("&cClipboard vide. Utilisez /edit copy d'abord."));
                return CompletableFuture.completedFuture(null);
            }

            // Vérifier si -a est présent (ignorer l'air)
            boolean skipAir = Boolean.TRUE.equals(ctx.get(skipAirArg));

            ctx.sendMessage(ColorUtil.parse("&7Collage en cours" + (skipAir ? " (sans air)" : "") + "..."));
            return plugin.getClipboardOperations().paste(player, skipAir)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class RotateSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<Integer> degreesArg;

        public RotateSubCommand(EditPlugin plugin) {
            super("rotate", "Tourner le clipboard (90, 180, 270)");
            this.plugin = plugin;
            degreesArg = withRequiredArg("degrees", "Angle de rotation", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            BlockOperations.OperationResult result = plugin.getClipboardOperations().rotate(player, ctx.get(degreesArg));
            sendOperationResult(ctx, result);
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class FlipSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final OptionalArg<String> axisArg;

        public FlipSubCommand(EditPlugin plugin) {
            super("flip", "Miroir du clipboard (auto ou x/y/z)");
            this.plugin = plugin;
            axisArg = withOptionalArg("axis", "Axe (x, y, z) - auto base sur position de copie", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            String axis = ctx.get(axisArg);

            // Si axe spécifié, l'utiliser directement
            if (axis != null && !axis.isEmpty()) {
                String axisLower = axis.toLowerCase();
                if (!axisLower.equals("x") && !axisLower.equals("y") && !axisLower.equals("z")) {
                    ctx.sendMessage(ColorUtil.parse("&cAxe invalide! Utiliser: x, y ou z"));
                    return CompletableFuture.completedFuture(null);
                }
                BlockOperations.OperationResult result = plugin.getClipboardOperations().flip(player, axisLower);
                sendOperationResult(ctx, result);
                return CompletableFuture.completedFuture(null);
            }

            // Sinon, détecter l'axe automatiquement via la position de copie du clipboard
            BlockOperations.OperationResult result = plugin.getClipboardOperations().flipAuto(player);
            if (result.success()) {
                ctx.sendMessage(ColorUtil.parse("&7Axe auto (position copie): " + result.message().split(" ")[3]));
            }
            sendOperationResult(ctx, result);
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class UndoSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public UndoSubCommand(EditPlugin plugin) {
            super("undo", "Annuler la derniere action");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            ctx.sendMessage(ColorUtil.parse("&7Annulation en cours..."));
            return plugin.getBlockOperations().undo(player)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class RedoSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public RedoSubCommand(EditPlugin plugin) {
            super("redo", "Refaire l'action annulee");
            this.plugin = plugin;
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) return CompletableFuture.completedFuture(null);
            Player player = ctx.senderAs(Player.class);

            ctx.sendMessage(ColorUtil.parse("&7Refaire en cours..."));
            return plugin.getBlockOperations().redo(player)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class SphereSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public SphereSubCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "hsphere" : "sphere", (hollow ? "Sphere creuse" : "Sphere pleine"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Type de bloc (ou hand/*)", ArgTypes.STRING);
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

    public static class CylinderSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<Integer> heightArg;

        public CylinderSubCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "hcyl" : "cyl", (hollow ? "Cylindre creux" : "Cylindre plein"));
            addAliases(hollow ? "hcylinder" : "cylinder");
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Type de bloc (ou hand/*)", ArgTypes.STRING);
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

    public static class PyramidSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> sizeArg;

        public PyramidSubCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "hpyramid" : "pyramid", (hollow ? "Pyramide creuse" : "Pyramide pleine"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Type de bloc (ou hand/*)", ArgTypes.STRING);
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

    public static class ConeSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<Integer> heightArg;

        public ConeSubCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "hcone" : "cone", (hollow ? "Cone creux" : "Cone plein"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Type de bloc (ou hand/*)", ArgTypes.STRING);
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

    public static class DomeSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final boolean hollow;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> radiusArg;

        public DomeSubCommand(EditPlugin plugin, boolean hollow) {
            super(hollow ? "hdome" : "dome", (hollow ? "Dome creux" : "Dome plein"));
            this.plugin = plugin;
            this.hollow = hollow;
            blockArg = withRequiredArg("block", "Type de bloc (ou hand/*)", ArgTypes.STRING);
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

    public static class SizeSubCommand extends AbstractCommand {
        private final EditPlugin plugin;

        public SizeSubCommand(EditPlugin plugin) {
            super("size", "Afficher la taille de la selection");
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

    public static class FillSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;

        public FillSubCommand(EditPlugin plugin) {
            super("fill", "Remplir l'air au niveau des pieds (patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            // Obtenir le niveau Y des pieds du joueur
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int yLevel = (int) pos.getY();

            ctx.sendMessage(ColorUtil.parse("&7Remplissage de l'air a Y=" + yLevel + " avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().fillAirAtLevel(player, resolved.pattern().toString(), yLevel)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class FillAirSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Integer> depthArg;

        public FillAirSubCommand(EditPlugin plugin) {
            super("fillair", "Remplir l'air sur profondeur (patterns %)");
            this.plugin = plugin;
            blockArg = withRequiredArg("block", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
            depthArg = withRequiredArg("depth", "Profondeur (blocs vers le bas)", ArgTypes.INTEGER);
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

            int depth = ctx.get(depthArg);
            if (depth < 1 || depth > 256) {
                ctx.sendMessage(ColorUtil.parse("&cProfondeur invalide (1-256)"));
                return CompletableFuture.completedFuture(null);
            }

            // Obtenir le niveau Y des pieds du joueur
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int yStart = (int) pos.getY();

            ctx.sendMessage(ColorUtil.parse("&7Remplissage de l'air de Y=" + yStart + " a Y=" + (yStart - depth) + " avec " + resolved.pattern() + "..."));
            return plugin.getBlockOperations().fillAirRange(player, resolved.pattern().toString(), yStart, depth)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class ReplaceNearSubCommand extends AbstractCommand {
        private final EditPlugin plugin;
        private final RequiredArg<Integer> radiusArg;
        private final RequiredArg<String> fromArg;
        private final RequiredArg<String> toArg;

        public ReplaceNearSubCommand(EditPlugin plugin) {
            super("replacenear", "Remplacer dans un rayon (to: patterns %)");
            this.plugin = plugin;
            radiusArg = withRequiredArg("radius", "Rayon (1-100)", ArgTypes.INTEGER);
            fromArg = withRequiredArg("from", "Bloc a remplacer", ArgTypes.STRING);
            toArg = withRequiredArg("to", "Pattern (ex: stone, 20%a,80%b)", ArgTypes.STRING);
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

            ctx.sendMessage(ColorUtil.parse("&7Remplacement " + resolvedFrom.blockType() + " -> " + resolvedTo.pattern() + " (rayon " + radius + ")..."));
            return plugin.getBlockOperations().replaceNear(player, radius, resolvedFrom.blockType(), resolvedTo.pattern().toString())
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        }
    }

    public static class HelpSubCommand extends AbstractCommand {
        public HelpSubCommand() {
            super("help", "Afficher l'aide");
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            List<String> help = Arrays.asList(
                    "&6=== IslandiumEdit - Aide ===",
                    "",
                    "&e/edit wand &7- Obtenir la wand",
                    "&e/edit pos1 &7/ &e/edit pos2 &7- Definir positions",
                    "&e/edit set <block> &7- Remplir",
                    "&e/edit replace <from> <to> &7- Remplacer",
                    "&e/edit cyl <block> <r> <h> &7- Cylindre",
                    "&e/edit sphere <block> <r> &7- Sphere",
                    "&e/edit undo &7/ &e/edit redo &7- Annuler/Refaire"
            );
            for (String line : help) {
                ctx.sendMessage(ColorUtil.parse(line));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // Helper method for shapes
    private static CompletableFuture<Void> placeShape(CommandContext ctx, Player player, Shape shape, String blockTypeArg, EditPlugin plugin) {
        try {
            // Résoudre le type de bloc (hand, *, ou ID direct)
            var resolved = plugin.getBlockOperations().resolveBlockTypeWithError(player, blockTypeArg);
            if (!resolved.isValid()) {
                ctx.sendMessage(ColorUtil.parse("&c" + resolved.error()));
                return CompletableFuture.completedFuture(null);
            }
            String blockType = resolved.blockType();

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

            ctx.sendMessage(ColorUtil.parse("&7Placement de " + shape.getDescription() + " avec " + blockType + "..."));

            return plugin.getBlockOperations().placeShape(player, shape, centerX, centerY, centerZ, blockType)
                    .thenAccept(result -> sendOperationResult(ctx, result))
                    .thenApply(v -> null);
        } catch (Exception e) {
            ctx.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
    }
}
