package com.islandium.edit.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.edit.EditPlugin;

import java.util.logging.Level;

/**
 * Interaction custom pour la wand de sélection.
 * - PRIMARY (clic gauche) = Pos1
 * - SECONDARY (clic droit) = Pos2
 */
public class WandInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<WandInteraction> CODEC = BuilderCodec.builder(WandInteraction.class, WandInteraction::new).build();

    public WandInteraction() {
        super("Edit_Wand");
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        EditPlugin plugin = EditPlugin.get();
        if (!EditPlugin.isInitialized()) {
            return;
        }

        // Récupérer le joueur depuis le contexte
        var entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = entityRef.getStore();
        if (store == null) {
            return;
        }

        // Store implémente ComponentAccessor, on peut l'utiliser directement
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Récupérer le bloc ciblé
        BlockPosition targetBlockPos = context.getTargetBlock();
        if (targetBlockPos == null) {
            // Aucun bloc ciblé = désélectionner la zone
            plugin.getSelectionManager().clearSelectionVisual(player);
            player.sendMessage(ColorUtil.parse("&7Selection effacee."));
            return;
        }

        Vector3i blockPos = new Vector3i(targetBlockPos.x, targetBlockPos.y, targetBlockPos.z);

        // PRIMARY = clic gauche = Pos1, SECONDARY = clic droit = Pos2
        if (type == InteractionType.Primary) {
            plugin.log(Level.INFO, "[WandInteraction] PRIMARY (left click) by " + player.getDisplayName());
            plugin.getSelectionManager().setPos1(player, blockPos);
            player.sendMessage(ColorUtil.parse("&aPos1: &f" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ()));
        } else if (type == InteractionType.Secondary) {
            plugin.log(Level.INFO, "[WandInteraction] SECONDARY (right click) by " + player.getDisplayName());
            plugin.getSelectionManager().setPos2(player, blockPos);
            player.sendMessage(ColorUtil.parse("&aPos2: &f" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ()));
        }

        // Afficher le volume de la sélection si les deux positions sont définies
        showSelectionInfo(plugin, player);
    }

    private void showSelectionInfo(EditPlugin plugin, Player player) {
        // Utiliser les méthodes locales pour vérifier la sélection
        if (plugin.getSelectionManager().hasLocalSelection(player)) {
            long volume = plugin.getSelectionManager().getLocalVolume(player);
            player.sendMessage(ColorUtil.parse("&7Selection: " + volume + " blocs"));
        }
    }
}
