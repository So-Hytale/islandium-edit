package com.islandium.edit.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.edit.EditPlugin;
import com.islandium.edit.math.AffineTransform;
import com.islandium.edit.operation.ClipboardHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * HUD Edit affichant l'etat du clipboard en temps reel.
 * Montre: status, nombre de blocs, taille, rotation, flips.
 */
public class EditHud extends CustomUIHud {

    private final EditPlugin plugin;
    private final UUID playerUuid;
    private final Player player;
    private volatile boolean built = false;

    public EditHud(@NotNull PlayerRef playerRef, @NotNull Player player, @NotNull EditPlugin plugin) {
        super(playerRef);
        this.plugin = plugin;
        this.playerUuid = playerRef.getUuid();
        this.player = player;
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append("Pages/Edit/EditHud.ui");

        ClipboardHolder holder = plugin.getClipboardOperations().getClipboardHolder(player);
        if (holder == null || holder.isEmpty()) {
            cmd.set("#StatusValue.Text", "Vide");
            cmd.set("#StatusValue.Style.TextColor", "#808080");
            cmd.set("#BlocksValue.Text", "0");
            cmd.set("#SizeValue.Text", "---");
            cmd.set("#RotValue.Text", "0");
            cmd.set("#FlipValue.Text", "---");
            cmd.set("#FlipValue.Style.TextColor", "#505060");
            cmd.set("#HintValue.Text", "/ecopy pour copier");
        } else {
            applyClipboardData(cmd, holder);
        }

        built = true;
    }

    /**
     * Rafraichit les donnees du HUD en temps reel.
     */
    public void refreshData() {
        // Ne pas refresh tant que build() n'a pas ete execute par le client
        if (!built) return;

        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                try {
                    doRefresh();
                } catch (Exception e) {
                    // Ignore
                }
            }, world);
        } catch (Exception e) {
            // Silently ignore refresh errors
        }
    }

    private void doRefresh() {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            ClipboardHolder holder = plugin.getClipboardOperations().getClipboardHolder(player);
            if (holder == null || holder.isEmpty()) {
                cmd.set("#StatusValue.TextSpans", Message.raw("Vide"));
                cmd.set("#StatusValue.Style.TextColor", "#808080");
                cmd.set("#BlocksValue.TextSpans", Message.raw("0"));
                cmd.set("#SizeValue.TextSpans", Message.raw("---"));
                cmd.set("#RotValue.TextSpans", Message.raw("0"));
                cmd.set("#FlipValue.TextSpans", Message.raw("---"));
                cmd.set("#FlipValue.Style.TextColor", "#505060");
                cmd.set("#HintValue.TextSpans", Message.raw("/ecopy pour copier"));
            } else {
                applyClipboardDataUpdate(cmd, holder);
            }

            update(false, cmd);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Applique les donnees du clipboard dans le build() initial.
     */
    private void applyClipboardData(UICommandBuilder cmd, @NotNull ClipboardHolder holder) {
        // Status
        boolean hasPreview = plugin.getPreviewManager().hasActivePreview(player);
        if (hasPreview) {
            cmd.set("#StatusValue.Text", "Preview");
            cmd.set("#StatusValue.Style.TextColor", "#69f0ae");
        } else {
            cmd.set("#StatusValue.Text", "Pret");
            cmd.set("#StatusValue.Style.TextColor", "#4dd0e1");
        }

        // Blocs
        cmd.set("#BlocksValue.Text", String.valueOf(holder.getBlockCount()));

        // Taille
        cmd.set("#SizeValue.Text", holder.getWidth() + "x" + holder.getHeight() + "x" + holder.getDepth());

        // Transform info
        AffineTransform t = holder.getTransform();
        int rotation = t.getYRotation();
        boolean flipX = t.isFlipX();
        boolean flipZ = t.isFlipZ();
        boolean flipY = t.isVerticalFlip();

        // Rotation
        cmd.set("#RotValue.Text", rotation + "");
        if (rotation != 0) {
            cmd.set("#RotValue.Style.TextColor", "#ffab40");
        } else {
            cmd.set("#RotValue.Style.TextColor", "#505060");
        }

        // Flips
        String flipText = buildFlipText(flipX, flipZ, flipY);
        cmd.set("#FlipValue.Text", flipText);
        if (flipX || flipZ || flipY) {
            cmd.set("#FlipValue.Style.TextColor", "#ce93d8");
        } else {
            cmd.set("#FlipValue.Style.TextColor", "#505060");
        }

        // Hint
        if (hasPreview) {
            cmd.set("#HintValue.Text", "/epaste | /estop");
        } else {
            cmd.set("#HintValue.Text", "/epreview | /epaste");
        }
    }

    /**
     * Applique les donnees du clipboard pour un update() runtime.
     */
    private void applyClipboardDataUpdate(UICommandBuilder cmd, @NotNull ClipboardHolder holder) {
        // Status
        boolean hasPreview = plugin.getPreviewManager().hasActivePreview(player);
        if (hasPreview) {
            cmd.set("#StatusValue.TextSpans", Message.raw("Preview"));
            cmd.set("#StatusValue.Style.TextColor", "#69f0ae");
        } else {
            cmd.set("#StatusValue.TextSpans", Message.raw("Pret"));
            cmd.set("#StatusValue.Style.TextColor", "#4dd0e1");
        }

        // Blocs
        cmd.set("#BlocksValue.TextSpans", Message.raw(String.valueOf(holder.getBlockCount())));

        // Taille
        cmd.set("#SizeValue.TextSpans", Message.raw(holder.getWidth() + "x" + holder.getHeight() + "x" + holder.getDepth()));

        // Transform info
        AffineTransform t = holder.getTransform();
        int rotation = t.getYRotation();
        boolean flipX = t.isFlipX();
        boolean flipZ = t.isFlipZ();
        boolean flipY = t.isVerticalFlip();

        // Rotation
        cmd.set("#RotValue.TextSpans", Message.raw(rotation + ""));
        if (rotation != 0) {
            cmd.set("#RotValue.Style.TextColor", "#ffab40");
        } else {
            cmd.set("#RotValue.Style.TextColor", "#505060");
        }

        // Flips
        String flipText = buildFlipText(flipX, flipZ, flipY);
        cmd.set("#FlipValue.TextSpans", Message.raw(flipText));
        if (flipX || flipZ || flipY) {
            cmd.set("#FlipValue.Style.TextColor", "#ce93d8");
        } else {
            cmd.set("#FlipValue.Style.TextColor", "#505060");
        }

        // Hint
        if (hasPreview) {
            cmd.set("#HintValue.TextSpans", Message.raw("/epaste | /estop"));
        } else {
            cmd.set("#HintValue.TextSpans", Message.raw("/epreview | /epaste"));
        }
    }

    private String buildFlipText(boolean flipX, boolean flipZ, boolean flipY) {
        if (!flipX && !flipZ && !flipY) return "---";
        StringBuilder sb = new StringBuilder();
        if (flipX) sb.append("X ");
        if (flipZ) sb.append("Z ");
        if (flipY) sb.append("Y ");
        return sb.toString().trim();
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }
}
