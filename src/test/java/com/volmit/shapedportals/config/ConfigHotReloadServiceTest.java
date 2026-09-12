package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class ConfigHotReloadServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void watchesConfigurationAndDirectLocaleFilesIncludingDeletedPersonalFiles() {
        File config = temporaryDirectory.resolve("config.toml").toFile();
        File active = temporaryDirectory.resolve("languages/es_ES.toml").toFile();
        File inactive = temporaryDirectory.resolve("languages/fr_FR.toml").toFile();
        LanguageService language = new LanguageService(temporaryDirectory.toFile(), Logger.getAnonymousLogger());

        try {
            assertThat(ConfigHotReloadService.isManagedFile(config, config, language)).isTrue();
            assertThat(ConfigHotReloadService.isManagedFile(active, config, language)).isTrue();
            assertThat(ConfigHotReloadService.isManagedFile(inactive, config, language)).isTrue();
            assertThat(ConfigHotReloadService.isManagedFile(
                    temporaryDirectory.resolve("languages/nested/fr_FR.toml").toFile(), config, language)).isFalse();
            assertThat(ConfigHotReloadService.isManagedFile(
                    temporaryDirectory.resolve("languages/language-preferences.properties").toFile(), config, language)).isFalse();
            assertThat(ConfigHotReloadService.isManagedFile(null, config, language)).isFalse();
        } finally {
            language.close();
        }
    }

    @Test
    void personalFileWatcherRetainsMalformedTranslationsAndFallsBackOnDeletionUntilRestored() throws Exception {
        LanguageService language = new LanguageService(temporaryDirectory.toFile(), Logger.getAnonymousLogger());
        language.install(language.prepare("en_US"));
        Path personal = write("languages/fr_FR.toml", "[runtime]\nprefix = 'PERSONAL'\n").toPath();
        language.prepare("fr_FR");
        PluginLanguageService selections = language.initializeSelections(() -> "en_US", (locale, snapshot) -> {
            throw new AssertionError("Personal file edits cannot change the server default");
        });
        UUID player = UUID.randomUUID();
        selections.selectPlayer(player, "fr_FR").get(5L, TimeUnit.SECONDS);
        Path preferences = temporaryDirectory.resolve("languages/language-preferences.properties");
        String savedPreferences = Files.readString(preferences);
        ConfigService config = mock(ConfigService.class);
        RuntimeConfig runtime = mock(RuntimeConfig.class);
        when(config.runtime()).thenReturn(runtime);
        File configuration = write("config.toml", "enabled = true\n");
        when(config.configFile()).thenReturn(configuration);
        when(config.dataFolder()).thenReturn(temporaryDirectory.toFile());
        when(runtime.hotReloadEnabled()).thenReturn(true);
        when(runtime.hotReloadPollMillis()).thenReturn(250L);
        when(runtime.hotReloadCooldownMillis()).thenReturn(250L);
        ShapedPortals plugin = mock(ShapedPortals.class);
        when(plugin.getLanguageService()).thenReturn(language);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        AtomicInteger rejected = new AtomicInteger();
        doAnswer(invocation -> rejected.incrementAndGet()).when(logger)
                .warning("Rejected ShapedPortals file changes; the last known good settings remain active.");
        AtomicInteger reloads = new AtomicInteger();
        try (ConfigHotReloadService watcher = new ConfigHotReloadService(plugin, config)) {
            when(plugin.reloadAll(false)).thenAnswer(invocation -> {
                language.install(language.prepare("en_US"));
                watcher.requestReconfigure();
                reloads.incrementAndGet();
                return true;
            });
            watcher.start();
            Files.writeString(personal, "[runtime]\nprefix = 'LIVE'\n[runtime.permission]\ndenied = 'PERSONAL ONLY'\n");
            await(() -> playerPrefix(language, player).equals("LIVE"));

            Files.writeString(personal, "[runtime\n");
            await(() -> rejected.get() > 0);
            assertThat(playerPrefix(language, player)).isEqualTo("LIVE");
            assertThat(Files.readString(personal)).isEqualTo("[runtime\n");

            Files.writeString(language.activeFile().toPath(),
                    "[runtime]\nprefix = 'SERVER'\n[runtime.permission]\ndenied = 'SERVER ONLY'\n");
            await(() -> language.render(ShapedMessages.PREFIX).plain().equals("SERVER"));
            assertThat(playerPrefix(language, player)).isEqualTo("LIVE");
            assertThat(LanguageAudience.call(player, () -> language.render(ShapedMessages.NO_PERMISSION).plain()))
                    .isEqualTo("PERSONAL ONLY");

            String partial = "[runtime]\nprefix = 'PARTIAL'\n";
            Files.writeString(personal, partial);
            await(() -> playerPrefix(language, player).equals("PARTIAL"));
            assertThat(LanguageAudience.call(player, () -> language.render(ShapedMessages.NO_PERMISSION).plain()))
                    .isEqualTo("PARTIAL › You do not have permission to do that.");

            Files.delete(personal);
            await(() -> playerPrefix(language, player).equals("ShapedPortals"));
            assertThat(personal).doesNotExist();
            assertThat(selections.playerLocale(player)).contains("fr_FR");
            assertThat(Files.readString(preferences)).isEqualTo(savedPreferences);
            assertThat(language.activeFile()).isEqualTo(language.languageFile("en_US"));
            assertThat(language.render(ShapedMessages.PREFIX).plain()).isEqualTo("SERVER");

            int previousReloads = reloads.get();
            Files.writeString(configuration.toPath(), "enabled = false\n");
            await(() -> reloads.get() > previousReloads);
            assertThat(playerPrefix(language, player)).isEqualTo("ShapedPortals");
            assertThat(personal).doesNotExist();

            Files.writeString(personal, partial);
            await(() -> playerPrefix(language, player).equals("PARTIAL"));
            assertThat(Files.readString(preferences)).isEqualTo(savedPreferences);
            assertThat(selections.playerLocale(player)).contains("fr_FR");
        } finally {
            language.close();
        }
    }

    @Test
    void restoresLanguageEditsSavedBeforeTheQueuedWatcherReconfiguration() throws Exception {
        LanguageService language = new LanguageService(temporaryDirectory.toFile(), Logger.getAnonymousLogger());
        language.install(language.prepare("en_US"));
        Path file = language.activeFile().toPath();
        String original = Files.readString(file);
        String edited = original.replace(ShapedMessages.PREFIX.english(), "&6Local");
        PluginLanguageService selections = language.initializeSelections(() -> "en_US", (locale, snapshot) -> {});
        UUID player = UUID.randomUUID();
        selections.selectPlayer(player, "en_US").get(5L, TimeUnit.SECONDS);
        ConfigService config = mock(ConfigService.class);
        RuntimeConfig runtime = mock(RuntimeConfig.class);
        when(config.runtime()).thenReturn(runtime);
        when(config.configFile()).thenReturn(write("config.toml", "enabled = true\n"));
        when(config.dataFolder()).thenReturn(temporaryDirectory.toFile());
        when(runtime.hotReloadEnabled()).thenReturn(true);
        when(runtime.hotReloadPollMillis()).thenReturn(250L);
        when(runtime.hotReloadCooldownMillis()).thenReturn(250L);
        ShapedPortals plugin = mock(ShapedPortals.class);
        when(plugin.getLanguageService()).thenReturn(language);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ConfigHotReloadService watcher = new ConfigHotReloadService(plugin, config);
        AtomicInteger reloads = new AtomicInteger();
        CountDownLatch restored = new CountDownLatch(1);
        when(plugin.reloadAll(false)).thenAnswer(invocation -> {
            language.install(language.prepare("en_US"));
            watcher.requestReconfigure();
            if (reloads.incrementAndGet() == 1) {
                assertThat(LanguageAudience.call(player, () -> language.render(ShapedMessages.PREFIX).plain())).isEqualTo("Local");
                Files.writeString(file, original);
            } else {
                restored.countDown();
            }
            return true;
        });
        try {
            watcher.start();
            Files.writeString(file, edited);

            assertThat(restored.await(8L, TimeUnit.SECONDS)).isTrue();
            assertThat(reloads.get()).isEqualTo(2);
            assertThat(LanguageAudience.call(player, () -> language.render(ShapedMessages.PREFIX).plain())).isEqualTo("ShapedPortals");
            assertThat(selections.playerLocale(player)).contains("en_US");
        } finally {
            watcher.close();
            language.close();
        }
    }

    @Test
    void selfWriteSnapshotDoesNotStartOrAnnounceAReload() throws IOException {
        File config = write("config.toml", "language = \"en_US\"\n");
        File active = write("languages/en_US.toml", "prefix = \"English\"\n");
        ConfigHotloadEngine engine = engine(config, active);
        try {
            engine.configure(250L, 250L, List.of(config, active), List.of(temporaryDirectory.toFile()));
            String updated = "language = \"es_ES\"\n";
            Files.writeString(config.toPath(), updated, StandardCharsets.UTF_8);
            engine.noteSelfWrite(config, updated);
            AtomicInteger reloads = new AtomicInteger();

            ConfigHotReloadService.ReloadOutcome outcome = ConfigHotReloadService.processSnapshots(
                    engine,
                    Set.of(new ConfigHotloadEngine.StableContentSnapshot(config, "updated", updated, 1L)),
                    true,
                    () -> {
                        reloads.incrementAndGet();
                        return true;
                    }
            );

            assertThat(outcome).isEqualTo(ConfigHotReloadService.ReloadOutcome.NOT_ATTEMPTED);
            assertThat(reloads.get()).isZero();
        } finally {
            engine.clear();
        }
    }

    @Test
    void externalBatchReloadsOnce() throws IOException {
        File config = write("config.toml", "language = \"en_US\"\n");
        File active = write("languages/en_US.toml", "prefix = \"English\"\n");
        ConfigHotloadEngine engine = engine(config, active);
        try {
            engine.configure(250L, 250L, List.of(config, active), List.of(temporaryDirectory.toFile()));
            String updatedConfig = "language = \"es_ES\"\n";
            String updatedLanguage = "prefix = \"Spanish\"\n";
            Files.writeString(config.toPath(), updatedConfig, StandardCharsets.UTF_8);
            Files.writeString(active.toPath(), updatedLanguage, StandardCharsets.UTF_8);
            AtomicInteger reloads = new AtomicInteger();

            ConfigHotReloadService.ReloadOutcome outcome = ConfigHotReloadService.processSnapshots(
                    engine,
                    Set.of(
                            new ConfigHotloadEngine.StableContentSnapshot(config, "config", updatedConfig, 0L),
                            new ConfigHotloadEngine.StableContentSnapshot(active, "language", updatedLanguage, 0L)
                    ),
                    true,
                    () -> {
                        reloads.incrementAndGet();
                        return true;
                    }
            );

            assertThat(outcome).isEqualTo(ConfigHotReloadService.ReloadOutcome.APPLIED);
            assertThat(reloads.get()).isOne();
        } finally {
            engine.clear();
        }
    }

    private ConfigHotloadEngine engine(File config, File active) {
        return new ConfigHotloadEngine(
                file -> file.equals(config) || file.equals(active),
                () -> List.of(config, active),
                this::read,
                content -> content
        );
    }

    private File write(String relativePath, String content) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toFile();
    }

    private String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private String playerPrefix(LanguageService language, UUID player) {
        return LanguageAudience.call(player, () -> language.render(ShapedMessages.PREFIX).plain());
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
