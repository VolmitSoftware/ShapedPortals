package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigHotReloadServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void watchesOnlyTheConfigurationAndActiveLanguage() {
        File config = temporaryDirectory.resolve("config.toml").toFile();
        File active = temporaryDirectory.resolve("languages/es_ES.toml").toFile();
        File inactive = temporaryDirectory.resolve("languages/fr_FR.toml").toFile();

        assertThat(ConfigHotReloadService.isManagedFile(config, config, active)).isTrue();
        assertThat(ConfigHotReloadService.isManagedFile(active, config, active)).isTrue();
        assertThat(ConfigHotReloadService.isManagedFile(inactive, config, active)).isFalse();
        assertThat(ConfigHotReloadService.isManagedFile(null, config, active)).isFalse();
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
                file -> ConfigHotReloadService.isManagedFile(file, config, active),
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
}
