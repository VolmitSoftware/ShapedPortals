package com.volmit.shapedportals.debug;

import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ShapedDebugContributor implements DebugDumpContributor {
    private final ShapedPortals plugin;

    public ShapedDebugContributor(ShapedPortals plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Report capture() {
        Set<UUID> loadedWorldIds = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            loadedWorldIds.add(world.getUID());
        }
        ShapedDebugSnapshot snapshot = new ShapedDebugSnapshot(
                plugin.schedulerName(),
                plugin.getConfigService().runtime().language(),
                plugin.getLanguageService().availableLocales(),
                plugin.getLanguageService().remoteCatalogFailure()
                        .map(failure -> "unavailable (" + failure.getClass().getSimpleName() + ")")
                        .orElse("ready"),
                plugin.getLanguageService().remoteCatalogReference().orElse("unavailable"),
                plugin.getLanguageService().hasRemoteCatalogLocale(plugin.getConfigService().runtime().language()),
                plugin.getConfigService().editableCopy(),
                plugin.getMetricsService() != null && plugin.getMetricsService().initialized(),
                plugin.getIntegrationService() != null && plugin.getIntegrationService().registered(),
                plugin.getPortalRegistry().portalCount(),
                plugin.getPortalRegistry().interiorCellCount(),
                plugin.getPortalRegistry().allRecords(),
                plugin.getPortalStats().snapshot(),
                loadedWorldIds,
                plugin.getDataFolder().toPath()
        );
        return () -> ShapedDebugReport.create(snapshot);
    }
}
