package com.volmit.shapedportals.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void inGameEditsApplyWhenTheFileMatchesTheAcceptedContent() throws IOException {
        ConfigService service = new ConfigService(temporaryDirectory.toFile());
        service.install(service.prepareFromDisk());

        service.update(config -> config.portal.maximumInteriorBlocks = 300);
        service.update(config -> config.portal.maximumInteriorBlocks = 400);

        assertThat(Files.readString(temporaryDirectory.resolve("config.toml"), StandardCharsets.UTF_8))
                .contains("maximumInteriorBlocks = 400");
    }

    @Test
    void inGameEditsDoNotOverwriteUnappliedFileChanges() throws IOException {
        ConfigService service = new ConfigService(temporaryDirectory.toFile());
        service.install(service.prepareFromDisk());
        Path file = temporaryDirectory.resolve("config.toml");
        String operatorEdit = Files.readString(file, StandardCharsets.UTF_8) + "\n# operator note\n";
        Files.writeString(file, operatorEdit, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.update(config -> config.general.failureFeedback = false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not applied");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(operatorEdit);
    }

    @Test
    void unreadableFileFallsBackWithCreationPausedAndIsNeverOverwritten() throws IOException {
        Path file = temporaryDirectory.resolve("config.toml");
        String broken = "[portal\nmaximumInteriorBlocks = ";
        Files.writeString(file, broken, StandardCharsets.UTF_8);
        ConfigService service = new ConfigService(temporaryDirectory.toFile());

        assertThatThrownBy(service::prepareFromDisk).isInstanceOf(IOException.class);
        service.install(service.creationPausedDefaults());

        assertThat(service.runtime().enabled()).isFalse();
        assertThat(service.runtime().integrityEnabled()).isTrue();
        assertThatThrownBy(() -> service.update(config -> config.general.enabled = true))
                .isInstanceOf(IOException.class);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(broken);
    }
}
