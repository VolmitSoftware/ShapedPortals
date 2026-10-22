package com.volmit.shapedportals;

import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.diagnostics.BukkitDebugDump;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.volmit.shapedportals.command.CommandService;
import com.volmit.shapedportals.config.ConfigHotReloadService;
import com.volmit.shapedportals.config.ConfigRepository;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.debug.ShapedDebugContributor;
import com.volmit.shapedportals.gui.ConfigEditorGui;
import com.volmit.shapedportals.integration.ShapedPortalsIntegrationMetrics;
import com.volmit.shapedportals.integration.ShapedPortalsIntegrationService;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.metrics.MetricsService;
import com.volmit.shapedportals.portal.PortalEventListener;
import com.volmit.shapedportals.portal.PortalIntegrityService;
import com.volmit.shapedportals.portal.PortalNavigationService;
import com.volmit.shapedportals.portal.PortalRegistry;
import com.volmit.shapedportals.portal.PortalService;
import com.volmit.shapedportals.portal.PortalStats;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import com.volmit.shapedportals.presentation.PresentationService;
import com.volmit.shapedportals.util.SplashScreen;
import com.volmit.shapedportals.update.UpdateNotifier;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ShapedPortals extends JavaPlugin implements ReloadAware {
    private ConfigService configService;
    private LanguageService languageService;
    private ConfigHotReloadService hotReloadService;
    private PortalRegistry portalRegistry;
    private PortalStats portalStats;
    private PortalIntegrityService integrityService;
    private PortalNavigationService navigationService;
    private ConfigEditorGui configEditor;
    private PresentationService presentationService;
    private BukkitDebugDump debugDump;
    private MetricsService metricsService;
    private UpdateNotifier updateNotifier;
    private ShapedPortalsIntegrationService integrationService;
    private BukkitLanguageSwitcher languageSwitcher;
    private final Set<String> pendingLanguageActivations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public ShapedPortals() {
        getLogger().info("Loading libraries...");
        new SpigotApplicationBuilder(this).build();
        getLogger().info("Libraries loaded.");
    }

    @Override
    public void onEnable() {
        stopped.set(false);
        long startedNanos = System.nanoTime();
        try {
            configService = new ConfigService(getDataFolder());
            languageService = new LanguageService(getDataFolder(), getLogger());
            installInitialConfiguration();
            languageService.initializeSelections(
                    () -> configService.runtime().language(),
                    this::selectDefaultLanguage
            );
            metricsService = new MetricsService(this);
            metricsService.reconfigure(configService.runtime().metricsEnabled());
            presentationService = new PresentationService(this, configService, languageService);
            getServer().getPluginManager().registerEvents(presentationService, this);

            portalRegistry = new PortalRegistry(getDataFolder(),
                    exception -> getLogger().log(Level.SEVERE, "Portal registry error", exception));
            portalRegistry.load();
            portalStats = new PortalStats();
            integrationService = new ShapedPortalsIntegrationService(
                    this, new ShapedPortalsIntegrationMetrics(portalRegistry, portalStats));
            integrationService.register();
            integrityService = new PortalIntegrityService(this, configService, portalRegistry);
            navigationService = new PortalNavigationService(new PortalNavigationService.Dependencies(
                    this, configService, portalRegistry, integrityService, languageService));
            PortalService portalService = new PortalService(
                    this, configService, presentationService, portalRegistry, portalStats);
            PortalEventListener portalListener = new PortalEventListener(
                    portalService, portalRegistry, integrityService);
            getServer().getPluginManager().registerEvents(portalListener, this);

            configEditor = new ConfigEditorGui(this, configService, languageService, presentationService);
            getServer().getPluginManager().registerEvents(configEditor, this);
            languageSwitcher = BukkitLanguageSwitcher.register(
                    this,
                    languageService.selections(),
                    new BukkitLanguageSwitcher.Options(
                            "shapedportals",
                            "shapedportals.config",
                            ChatMenuStyle.theme(),
                            languageService.directorResolver(),
                            languageService.editorOptions(),
                            (sender, change) -> languageService.render(
                                    sender,
                                    ShapedMessages.CONFIG_SAVED,
                                    MessageArgs.builder()
                                            .untrusted("setting", change.key())
                                            .untrusted("old", compactChangeValue(change.before()))
                                            .untrusted("new", compactChangeValue(change.after()))
                                            .build()
                            )
                    )
            );
            debugDump = BukkitDebugDump.create(this, new BukkitDebugDump.Options(
                    () -> configService.runtime().debugUploadEnabled(),
                    new ShapedDebugContributor(this),
                    new BukkitDebugDump.Presentation(
                            "/shapedportals debug dump",
                            "/shapedportals debug",
                            ChatMenuStyle.theme(),
                            languageService::render
                    )
            ));
            new CommandService(this).register();

            integrityService.start();
            hotReloadService = new ConfigHotReloadService(this, configService);
            hotReloadService.start();
            languageService.remoteCatalogFailure().ifPresent(failure -> getLogger().log(
                    Level.WARNING,
                    "Remote language catalog is unavailable; code-owned English and installed local language files remain active",
                    failure
            ));
            requestConfiguredLanguage();
            updateNotifier = new UpdateNotifier(this);
            updateNotifier.reconfigure();
            long startupMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
            if (configService.runtime().splashScreen()) {
                SplashScreen.print(this);
            }
            getLogger().info("ShapedPortals ready in " + startupMillis + " ms with " + schedulerName()
                    + " scheduling and " + portalRegistry.portalCount() + " managed portals.");
        } catch (Throwable exception) {
            getLogger().log(Level.SEVERE, "ShapedPortals could not enable safely", exception);
            if (configService != null && configService.runtime().splashScreen()) {
                SplashScreen.print(this);
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public CompletionStage<Void> commitReload(PreUnloadReason reason) {
        stop();
        return CompletableFuture.completedFuture(null);
    }

    // BileTools disables the plugin again after commitReload, so teardown must tolerate a second call.
    private void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (updateNotifier != null) {
            updateNotifier.close();
        }
        pendingLanguageActivations.clear();
        HandlerList.unregisterAll(this);
        if (integrationService != null) {
            integrationService.unregister();
        }
        if (hotReloadService != null) {
            hotReloadService.close();
        }
        if (languageSwitcher != null) {
            languageSwitcher.close();
        }
        if (integrityService != null) {
            integrityService.stop();
        }
        if (configEditor != null) {
            configEditor.shutdown();
        }
        if (debugDump != null) {
            debugDump.close();
        }
        if (presentationService != null) {
            presentationService.shutdown();
        }
        if (languageService != null) {
            languageService.close();
        }
        if (metricsService != null) {
            metricsService.close();
        }
        SchedulerUtils.cancelPluginTasks(this);
        if (portalRegistry != null) {
            portalRegistry.close();
        }
    }

    public synchronized boolean reloadAll(boolean notifyConsole) {
        try {
            ConfigRepository.PreparedConfig preparedConfig = configService.prepareFromDisk();
            LanguageService.PreparedLanguage preparedLanguage = languageService.prepare(
                    preparedConfig.runtime().language());
            if (!preparedLanguage.selectionReady()) {
                requestPendingLanguage(preparedLanguage.locale());
                getLogger().warning("The requested language " + preparedLanguage.locale()
                        + " is not verified yet; the previous configuration and language remain active");
                return false;
            }
            configService.install(preparedConfig);
            languageService.install(preparedLanguage);
            configurationInstalled();
            if (notifyConsole) {
                getLogger().info("Reloaded ShapedPortals configuration and language files.");
            }
            return true;
        } catch (IOException exception) {
            getLogger().severe("ShapedPortals reload failed; the previous runtime configuration remains active. "
                    + rootCause(exception));
            return false;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE,
                    "ShapedPortals reload failed; the previous runtime configuration remains active", exception);
            return false;
        }
    }

    public void configurationInstalled() {
        if (updateNotifier != null) {
            updateNotifier.reconfigure();
        }
        if (metricsService != null) {
            metricsService.reconfigure(configService.runtime().metricsEnabled());
        }
        if (hotReloadService != null) {
            hotReloadService.requestReconfigure();
        }
        requestConfiguredLanguage();
    }

    public synchronized void applyConfigurationEdit(
            Consumer<ShapedPortalsConfig> mutation,
            LanguageService.PreparedLanguage preparedLanguage
    ) throws IOException {
        configService.update(mutation);
        if (preparedLanguage != null) {
            languageService.install(preparedLanguage);
        }
        configurationInstalled();
    }

    public synchronized void installPreparedLanguage(LanguageService.PreparedLanguage preparedLanguage) {
        languageService.install(preparedLanguage);
        configurationInstalled();
    }

    public String schedulerName() {
        return FoliaScheduler.isFoliaThreading(getServer()) ? "Folia region" : "Bukkit main-thread";
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public LanguageService getLanguageService() {
        return languageService;
    }

    public PortalRegistry getPortalRegistry() {
        return portalRegistry;
    }

    public PortalStats getPortalStats() {
        return portalStats;
    }

    public PortalNavigationService getNavigationService() {
        return navigationService;
    }

    public ConfigEditorGui getConfigEditor() {
        return configEditor;
    }

    public PresentationService getPresentationService() {
        return presentationService;
    }

    public BukkitDebugDump getDebugDump() {
        return debugDump;
    }

    public BukkitLanguageSwitcher getLanguageSwitcher() {
        return languageSwitcher;
    }

    public MetricsService getMetricsService() {
        return metricsService;
    }

    public ShapedPortalsIntegrationService getIntegrationService() {
        return integrationService;
    }

    private void installInitialConfiguration() throws IOException {
        ConfigRepository.PreparedConfig preparedConfig;
        try {
            preparedConfig = configService.prepareFromDisk();
        } catch (IOException exception) {
            getLogger().severe("config.toml could not be loaded. Portal creation is paused and existing"
                    + " portals are maintained with default settings until the file is fixed; the file was not changed. "
                    + rootCause(exception));
            preparedConfig = configService.creationPausedDefaults();
        }
        configService.install(preparedConfig);
        try {
            LanguageService.PreparedLanguage preparedLanguage = languageService.prepare(
                    preparedConfig.runtime().language());
            languageService.install(preparedLanguage);
        } catch (IOException | RuntimeException exception) {
            getLogger().log(
                    Level.WARNING,
                    "Configured language could not be loaded; ShapedPortals is continuing with code-owned English",
                    exception
            );
            languageService.install(languageService.englishFallback(preparedConfig.runtime().language()));
        }
    }

    // Operator file mistakes need the parser's line, not a stack trace.
    private static String rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return "Cause: " + (message == null || message.isBlank() ? cause.getClass().getSimpleName() : message);
    }

    private synchronized void selectDefaultLanguage(String locale, LocalizationSnapshot prepared) throws IOException {
        configService.update(config -> config.general.language = locale);
        languageService.install(new LanguageService.PreparedLanguage(
                locale,
                languageService.languageFile(locale),
                prepared,
                true
        ));
        if (hotReloadService != null) {
            hotReloadService.requestReconfigure();
        }
    }

    private void requestConfiguredLanguage() {
        if (languageService == null || configService == null) {
            return;
        }
        String locale = configService.runtime().language();
        languageService.requestRemote(locale, result -> remoteLanguageCompleted(locale, result));
    }

    private void remoteLanguageCompleted(String requestedLocale, RemoteLanguageCatalog.DownloadResult result) {
        if (!result.successful()) {
            getLogger().warning(languageFailureMessage(result, "the current verified language remains active"));
            return;
        }
        if (!FoliaScheduler.runGlobal(this, () -> activateDownloadedLanguage(requestedLocale))) {
            getLogger().warning("Downloaded locale " + requestedLocale
                    + " will activate on the next reload or restart because scheduling was unavailable.");
        }
    }

    private void activateDownloadedLanguage(String requestedLocale) {
        if (!isEnabled() || !requestedLocale.equalsIgnoreCase(configService.runtime().language())) {
            return;
        }
        reloadAll(false);
    }

    private void requestPendingLanguage(String locale) {
        if (!pendingLanguageActivations.add(locale)) {
            return;
        }
        RemoteLanguageCatalog.RequestState state = languageService.requestRemote(
                locale,
                result -> pendingLanguageCompleted(locale, result)
        );
        if (state == RemoteLanguageCatalog.RequestState.SCHEDULED
                || state == RemoteLanguageCatalog.RequestState.IN_FLIGHT) {
            return;
        }
        pendingLanguageActivations.remove(locale);
        if (state == RemoteLanguageCatalog.RequestState.CURRENT) {
            schedulePendingLanguageReload(locale);
            return;
        }
        getLogger().warning("Unable to prepare language file " + locale + " (" + state.name().toLowerCase()
                + "); the previous configuration and language remain active");
    }

    private void pendingLanguageCompleted(String locale, RemoteLanguageCatalog.DownloadResult result) {
        pendingLanguageActivations.remove(locale);
        if (!result.successful()) {
            getLogger().warning(languageFailureMessage(
                    result,
                    "the previous configuration and language remain active"
            ));
            return;
        }
        schedulePendingLanguageReload(locale);
    }

    private void schedulePendingLanguageReload(String locale) {
        if (!FoliaScheduler.runGlobal(this, () -> reloadAll(false))) {
            getLogger().warning("Downloaded locale " + locale
                    + " could not activate because scheduling was unavailable");
        }
    }

    private String languageFailureMessage(RemoteLanguageCatalog.DownloadResult result, String outcome) {
        Throwable failure = result.failure();
        String detail = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "unknown download failure"
                : failure.getMessage();
        String failureMessage = detail.startsWith("Unable to fetch language file ")
                ? detail
                : "Unable to fetch language file " + result.locale() + " from " + result.source() + ": " + detail;
        return failureMessage + "; " + outcome;
    }

    private static String compactChangeValue(String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "…";
    }

}
