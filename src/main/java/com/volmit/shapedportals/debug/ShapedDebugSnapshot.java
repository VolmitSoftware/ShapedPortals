package com.volmit.shapedportals.debug;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record ShapedDebugSnapshot(
        String scheduler,
        String activeLocale,
        List<String> availableLocales,
        String languageCatalogState,
        String languageCatalogReference,
        boolean activeLocaleInRemoteCatalog,
        ShapedPortalsConfig config,
        boolean metricsInitialized,
        boolean reactIntegrationRegistered,
        int managedPortals,
        int interiorCells,
        List<PortalRecord> portalRecords,
        PortalStats.Snapshot portalStats,
        Set<UUID> loadedWorldIds,
        Path dataDirectory
) {
    ShapedDebugSnapshot {
        availableLocales = List.copyOf(availableLocales);
        config = config.copy();
        portalRecords = List.copyOf(portalRecords);
        loadedWorldIds = Set.copyOf(loadedWorldIds);
        dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }
}
