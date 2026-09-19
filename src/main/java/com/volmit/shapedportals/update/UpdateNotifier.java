package com.volmit.shapedportals.update;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.update.GitHubReleaseChecker;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.ShapedMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

public final class UpdateNotifier implements Listener, AutoCloseable {
    private static final String PERMISSION = "shapedportals.update";

    private final ShapedPortals plugin;
    private final GitHubReleaseChecker checker;
    private final String installedVersion;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean enabled;
    private volatile boolean closed;

    public UpdateNotifier(ShapedPortals plugin, GitHubReleaseChecker checker) {
        this.plugin = plugin;
        this.checker = checker;
        installedVersion = plugin.getDescription().getVersion();
    }

    public synchronized void reconfigure() {
        boolean requested = plugin.getConfigService().runtime().updateNotifications();
        if (closed || enabled == requested) {
            return;
        }
        generation.incrementAndGet();
        enabled = requested;
        checker.setEnabled(requested);
    }

    @Override
    public synchronized void close() {
        closed = true;
        enabled = false;
        generation.incrementAndGet();
        checker.close();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long expectedGeneration = generation.get();
        if (!active(expectedGeneration) || !eligible(player)) {
            return;
        }
        checker.check().thenAccept(release -> {
            if (release.isPresent() && active(expectedGeneration)) {
                FoliaScheduler.runEntity(plugin, player,
                        () -> notifyOwned(player, release.get(), expectedGeneration), 20L);
            }
        });
    }

    private void notifyOwned(Player player, GitHubReleaseChecker.Release release, long expectedGeneration) {
        if (!active(expectedGeneration) || !player.isOnline() || !eligible(player)) {
            return;
        }
        ComponentText message = plugin.getLanguageService().renderPrefixed(player, ShapedMessages.UPDATE_AVAILABLE, MessageArgs.builder()
                .untrusted("new", release.tagName())
                .untrusted("old", installedVersion)
                .untrusted("url", release.url())
                .build());
        ComponentMessenger.sendOpenUrl(player, message, URI.create(release.url()),
                ComponentText.literal(release.url()));
    }

    private boolean active(long expectedGeneration) {
        return !closed && enabled && generation.get() == expectedGeneration && plugin.isEnabled()
                && plugin.getConfigService().runtime().updateNotifications();
    }

    private boolean eligible(Player player) {
        return player.isOp() || player.hasPermission(PERMISSION);
    }
}
