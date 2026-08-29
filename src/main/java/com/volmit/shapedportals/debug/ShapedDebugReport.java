package com.volmit.shapedportals.debug;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats.RejectionReason;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ShapedDebugReport {
    private static final int BUFFER_SIZE = 16 * 1024;

    private ShapedDebugReport() {
    }

    static String create(ShapedDebugSnapshot snapshot) {
        StringBuilder report = new StringBuilder(24_576);
        section(report, "ShapedPortals diagnostic report");
        value(report, "Generated (UTC)", snapshot.generatedAt());
        value(report, "Format", 4);
        value(report, "Command sender type", snapshot.senderType());

        section(report, "ShapedPortals");
        value(report, "Version", snapshot.pluginVersion());
        value(report, "Java bytecode target", 17);
        value(report, "Scheduler", snapshot.scheduler());
        value(report, "Active locale", snapshot.activeLocale());
        value(report, "Available locales", String.join(", ", snapshot.availableLocales()));
        value(report, "Language catalog", snapshot.languageCatalogState());
        value(report, "Language source reference", snapshot.languageCatalogReference());
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

        appendServer(report, snapshot);
        appendPortals(report, snapshot);
        appendConfig(report, snapshot);
        appendPlugins(report, snapshot.plugins());
        appendRuntime(report);
        appendMemory(report);
        appendCpu(report);
        appendGarbageCollectors(report);
        appendBufferPools(report);
        appendStorage(report, snapshot.dataDirectory());
        appendKnownFiles(report, snapshot);
        appendArtifact(report, snapshot.codeSource());
        return report.toString();
    }

    private static void appendServer(StringBuilder report, ShapedDebugSnapshot snapshot) {
        section(report, "Server");
        value(report, "Implementation", snapshot.serverName());
        value(report, "Server version", snapshot.serverVersion());
        value(report, "Bukkit API", snapshot.bukkitVersion());
        value(report, "Minecraft", snapshot.minecraftVersion());
        value(report, "Online mode", snapshot.onlineMode());
        value(report, "Players online", snapshot.onlinePlayers());
        value(report, "Maximum players", snapshot.maximumPlayers());
        value(report, "View distance", snapshot.viewDistance());
        value(report, "Simulation distance", snapshot.simulationDistance());
        value(report, "Hardcore", snapshot.hardcore());
        value(report, "Allow flight", snapshot.allowFlight());
        value(report, "Whitelist enabled", snapshot.whitelistEnabled());
        value(report, "Default game mode", snapshot.defaultGameMode());
        value(report, "Spawn radius", snapshot.spawnRadius());
        value(report, "Idle timeout minutes", snapshot.idleTimeout());
        value(report, "Pending scheduler tasks", snapshot.pendingSchedulerTasks() < 0
                ? "unavailable" : snapshot.pendingSchedulerTasks());
        value(report, "Loaded worlds", snapshot.loadedWorlds());
        for (Map.Entry<String, Integer> environment : snapshot.worldEnvironments().entrySet()) {
            value(report, "Worlds " + environment.getKey().toLowerCase(Locale.ROOT), environment.getValue());
        }
        value(report, "TPS", snapshot.ticksPerSecond());
        value(report, "MSPT", snapshot.millisecondsPerTick());
    }

    private static void appendConfig(StringBuilder report, ShapedDebugSnapshot snapshot) {
        section(report, "Effective configuration");
        ShapedPortalsConfig config = snapshot.config();
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
        value(report, "effects.creationSound", config.effects.creationSound);
        value(report, "effects.creationSoundType", config.effects.creationSoundType);
        value(report, "effects.creationSoundVolume", config.effects.creationSoundVolume);
        value(report, "effects.creationSoundPitch", config.effects.creationSoundPitch);
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
        value(report, "presentation.overlayDurationTicks", config.presentation.overlayDurationTicks);
        value(report, "presentation.titleFadeInTicks", config.presentation.titleFadeInTicks);
        value(report, "presentation.titleStayTicks", config.presentation.titleStayTicks);
        value(report, "presentation.titleFadeOutTicks", config.presentation.titleFadeOutTicks);
        value(report, "debug.uploadEnabled", config.debug.uploadEnabled);
    }

    private static void appendPlugins(StringBuilder report, List<ShapedDebugSnapshot.PluginState> plugins) {
        section(report, "Plugins");
        value(report, "Loaded", plugins.size());
        for (ShapedDebugSnapshot.PluginState plugin : plugins) {
            report.append("- ").append(sanitize(plugin.name()))
                    .append(' ').append(sanitize(plugin.version()))
                    .append(" | enabled=").append(plugin.enabled())
                    .append(" | main=").append(sanitize(plugin.mainClass()))
                    .append(" | authors=").append(sanitize(join(plugin.authors())))
                    .append(" | load=").append(sanitize(plugin.loadOrder()))
                    .append(" | api=").append(sanitize(plugin.apiVersion()))
                    .append(" | depends=").append(sanitize(join(plugin.dependencies())))
                    .append(" | softDepends=").append(sanitize(join(plugin.softDependencies())))
                    .append('\n');
        }
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
            if (portal.axis().name().equals("X")) {
                axisX++;
            } else {
                axisZ++;
            }
        }
        value(report, "Records", portals.size());
        value(report, "Axis X", axisX);
        value(report, "Axis Z", axisZ);
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
                    .append(" | interior=").append(portal.interior().size())
                    .append(" | frame=").append(portal.frame().size())
                    .append(" | chunks=").append(portal.chunks().size())
                    .append(" | created=").append(Instant.ofEpochMilli(portal.createdAtEpochMillis()))
                    .append('\n');
        }
    }

    private static void appendRuntime(StringBuilder report) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
        section(report, "Java runtime");
        value(report, "Java version", System.getProperty("java.version"));
        value(report, "Java vendor", System.getProperty("java.vendor"));
        value(report, "VM name", System.getProperty("java.vm.name"));
        value(report, "VM vendor", System.getProperty("java.vm.vendor"));
        value(report, "VM version", System.getProperty("java.vm.version"));
        value(report, "Process ID", runtime.getPid());
        value(report, "Uptime", duration(runtime.getUptime()));
        value(report, "Start time (epoch ms)", runtime.getStartTime());
        value(report, "Default locale", Locale.getDefault().toLanguageTag());
        value(report, "Default time zone", ZoneId.systemDefault());
        value(report, "Default charset", Charset.defaultCharset());
        value(report, "Native encoding", System.getProperty("native.encoding", "unavailable"));
        value(report, "Loaded classes", classes.getLoadedClassCount());
        value(report, "Total loaded classes", classes.getTotalLoadedClassCount());
        value(report, "Unloaded classes", classes.getUnloadedClassCount());
        if (compilation != null) {
            value(report, "JIT compiler", compilation.getName());
            if (compilation.isCompilationTimeMonitoringSupported()) {
                value(report, "JIT compilation time", duration(compilation.getTotalCompilationTime()));
            }
        }
    }

    private static void appendMemory(StringBuilder report) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        section(report, "Memory");
        memoryUsage(report, "Heap", memory.getHeapMemoryUsage());
        memoryUsage(report, "Non-heap", memory.getNonHeapMemoryUsage());
        value(report, "Runtime used", bytes(runtime.totalMemory() - runtime.freeMemory()));
        value(report, "Runtime free", bytes(runtime.freeMemory()));
        value(report, "Runtime total", bytes(runtime.totalMemory()));
        value(report, "Runtime maximum", bytes(runtime.maxMemory()));
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            memoryUsage(report, "Pool " + pool.getName(), pool.getUsage());
        }
    }

    private static void appendCpu(StringBuilder report) {
        java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        section(report, "CPU and operating system");
        value(report, "OS name", System.getProperty("os.name"));
        value(report, "OS version", System.getProperty("os.version"));
        value(report, "OS architecture", System.getProperty("os.arch"));
        value(report, "Logical processors", base.getAvailableProcessors());
        value(report, "System load average", decimal(base.getSystemLoadAverage()));
        if (base instanceof OperatingSystemMXBean operatingSystem) {
            value(report, "Process CPU load", percent(operatingSystem.getProcessCpuLoad()));
            value(report, "System CPU load", percent(operatingSystem.getCpuLoad()));
            value(report, "Process CPU time", duration(operatingSystem.getProcessCpuTime() / 1_000_000L));
            value(report, "Committed virtual memory", bytes(operatingSystem.getCommittedVirtualMemorySize()));
            value(report, "Physical memory total", bytes(operatingSystem.getTotalMemorySize()));
            value(report, "Physical memory free", bytes(operatingSystem.getFreeMemorySize()));
            value(report, "Swap total", bytes(operatingSystem.getTotalSwapSpaceSize()));
            value(report, "Swap free", bytes(operatingSystem.getFreeSwapSpaceSize()));
        }
        if (base instanceof UnixOperatingSystemMXBean unix) {
            value(report, "Open file descriptors", unix.getOpenFileDescriptorCount());
            value(report, "Maximum file descriptors", unix.getMaxFileDescriptorCount());
        }
    }

    private static void appendGarbageCollectors(StringBuilder report) {
        section(report, "Garbage collectors");
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            value(report, collector.getName() + " collections", collector.getCollectionCount());
            value(report, collector.getName() + " time", duration(collector.getCollectionTime()));
        }
    }

    private static void appendBufferPools(StringBuilder report) {
        section(report, "Buffer pools");
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            value(report, pool.getName() + " count", pool.getCount());
            value(report, pool.getName() + " used", bytes(pool.getMemoryUsed()));
            value(report, pool.getName() + " capacity", bytes(pool.getTotalCapacity()));
        }
    }

    private static void appendStorage(StringBuilder report, Path dataDirectory) {
        section(report, "Plugin data filesystem");
        try {
            FileStore store = Files.getFileStore(dataDirectory);
            value(report, "Type", store.type());
            value(report, "Total", bytes(store.getTotalSpace()));
            value(report, "Usable", bytes(store.getUsableSpace()));
            value(report, "Unallocated", bytes(store.getUnallocatedSpace()));
        } catch (IOException | RuntimeException exception) {
            value(report, "Status", "unavailable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private static void appendKnownFiles(StringBuilder report, ShapedDebugSnapshot snapshot) {
        section(report, "ShapedPortals files");
        appendKnownFile(report, snapshot.dataDirectory(), Path.of("config.toml"));
        appendKnownFile(report, snapshot.dataDirectory(), Path.of("portals.json"));
        appendKnownFile(report, snapshot.dataDirectory(),
                Path.of("languages", snapshot.activeLocale() + ".toml"));
    }

    private static void appendKnownFile(StringBuilder report, Path root, Path relative) {
        String label = relative.toString().replace('\\', '/');
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relative).normalize();
        if (!target.startsWith(normalizedRoot)) {
            value(report, label, "invalid path");
            return;
        }
        try {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
                value(report, label, "not present");
                return;
            }
            value(report, label, "size=" + bytes(Files.size(target))
                    + ", modified=" + Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toInstant()
                    + ", sha256=" + sha256(target));
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            value(report, label, "unavailable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private static void appendArtifact(StringBuilder report, Path codeSource) {
        section(report, "ShapedPortals artifact");
        if (codeSource == null) {
            value(report, "Status", "code source unavailable");
            return;
        }
        value(report, "Filename", codeSource.getFileName());
        value(report, "Type", Files.isRegularFile(codeSource) ? "jar" : Files.isDirectory(codeSource) ? "classes directory" : "unknown");
        if (!Files.isRegularFile(codeSource)) {
            return;
        }
        try {
            value(report, "Size", bytes(Files.size(codeSource)));
            value(report, "Modified", Files.getLastModifiedTime(codeSource, LinkOption.NOFOLLOW_LINKS).toInstant());
            value(report, "SHA-256", sha256(codeSource));
        } catch (IOException | NoSuchAlgorithmException exception) {
            value(report, "Status", "artifact inspection unavailable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void memoryUsage(StringBuilder report, String label, MemoryUsage usage) {
        if (usage == null) {
            value(report, label, "unavailable");
            return;
        }
        value(report, label, "used=" + bytes(usage.getUsed()) + ", committed=" + bytes(usage.getCommitted())
                + ", maximum=" + bytes(usage.getMax()));
    }

    private static void section(StringBuilder report, String name) {
        if (!report.isEmpty()) {
            report.append('\n');
        }
        report.append("== ").append(sanitize(name)).append(" ==\n");
    }

    private static void value(StringBuilder report, String name, Object value) {
        report.append(sanitize(name)).append(": ").append(sanitize(Objects.toString(value, "unavailable"))).append('\n');
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join(",", values);
    }

    private static String bytes(long value) {
        if (value < 0L) {
            return "unavailable";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double scaled = value;
        int unit = 0;
        while (scaled >= 1024D && unit < units.length - 1) {
            scaled /= 1024D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s (%d bytes)", scaled, units[unit], value);
    }

    private static String percent(double value) {
        return value < 0D || !Double.isFinite(value)
                ? "unavailable"
                : String.format(Locale.ROOT, "%.2f%%", value * 100D);
    }

    private static String decimal(double value) {
        return value < 0D || !Double.isFinite(value)
                ? "unavailable"
                : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String duration(long millis) {
        if (millis < 0L) {
            return "unavailable";
        }
        Duration duration = Duration.ofMillis(millis);
        return duration.toDaysPart() + "d " + duration.toHoursPart() + "h " + duration.toMinutesPart()
                + "m " + duration.toSecondsPart() + "s " + duration.toMillisPart() + "ms";
    }

    private static String sanitize(String value) {
        return Objects.requireNonNullElse(value, "unavailable")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }
}
