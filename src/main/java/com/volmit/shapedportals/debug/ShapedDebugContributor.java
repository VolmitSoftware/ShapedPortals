package com.volmit.shapedportals.debug;

import art.arcane.volmlib.util.diagnostics.DebugDumpContributor;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.World;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
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
                languageSourceState(),
                plugin.getLanguageService().sourceDescription(),
                plugin.getLanguageService().isRepositoryLocale(plugin.getConfigService().runtime().language()),
                ShapedDebugSnapshot.ConfigState.capture(plugin.getConfigService().editableCopy()),
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

    private String languageSourceState() {
        Optional<Throwable> failure = plugin.getLanguageService().sourceFailure();
        if (failure.isPresent()) {
            return "unavailable (" + failure.get().getClass().getSimpleName() + ")";
        }
        Optional<LanguageUpstreamSync.Status> status = plugin.getLanguageService().upstreamStatus();
        if (status.isEmpty()) {
            return "unavailable";
        }
        LanguageUpstreamSync.Status current = status.get();
        StringBuilder state = new StringBuilder();
        state.append(current.revision().map(sha -> "revision " + sha.substring(0, 7)).orElse("not checked"));
        if (current.checkedMillis() > 0L) {
            state.append(", checked ").append(Instant.ofEpochMilli(current.checkedMillis()));
        }
        current.lastError().ifPresent(error -> state.append(", last error: ").append(error));
        return state.toString();
    }
}
