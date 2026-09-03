package com.volmit.shapedportals.debug;

import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ShapedDebugSource implements DebugDumpContributor {
    private final ShapedPortals plugin;

    public ShapedDebugSource(ShapedPortals plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Report capture() {
        Set<UUID> loadedWorldIds = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            loadedWorldIds.add(world.getUID());
        }
        String locale = plugin.getConfigService().runtime().language();
        ShapedDebugSnapshot snapshot = new ShapedDebugSnapshot(
                loadedWorldIds,
                locale,
                plugin.getLanguageService().availableLocales(),
                languageCatalogState(),
                plugin.getLanguageService().remoteCatalogReference().orElse("unavailable"),
                plugin.getLanguageService().hasRemoteCatalogLocale(locale),
                ShapedDebugSnapshot.ConfigState.capture(plugin.getConfigService().editableCopy()),
                plugin.getMetricsService() != null && plugin.getMetricsService().initialized(),
                plugin.getIntegrationService() != null && plugin.getIntegrationService().registered(),
                plugin.getPortalRegistry().portalCount(),
                plugin.getPortalRegistry().interiorCellCount(),
                plugin.getPortalRegistry().allRecords(),
                plugin.getPortalStats().snapshot(),
                plugin.getDataFolder().toPath().toAbsolutePath().normalize()
        );
        return () -> ShapedDebugReport.create(snapshot);
    }

    private String languageCatalogState() {
        return plugin.getLanguageService().remoteCatalogFailure()
                .map(failure -> "unavailable (" + failure.getClass().getSimpleName() + ")")
                .orElse("ready");
    }
}
