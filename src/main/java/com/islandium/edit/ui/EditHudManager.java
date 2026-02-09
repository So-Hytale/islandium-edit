package com.islandium.edit.ui;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.edit.EditPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gestionnaire du HUD Edit.
 * Utilise l'API MultipleHUD (Buuz135) pour coexister avec les autres HUDs.
 * Affiche automatiquement quand le joueur a un clipboard, masque quand il est vide.
 */
public class EditHudManager {

    private static final String HUD_ID = "EditHud";
    private static final long REFRESH_INTERVAL_SECONDS = 1;

    private final EditPlugin plugin;
    private final Map<UUID, EditHud> activeHuds = new ConcurrentHashMap<>();
    private ScheduledExecutorService refreshScheduler;

    public EditHudManager(@NotNull EditPlugin plugin) {
        this.plugin = plugin;
        startRefreshTimer();
    }

    private void startRefreshTimer() {
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IslandiumEdit-HUD-Refresh");
            t.setDaemon(true);
            return t;
        });
        refreshScheduler.scheduleAtFixedRate(() -> {
            try {
                for (EditHud hud : activeHuds.values()) {
                    try {
                        hud.refreshData();
                    } catch (Exception e) {
                        // Ignore individual refresh errors
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }, REFRESH_INTERVAL_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
        }
        activeHuds.clear();
    }

    // === HUD Management ===

    /**
     * Affiche le HUD Edit pour un joueur.
     * Doit etre execute sur le world thread pour que MultipleHUD fonctionne.
     */
    @SuppressWarnings("deprecation")
    public void showHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                try {
                    var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null) return;

                    UUID uuid = playerRef.getUuid();

                    // Creer le HUD
                    EditHud hud = new EditHud(playerRef, player, plugin);
                    activeHuds.put(uuid, hud);

                    // Enregistrer via MultipleHUD
                    invokeMultipleHUD("setCustomHud", player, playerRef, HUD_ID, hud);
                } catch (Exception e) {
                    plugin.log(Level.WARNING, "Failed to show Edit HUD: " + e.getMessage());
                }
            }, world);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to show Edit HUD: " + e.getMessage());
        }
    }

    /**
     * Cache le HUD Edit pour un joueur.
     * Doit etre execute sur le world thread pour que MultipleHUD fonctionne.
     */
    @SuppressWarnings("deprecation")
    public void hideHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                try {
                    var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null) return;

                    UUID uuid = playerRef.getUuid();
                    activeHuds.remove(uuid);

                    // Retirer via MultipleHUD
                    invokeMultipleHUD("hideCustomHud", player, playerRef, HUD_ID);
                } catch (Exception e) {
                    plugin.log(Level.WARNING, "Failed to hide Edit HUD: " + e.getMessage());
                }
            }, world);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to hide Edit HUD: " + e.getMessage());
        }
    }

    /**
     * Verifie si le HUD est actif pour un joueur.
     */
    @SuppressWarnings("deprecation")
    public boolean hasHud(@NotNull Player player) {
        return activeHuds.containsKey(player.getUuid());
    }

    /**
     * Nettoie le HUD d'un joueur (deconnexion).
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        activeHuds.remove(uuid);
    }

    /**
     * Invoque une methode sur MultipleHUD.getInstance() par reflection.
     */
    private void invokeMultipleHUD(String methodName, Object... args) {
        try {
            Class<?> mhudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
            Method getInstance = mhudClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);

            for (Method m : mhudClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    m.invoke(instance, args);
                    return;
                }
            }

            plugin.log(Level.WARNING, "MultipleHUD method not found: " + methodName);
        } catch (ClassNotFoundException e) {
            plugin.log(Level.WARNING, "MultipleHUD not available - Edit HUD disabled");
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to invoke MultipleHUD." + methodName + ": " + e.getMessage());
        }
    }
}
