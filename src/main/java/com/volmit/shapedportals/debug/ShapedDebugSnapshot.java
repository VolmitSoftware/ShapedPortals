package com.volmit.shapedportals.debug;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record ShapedDebugSnapshot(
        String scheduler,
        String activeLocale,
        List<String> availableLocales,
        String languageCatalogState,
        String languageCatalogReference,
        boolean activeLocaleInRemoteCatalog,
        ConfigState config,
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
        loadedWorldIds = Set.copyOf(loadedWorldIds);
        availableLocales = List.copyOf(availableLocales);
        portalRecords = List.copyOf(portalRecords);
        dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    record ConfigState(
            boolean integrityEnabled,
            boolean hotReloadEnabled,
            boolean uploadEnabled,
            boolean metricsEnabled,
            Map<String, String> settings
    ) {
        ConfigState {
            settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }

        static ConfigState capture(ShapedPortalsConfig config) {
            Map<String, String> settings = new LinkedHashMap<>();
            setting(settings, "general.enabled", config.general.enabled);
            setting(settings, "general.language", config.general.language);
            setting(settings, "general.requireCreatePermission", config.general.requireCreatePermission);
            setting(settings, "general.failureFeedback", config.general.failureFeedback);
            setting(settings, "metrics.enabled", config.metrics.enabled);
            setting(settings, "portal.minimumInteriorBlocks", config.portal.minimumInteriorBlocks);
            setting(settings, "portal.maximumInteriorBlocks", config.portal.maximumInteriorBlocks);
            setting(settings, "portal.maximumWidth", config.portal.maximumWidth);
            setting(settings, "portal.maximumHeight", config.portal.maximumHeight);
            setting(settings, "portal.frameMaterials", join(config.portal.frameMaterials));
            setting(settings, "portal.interiorMaterials", join(config.portal.interiorMaterials));
            setting(settings, "portal.ignitionCauses", join(config.portal.ignitionCauses));
            setting(settings, "portal.allowedWorlds count", config.portal.allowedWorlds.size());
            setting(settings, "portal.deniedWorlds count", config.portal.deniedWorlds.size());
            setting(settings, "portal.deduplicationMillis", config.portal.deduplicationMillis);
            setting(settings, "portal.endPortalCreation", config.portal.endPortalCreation);
            setting(settings, "portal.endMinimumInteriorBlocks", config.portal.endMinimumInteriorBlocks);
            setting(settings, "portal.endMaximumInteriorBlocks", config.portal.endMaximumInteriorBlocks);
            setting(settings, "portal.endMaximumWidth", config.portal.endMaximumWidth);
            setting(settings, "portal.endMaximumLength", config.portal.endMaximumLength);
            setting(settings, "portal.endInteriorMaterials", join(config.portal.endInteriorMaterials));
            setting(settings, "effects.creationSound", config.effects.creationSound);
            setting(settings, "effects.creationSoundType", config.effects.creationSoundType);
            setting(settings, "effects.creationSoundVolume", config.effects.creationSoundVolume);
            setting(settings, "effects.creationSoundPitch", config.effects.creationSoundPitch);
            setting(settings, "effects.endCreationSound", config.effects.endCreationSound);
            setting(settings, "effects.endCreationSoundType", config.effects.endCreationSoundType);
            setting(settings, "effects.endCreationSoundVolume", config.effects.endCreationSoundVolume);
            setting(settings, "effects.endCreationSoundPitch", config.effects.endCreationSoundPitch);
            setting(settings, "hotReload.enabled", config.hotReload.enabled);
            setting(settings, "hotReload.pollIntervalMillis", config.hotReload.pollIntervalMillis);
            setting(settings, "hotReload.cooldownMillis", config.hotReload.cooldownMillis);
            setting(settings, "hotReload.notifyOperators", config.hotReload.notifyOperators);
            setting(settings, "integrity.enabled", config.integrity.enabled);
            setting(settings, "integrity.checkIntervalTicks", config.integrity.checkIntervalTicks);
            setting(settings, "integrity.maximumChecksPerCycle", config.integrity.maximumChecksPerCycle);
            setting(settings, "presentation.splashScreen", config.presentation.splashScreen);
            setting(settings, "presentation.commandSounds", config.presentation.commandSounds);
            setting(settings, "presentation.commandOverlays", join(config.presentation.commandOverlays));
            setting(settings, "presentation.portalNotices", join(config.presentation.portalNotices));
            setting(settings, "presentation.netherCreationNotices", join(config.presentation.netherCreationNotices));
            setting(settings, "presentation.endCreationNotices", join(config.presentation.endCreationNotices));
            setting(settings, "presentation.overlayDurationTicks", config.presentation.overlayDurationTicks);
            setting(settings, "presentation.titleFadeInTicks", config.presentation.titleFadeInTicks);
            setting(settings, "presentation.titleStayTicks", config.presentation.titleStayTicks);
            setting(settings, "presentation.titleFadeOutTicks", config.presentation.titleFadeOutTicks);
            setting(settings, "debug.uploadEnabled", config.debug.uploadEnabled);
            return new ConfigState(config.integrity.enabled, config.hotReload.enabled,
                    config.debug.uploadEnabled, config.metrics.enabled, settings);
        }

        private static void setting(Map<String, String> settings, String name, Object value) {
            settings.put(name, Objects.toString(value, "unavailable"));
        }

        private static String join(List<String> values) {
            return values.isEmpty() ? "none" : String.join(",", values);
        }
    }
}
