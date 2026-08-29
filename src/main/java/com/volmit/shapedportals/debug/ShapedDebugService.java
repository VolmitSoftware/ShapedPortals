package com.volmit.shapedportals.debug;

import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.FeedbackTone;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class ShapedDebugService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final ShapedPortals plugin;
    private final MclogsClient mclogs;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ShapedDebugService(ShapedPortals plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        mclogs = new MclogsClient();
    }

    public void generate(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (closed.get()) {
            feedback(sender, ShapedMessages.DEBUG_FAILED, FeedbackTone.FAILURE);
            return;
        }
        if (!active.compareAndSet(false, true)) {
            feedback(sender, ShapedMessages.DEBUG_BUSY, FeedbackTone.INFO);
            return;
        }
        feedback(sender, ShapedMessages.DEBUG_STARTED, FeedbackTone.INFO);
        if (!FoliaScheduler.runGlobal(plugin, () -> captureAndSchedule(sender))) {
            active.set(false);
            feedback(sender, ShapedMessages.DEBUG_FAILED, FeedbackTone.FAILURE);
        }
    }

    public void shutdown() {
        closed.set(true);
    }

    private void captureAndSchedule(CommandSender sender) {
        ShapedDebugSnapshot snapshot;
        try {
            snapshot = capture(sender);
        } catch (RuntimeException exception) {
            active.set(false);
            plugin.getLogger().log(Level.SEVERE, "Unable to capture ShapedPortals diagnostic state", exception);
            feedback(sender, ShapedMessages.DEBUG_FAILED, FeedbackTone.FAILURE);
            return;
        }
        if (!FoliaScheduler.runAsync(plugin, () -> writeAndUpload(sender, snapshot))) {
            active.set(false);
            feedback(sender, ShapedMessages.DEBUG_FAILED, FeedbackTone.FAILURE);
        }
    }

    private ShapedDebugSnapshot capture(CommandSender sender) {
        List<ShapedDebugSnapshot.PluginState> plugins = new ArrayList<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            PluginDescriptionFile description = installed.getDescription();
            plugins.add(new ShapedDebugSnapshot.PluginState(
                    installed.getName(),
                    description.getVersion(),
                    installed.isEnabled(),
                    description.getMain(),
                    description.getAuthors(),
                    description.getLoad().name(),
                    Objects.requireNonNullElse(description.getAPIVersion(), "unspecified"),
                    description.getDepend(),
                    description.getSoftDepend()
            ));
        }
        plugins.sort(Comparator.comparing(ShapedDebugSnapshot.PluginState::name, String.CASE_INSENSITIVE_ORDER));

        Map<String, Integer> environments = new TreeMap<>();
        Set<UUID> loadedWorldIds = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            environments.merge(world.getEnvironment().name(), 1, Integer::sum);
            loadedWorldIds.add(world.getUID());
        }
        Server server = plugin.getServer();
        return new ShapedDebugSnapshot(
                Instant.now(),
                plugin.getDescription().getVersion(),
                Bukkit.getName(),
                Bukkit.getVersion(),
                Bukkit.getBukkitVersion(),
                minecraftVersion(),
                Bukkit.getOnlineMode(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                Bukkit.getViewDistance(),
                Bukkit.getSimulationDistance(),
                Bukkit.isHardcore(),
                Bukkit.getAllowFlight(),
                Bukkit.hasWhitelist(),
                Bukkit.getDefaultGameMode().name(),
                Bukkit.getSpawnRadius(),
                Bukkit.getIdleTimeout(),
                pendingSchedulerTasks(),
                Bukkit.getWorlds().size(),
                environments,
                loadedWorldIds,
                plugin.schedulerName(),
                metric(server, "getTPS"),
                metric(server, "getAverageTickTime"),
                plugin.getConfigService().runtime().language(),
                plugin.getLanguageService().availableLocales(),
                languageCatalogState(),
                plugin.getLanguageService().remoteCatalogReference().orElse("unavailable"),
                plugin.getLanguageService().hasRemoteCatalogLocale(plugin.getConfigService().runtime().language()),
                sender instanceof Player ? "player" : "console-or-other",
                plugin.getConfigService().editableCopy(),
                plugin.getMetricsService() != null && plugin.getMetricsService().initialized(),
                plugin.getIntegrationService() != null && plugin.getIntegrationService().registered(),
                plugin.getPortalRegistry().portalCount(),
                plugin.getPortalRegistry().interiorCellCount(),
                plugin.getPortalRegistry().allRecords(),
                plugin.getPortalStats().snapshot(),
                plugins,
                plugin.getDataFolder().toPath().toAbsolutePath().normalize(),
                codeSource()
        );
    }

    private void writeAndUpload(CommandSender sender, ShapedDebugSnapshot snapshot) {
        try {
            String report = ShapedDebugReport.create(snapshot);
            Path file = write(report, snapshot.generatedAt());
            String relativePath = "debug/" + file.getFileName();
            feedback(sender, ShapedMessages.DEBUG_SAVED, MessageArgs.builder()
                    .untrusted("path", relativePath)
                    .build(), FeedbackTone.SUCCESS);
            if (snapshot.config().debug.uploadEnabled) {
                upload(sender, report, snapshot.pluginVersion());
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to create ShapedPortals diagnostic report", exception);
            feedback(sender, ShapedMessages.DEBUG_FAILED, FeedbackTone.FAILURE);
        } finally {
            active.set(false);
        }
    }

    private Path write(String report, Instant generatedAt) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("debug").toAbsolutePath().normalize();
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))) {
            throw new IOException("ShapedPortals debug path is not a regular directory: " + directory);
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("ShapedPortals debug path cannot be a symbolic link: " + directory);
        }
        String baseName = "shapedportals-debug-" + FILE_TIME.format(generatedAt);
        Path target = availableTarget(directory, baseName);
        AtomicFileIO.writeString(target, report);
        return target;
    }

    private Path availableTarget(Path directory, String baseName) throws IOException {
        Path target = directory.resolve(baseName + ".txt");
        for (int suffix = 1; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
            if (suffix > 999) {
                throw new IOException("Could not allocate a unique ShapedPortals diagnostic filename");
            }
            target = directory.resolve(baseName + "-" + suffix + ".txt");
        }
        return target;
    }

    private void upload(CommandSender sender, String report, String pluginVersion) {
        try {
            URI url = mclogs.publish(
                    report,
                    "ShapedPortals " + pluginVersion,
                    "ShapedPortals/" + pluginVersion
            );
            sendLink(sender, url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, "ShapedPortals diagnostic upload was interrupted", exception);
            feedback(sender, ShapedMessages.DEBUG_UPLOAD_FAILED, FeedbackTone.FAILURE);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to upload ShapedPortals diagnostic report to mclo.gs", exception);
            feedback(sender, ShapedMessages.DEBUG_UPLOAD_FAILED, FeedbackTone.FAILURE);
        }
    }

    private void sendLink(CommandSender sender, URI url) {
        MessageArgs arguments = MessageArgs.builder().untrusted("url", url).build();
        Runnable delivery = () -> {
            ComponentText output = ComponentText.markup(plugin.getLanguageService().renderPrefixed(
                    ShapedMessages.DEBUG_UPLOADED, arguments));
            ComponentText hover = ComponentText.markup(plugin.getLanguageService().render(ShapedMessages.DEBUG_LINK_HOVER));
            if (sender instanceof Player player) {
                ComponentMessenger.sendOpenUrl(player, output, url, hover);
            } else {
                ComponentMessenger.send(sender, output);
            }
        };
        scheduleFeedback(sender, delivery);
    }

    private void feedback(CommandSender sender, TextKey key, FeedbackTone tone) {
        feedback(sender, key, MessageArgs.empty(), tone);
    }

    private void feedback(
            CommandSender sender,
            TextKey key,
            MessageArgs arguments,
            FeedbackTone tone
    ) {
        scheduleFeedback(sender, () -> plugin.getPresentationService().command(sender, key, arguments, tone));
    }

    private void scheduleFeedback(CommandSender sender, Runnable delivery) {
        boolean scheduled;
        if (sender instanceof Player player) {
            scheduled = FoliaScheduler.runEntity(plugin, player, delivery, 0L, () -> {
            });
        } else {
            scheduled = FoliaScheduler.runGlobal(plugin, delivery);
        }
        if (!scheduled && !closed.get()) {
            plugin.getLogger().warning("Could not schedule ShapedPortals diagnostic command feedback");
        }
    }

    private String metric(Server server, String methodName) {
        try {
            Method method = server.getClass().getMethod(methodName);
            Object value = method.invoke(server);
            if (value == null) {
                return "unavailable";
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<String> values = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    values.add(formatMetric(Array.get(value, index)));
                }
                return String.join(", ", values);
            }
            return formatMetric(value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            return "unavailable";
        }
    }

    private String minecraftVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        int separator = bukkitVersion.indexOf('-');
        return separator < 0 ? bukkitVersion : bukkitVersion.substring(0, separator);
    }

    private int pendingSchedulerTasks() {
        try {
            return Bukkit.getScheduler().getPendingTasks().size();
        } catch (RuntimeException | LinkageError exception) {
            return -1;
        }
    }

    private String languageCatalogState() {
        return plugin.getLanguageService().remoteCatalogFailure()
                .map(failure -> "unavailable (" + failure.getClass().getSimpleName() + ")")
                .orElse("ready");
    }

    private String formatMetric(Object value) {
        if (value instanceof Number number) {
            return String.format(Locale.ROOT, "%.2f", number.doubleValue());
        }
        return Objects.toString(value, "unavailable")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    private Path codeSource() {
        try {
            if (plugin.getClass().getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            File source = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return source.toPath().toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Unable to resolve the ShapedPortals code source for diagnostics", exception);
            return null;
        }
    }
}
