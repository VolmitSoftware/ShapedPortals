package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class ConfigHotReloadService implements AutoCloseable {
    private static final long MAXIMUM_WATCHED_BYTES = 2L * 1024L * 1024L;

    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final ConfigHotloadEngine engine;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean reconfigureRequested = new AtomicBoolean();

    public ConfigHotReloadService(ShapedPortals plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.engine = new ConfigHotloadEngine(
                this::isManagedFile,
                this::knownFiles,
                this::readFile,
                ConfigFileSupport::normalize
        );
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ShapedPortals-HotReload");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        configureEngine();
        configService.setSelfWriteListener(this::noteSelfWrite);
        plugin.getLanguageService().setSelfWriteListener(this::noteSelfWrite);
        scheduleNext(configService.runtime().hotReloadPollMillis());
    }

    public void requestReconfigure() {
        reconfigureRequested.set(true);
    }

    public void noteSelfWrite(File file, String content) {
        engine.noteSelfWrite(file, content);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        configService.setSelfWriteListener(null);
        plugin.getLanguageService().setSelfWriteListener(null);
        engine.clear();
        executor.shutdownNow();
    }

    private void scheduleNext(long delayMillis) {
        if (closed.get()) {
            return;
        }
        try {
            executor.schedule(this::poll, Math.max(250L, delayMillis), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            if (!closed.get()) {
                plugin.getLogger().log(Level.SEVERE, "ShapedPortals configuration watcher could not reschedule", exception);
            }
        }
    }

    private void poll() {
        try {
            if (reconfigureRequested.getAndSet(false)) {
                configureEngine();
            }
            Set<ConfigHotloadEngine.StableContentSnapshot> snapshots = engine.pollTouchedSnapshots();
            if (!snapshots.isEmpty()) {
                applySnapshots(snapshots);
            }
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals configuration watcher failed", exception);
        } finally {
            scheduleNext(configService.runtime().hotReloadPollMillis());
        }
    }

    private void applySnapshots(Set<ConfigHotloadEngine.StableContentSnapshot> snapshots) {
        boolean enabled = configService.runtime().hotReloadEnabled();
        boolean applied = !enabled || plugin.reloadAll(false);
        for (ConfigHotloadEngine.StableContentSnapshot snapshot : snapshots) {
            engine.processSnapshotChange(snapshot, ignored -> applied, null);
        }
        if (!enabled) {
            return;
        }
        if (applied) {
            plugin.getLogger().info("Applied ShapedPortals configuration file changes.");
        } else {
            plugin.getLogger().warning("Rejected ShapedPortals file changes; the last known good settings remain active.");
        }
        notifyOperators(applied);
    }

    private void configureEngine() {
        RuntimeConfig config = configService.runtime();
        engine.configure(
                config.hotReloadPollMillis(),
                config.hotReloadCooldownMillis(),
                knownFiles(),
                List.of(configService.dataFolder(), plugin.getLanguageService().languageDirectory())
        );
    }

    private void notifyOperators(boolean success) {
        if (!configService.runtime().hotReloadNotifyOperators()) {
            return;
        }
        FoliaScheduler.runGlobal(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.hasPermission("shapedportals.config")) {
                    plugin.getPresentationService().hotReload(player, success);
                }
            }
        });
    }

    private List<File> knownFiles() {
        return List.of(configService.configFile(), plugin.getLanguageService().activeFile());
    }

    private boolean isManagedFile(File file) {
        if (file == null) {
            return false;
        }
        File absolute = file.getAbsoluteFile();
        return absolute.equals(configService.configFile().getAbsoluteFile())
                || plugin.getLanguageService().isLanguageFile(absolute);
    }

    private String readFile(File file) {
        if (file == null) {
            return null;
        }
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || file.length() > MAXIMUM_WATCHED_BYTES) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }
}
