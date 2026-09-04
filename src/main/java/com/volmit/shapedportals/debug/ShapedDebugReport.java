package com.volmit.shapedportals.debug;

import art.arcane.volmlib.util.diagnostics.DebugDumpReport;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats.RejectionReason;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ShapedDebugReport {
    private ShapedDebugReport() {
    }

    static String create(ShapedDebugSnapshot snapshot) {
        StringBuilder report = new StringBuilder(12_288);
        section(report, "ShapedPortals services");
        value(report, "Java bytecode target", 17);
        value(report, "Scheduler", snapshot.scheduler());
        value(report, "Active locale", snapshot.activeLocale());
        value(report, "Available locales", String.join(", ", snapshot.availableLocales()));
        value(report, "Language catalog", snapshot.languageCatalogState());
        value(report, "Language source reference", snapshot.languageCatalogReference());
        value(report, "Active locale in remote catalog", snapshot.activeLocaleInRemoteCatalog());
        value(report, "Managed portals", snapshot.managedPortals());
        value(report, "Interior cells", snapshot.interiorCells());
        value(report, "Creation attempts", snapshot.portalStats().attempts());
        value(report, "Created portals", snapshot.portalStats().created());
        value(report, "Rejected attempts", snapshot.portalStats().rejected());
        for (RejectionReason reason : RejectionReason.values()) {
            long count = snapshot.portalStats().rejectionReasons().getOrDefault(reason, 0L);
            if (count > 0L) {
                value(report, "Rejected " + reason.name().toLowerCase(Locale.ROOT).replace('_', ' '), count);
            }
        }
        value(report, "Configuration service", "active");
        value(report, "Language service", "active");
        value(report, "Portal registry", "active");
        value(report, "Integrity service", snapshot.config().integrity.enabled ? "enabled" : "disabled");
        value(report, "Hot reload service", snapshot.config().hotReload.enabled ? "enabled" : "disabled");
        value(report, "Debug uploads", snapshot.config().debug.uploadEnabled ? "enabled" : "disabled");
        value(report, "bStats setting", snapshot.config().metrics.enabled ? "enabled" : "disabled");
        value(report, "bStats integration", snapshot.metricsInitialized() ? "initialized" : "not initialized");
        value(report, "React metric provider", snapshot.reactIntegrationRegistered() ? "registered" : "not registered");
        appendPortals(report, snapshot);
        appendConfig(report, snapshot.config());
        appendFiles(report, snapshot);
        return report.toString();
    }

    private static void appendConfig(StringBuilder report, ShapedPortalsConfig config) {
        section(report, "Effective ShapedPortals configuration");
        value(report, "general.enabled", config.general.enabled);
        value(report, "general.language", config.general.language);
        value(report, "general.requireCreatePermission", config.general.requireCreatePermission);
        value(report, "general.failureFeedback", config.general.failureFeedback);
        value(report, "metrics.enabled", config.metrics.enabled);
        value(report, "portal.minimumInteriorBlocks", config.portal.minimumInteriorBlocks);
        value(report, "portal.maximumInteriorBlocks", config.portal.maximumInteriorBlocks);
        value(report, "portal.maximumWidth", config.portal.maximumWidth);
        value(report, "portal.maximumHeight", config.portal.maximumHeight);
        value(report, "portal.frameMaterials", join(config.portal.frameMaterials));
        value(report, "portal.interiorMaterials", join(config.portal.interiorMaterials));
        value(report, "portal.ignitionCauses", join(config.portal.ignitionCauses));
        value(report, "portal.allowedWorlds count", config.portal.allowedWorlds.size());
        value(report, "portal.deniedWorlds count", config.portal.deniedWorlds.size());
        value(report, "portal.deduplicationMillis", config.portal.deduplicationMillis);
        value(report, "portal.endPortalCreation", config.portal.endPortalCreation);
        value(report, "portal.endMinimumInteriorBlocks", config.portal.endMinimumInteriorBlocks);
        value(report, "portal.endMaximumInteriorBlocks", config.portal.endMaximumInteriorBlocks);
        value(report, "portal.endMaximumWidth", config.portal.endMaximumWidth);
        value(report, "portal.endMaximumLength", config.portal.endMaximumLength);
        value(report, "portal.endInteriorMaterials", join(config.portal.endInteriorMaterials));
        value(report, "effects.creationSound", config.effects.creationSound);
        value(report, "effects.creationSoundType", config.effects.creationSoundType);
        value(report, "effects.creationSoundVolume", config.effects.creationSoundVolume);
        value(report, "effects.creationSoundPitch", config.effects.creationSoundPitch);
        value(report, "effects.endCreationSound", config.effects.endCreationSound);
        value(report, "effects.endCreationSoundType", config.effects.endCreationSoundType);
        value(report, "effects.endCreationSoundVolume", config.effects.endCreationSoundVolume);
        value(report, "effects.endCreationSoundPitch", config.effects.endCreationSoundPitch);
        value(report, "hotReload.enabled", config.hotReload.enabled);
        value(report, "hotReload.pollIntervalMillis", config.hotReload.pollIntervalMillis);
        value(report, "hotReload.cooldownMillis", config.hotReload.cooldownMillis);
        value(report, "hotReload.notifyOperators", config.hotReload.notifyOperators);
        value(report, "integrity.enabled", config.integrity.enabled);
        value(report, "integrity.checkIntervalTicks", config.integrity.checkIntervalTicks);
        value(report, "integrity.maximumChecksPerCycle", config.integrity.maximumChecksPerCycle);
        value(report, "presentation.splashScreen", config.presentation.splashScreen);
        value(report, "presentation.commandSounds", config.presentation.commandSounds);
        value(report, "presentation.commandOverlays", join(config.presentation.commandOverlays));
        value(report, "presentation.portalNotices", join(config.presentation.portalNotices));
        value(report, "presentation.netherCreationNotices", join(config.presentation.netherCreationNotices));
        value(report, "presentation.endCreationNotices", join(config.presentation.endCreationNotices));
        value(report, "presentation.overlayDurationTicks", config.presentation.overlayDurationTicks);
        value(report, "presentation.titleFadeInTicks", config.presentation.titleFadeInTicks);
        value(report, "presentation.titleStayTicks", config.presentation.titleStayTicks);
        value(report, "presentation.titleFadeOutTicks", config.presentation.titleFadeOutTicks);
        value(report, "debug.uploadEnabled", config.debug.uploadEnabled);
    }

    private static void appendPortals(StringBuilder report, ShapedDebugSnapshot snapshot) {
        List<PortalRecord> portals = snapshot.portalRecords();
        section(report, "Portal registry details");
        if (portals.isEmpty()) {
            value(report, "Records", 0);
            return;
        }
        long interior = 0L;
        long frame = 0L;
        long chunks = 0L;
        long oldest = Long.MAX_VALUE;
        long newest = Long.MIN_VALUE;
        int axisX = 0;
        int axisZ = 0;
        int axisY = 0;
        int loadedWorldRecords = 0;
        int unavailableWorldRecords = 0;
        Set<UUID> worlds = new HashSet<>();
        for (PortalRecord portal : portals) {
            worlds.add(portal.worldId());
            if (snapshot.loadedWorldIds().contains(portal.worldId())) {
                loadedWorldRecords++;
            } else {
                unavailableWorldRecords++;
            }
            interior += portal.interior().size();
            frame += portal.frame().size();
            chunks += portal.chunks().size();
            oldest = Math.min(oldest, portal.createdAtEpochMillis());
            newest = Math.max(newest, portal.createdAtEpochMillis());
            switch (portal.axis()) {
                case X -> axisX++;
                case Z -> axisZ++;
                case Y -> axisY++;
            }
        }
        value(report, "Records", portals.size());
        value(report, "Axis X", axisX);
        value(report, "Axis Z", axisZ);
        value(report, "Axis Y (End)", axisY);
        value(report, "Worlds represented", worlds.size());
        value(report, "Records in loaded worlds", loadedWorldRecords);
        value(report, "Records in unavailable worlds", unavailableWorldRecords);
        value(report, "Interior total", interior);
        value(report, "Interior average", decimal((double) interior / portals.size()));
        value(report, "Frame total", frame);
        value(report, "Frame average", decimal((double) frame / portals.size()));
        value(report, "Affected chunk references", chunks);
        value(report, "Oldest creation", Instant.ofEpochMilli(oldest));
        value(report, "Newest creation", Instant.ofEpochMilli(newest));
        ArrayList<PortalRecord> ordered = new ArrayList<>(portals);
        ordered.sort(Comparator.comparing(PortalRecord::id));
        for (PortalRecord portal : ordered) {
            report.append("- ").append(portal.id())
                    .append(" | schema=").append(portal.schemaVersion())
                    .append(" | axis=").append(portal.axis().name())
                    .append(" | type=").append(portal.type().name())
                    .append(" | interior=").append(portal.interior().size())
                    .append(" | frame=").append(portal.frame().size())
                    .append(" | chunks=").append(portal.chunks().size())
                    .append(" | created=").append(Instant.ofEpochMilli(portal.createdAtEpochMillis()))
                    .append('\n');
        }
    }

    private static void appendFiles(StringBuilder report, ShapedDebugSnapshot snapshot) {
        section(report, "ShapedPortals files");
        report.append(DebugDumpReport.describeFiles(snapshot.dataDirectory(), List.of(
                Path.of("config.toml"),
                Path.of("portals.json"),
                Path.of("languages", snapshot.activeLocale() + ".toml"),
                Path.of("languages", "language-preferences.properties")
        )));
    }

    private static void section(StringBuilder report, String name) {
        if (!report.isEmpty()) {
            report.append('\n');
        }
        report.append("== ").append(sanitize(name)).append(" ==\n");
    }

    private static void value(StringBuilder report, String name, Object value) {
        report.append(sanitize(name)).append(": ")
                .append(sanitize(Objects.toString(value, "unavailable"))).append('\n');
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join(",", values);
    }

    private static String decimal(double value) {
        return !Double.isFinite(value) ? "unavailable" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String sanitize(String value) {
        return Objects.requireNonNullElse(value, "unavailable")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }
}
