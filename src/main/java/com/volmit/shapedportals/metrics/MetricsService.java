package com.volmit.shapedportals.metrics;

import com.volmit.shapedportals.ShapedPortals;

import java.util.Objects;
import java.util.logging.Level;

public final class MetricsService implements AutoCloseable {
    private static final int BSTATS_PLUGIN_ID = 33267;

    private final ShapedPortals plugin;
    private Metrics metrics;
    private boolean closed;

    public MetricsService(ShapedPortals plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void reconfigure(boolean enabled) {
        if (closed) {
            return;
        }
        if (enabled == (metrics != null)) {
            return;
        }
        shutdownActive();
        if (!enabled) {
            return;
        }
        try {
            metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to start ShapedPortals bStats metrics", exception);
        }
    }

    public synchronized boolean initialized() {
        return metrics != null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        shutdownActive();
    }

    private void shutdownActive() {
        Metrics active = metrics;
        metrics = null;
        if (active == null) {
            return;
        }
        try {
            active.shutdown();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to stop ShapedPortals bStats metrics", exception);
        }
    }
}
