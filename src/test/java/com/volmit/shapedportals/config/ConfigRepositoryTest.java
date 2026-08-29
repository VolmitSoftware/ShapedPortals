package com.volmit.shapedportals.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndRoundTripsCanonicalToml() throws IOException {
        ConfigRepository repository = new ConfigRepository(temporaryDirectory.toFile());

        ConfigRepository.PreparedConfig initial = repository.load();
        ShapedPortalsConfig edited = initial.source().copy();
        edited.portal.maximumInteriorBlocks = 512;
        edited.metrics.enabled = false;
        repository.save(edited);
        ConfigRepository.PreparedConfig reloaded = repository.load();

        assertThat(reloaded.runtime().scanLimits().maximumInteriorBlocks()).isEqualTo(512);
        assertThat(reloaded.runtime().metricsEnabled()).isFalse();
        assertThat(Files.readString(temporaryDirectory.resolve("config.toml")))
                .contains("maximumInteriorBlocks = 512")
                .contains("notifyOperators = true")
                .contains("[metrics]")
                .contains("enabled = false")
                .contains("[presentation]")
                .contains("commandOverlays = [\"ACTION_BAR\"]")
                .contains("creationSoundType = \"minecraft:block.end_portal.spawn\"");
    }

    @Test
    void malformedConfigIsPreserved() throws IOException {
        Path file = temporaryDirectory.resolve("config.toml");
        String malformed = "[portal\nmaximumInteriorBlocks = nope";
        Files.writeString(file, malformed, StandardCharsets.UTF_8);
        ConfigRepository repository = new ConfigRepository(temporaryDirectory.toFile());

        assertThatThrownBy(repository::load).isInstanceOf(IOException.class);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(malformed);
    }
}
