package com.volmit.shapedportals.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PluginMetadataTest {
    @Test
    void declaresPortalPermissionsAndAliases() throws IOException {
        InputStream input = Objects.requireNonNull(
                PluginMetadataTest.class.getClassLoader().getResourceAsStream("plugin.yml"));
        YamlConfiguration metadata;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            metadata = YamlConfiguration.loadConfiguration(reader);
        }

        assertThat(metadata.getString("permissions.shapedportals.portals.default")).isEqualTo("op");
        assertThat(metadata.getString("permissions.shapedportals.teleport.default")).isEqualTo("op");
        assertThat(metadata.getString("permissions.shapedportals.teleport.unsafe.default")).isEqualTo("op");
        assertThat(metadata.getBoolean("permissions.shapedportals.create.default")).isTrue();
        assertThat(metadata.contains("permissions.shapedportals.reload")).isFalse();
        assertThat(metadata.getStringList("commands.shapedportals.aliases"))
                .containsExactly("shapedportal", "sp");
    }
}
