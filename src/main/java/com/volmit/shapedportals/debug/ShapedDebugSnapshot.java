package com.volmit.shapedportals.debug;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

record ShapedDebugSnapshot(
        Instant generatedAt,
        String pluginVersion,
        String serverName,
        String serverVersion,
        String bukkitVersion,
        String minecraftVersion,
        boolean onlineMode,
        int onlinePlayers,
        int maximumPlayers,
        int viewDistance,
        int simulationDistance,
        boolean hardcore,
        boolean allowFlight,
        boolean whitelistEnabled,
        String defaultGameMode,
        int spawnRadius,
        int idleTimeout,
        int pendingSchedulerTasks,
        int loadedWorlds,
        Map<String, Integer> worldEnvironments,
        Set<UUID> loadedWorldIds,
        String scheduler,
        String ticksPerSecond,
        String millisecondsPerTick,
        String activeLocale,
        List<String> availableLocales,
        String languageCatalogState,
        String languageCatalogReference,
        boolean activeLocaleInRemoteCatalog,
        String senderType,
        ShapedPortalsConfig config,
        boolean metricsInitialized,
        boolean reactIntegrationRegistered,
        int managedPortals,
        int interiorCells,
        List<PortalRecord> portalRecords,
        PortalStats.Snapshot portalStats,
        List<PluginState> plugins,
        Path dataDirectory,
        Path codeSource
) {
    ShapedDebugSnapshot {
        worldEnvironments = Collections.unmodifiableMap(new TreeMap<>(worldEnvironments));
        loadedWorldIds = Set.copyOf(loadedWorldIds);
        availableLocales = List.copyOf(availableLocales);
        config = config.copy();
        portalRecords = List.copyOf(portalRecords);
        plugins = List.copyOf(plugins);
    }

    record PluginState(
            String name,
            String version,
            boolean enabled,
            String mainClass,
            List<String> authors,
            String loadOrder,
            String apiVersion,
            List<String> dependencies,
            List<String> softDependencies
    ) {
        PluginState {
            authors = List.copyOf(authors);
            dependencies = List.copyOf(dependencies);
            softDependencies = List.copyOf(softDependencies);
        }
    }
}
