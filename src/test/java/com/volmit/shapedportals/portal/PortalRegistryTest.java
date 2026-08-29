package com.volmit.shapedportals.portal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.shapedportals.geometry.PortalAxis;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalRegistryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndLoadsAuthoritativeRecords() throws IOException {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        PortalRecord record = record();
        PortalRegistry first = new PortalRegistry(temporaryDirectory.toFile(), failures::add);
        first.load();
        assertThat(first.register(record)).isTrue();
        first.close();

        PortalRegistry second = new PortalRegistry(temporaryDirectory.toFile(), failures::add);
        second.load();

        assertThat(second.portalCount()).isEqualTo(1);
        assertThat(second.interiorCellCount()).isEqualTo(2);
        assertThat(second.get(record.id())).isEqualTo(record);
        second.close();
        assertThat(failures).isEmpty();
    }

    @Test
    void failedLoadDoesNotOverwriteInvalidStoreOnClose() throws IOException {
        Path store = temporaryDirectory.resolve("portals.json");
        String malformed = "{broken";
        Files.writeString(store, malformed, StandardCharsets.UTF_8);
        PortalRegistry registry = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });

        assertThatThrownBy(registry::load).isInstanceOf(IOException.class);
        registry.close();
        assertThat(Files.readString(store, StandardCharsets.UTF_8)).isEqualTo(malformed);
    }

    @Test
    void rejectsOverlappingRecordsWithoutRewritingTheStore() throws IOException {
        PortalRecord first = record();
        PortalRecord second = new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                first.worldId(),
                first.worldName(),
                first.axis(),
                first.anchor(),
                first.interior(),
                first.frame(),
                first.frameMaterialSnapshot(),
                first.createdAtEpochMillis(),
                first.creator()
        );
        String invalid = new Gson().toJson(Map.of(
                "schemaVersion", PortalRecord.CURRENT_SCHEMA_VERSION,
                "portals", List.of(first, second)
        ));
        Path store = temporaryDirectory.resolve("portals.json");
        Files.writeString(store, invalid, StandardCharsets.UTF_8);
        PortalRegistry registry = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });

        assertThatThrownBy(registry::load)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("overlapping");
        registry.close();
        assertThat(Files.readString(store, StandardCharsets.UTF_8)).isEqualTo(invalid);
    }

    @Test
    void loadsExistingRecordsWithoutAFrameMaterialSnapshot() throws IOException {
        JsonObject portal = JsonParser.parseString(new Gson().toJson(record())).getAsJsonObject();
        portal.remove("frameMaterialSnapshot");
        JsonArray portals = new JsonArray();
        portals.add(portal);
        JsonObject store = new JsonObject();
        store.addProperty("schemaVersion", PortalRecord.CURRENT_SCHEMA_VERSION);
        store.add("portals", portals);
        Files.writeString(temporaryDirectory.resolve("portals.json"), store.toString(), StandardCharsets.UTF_8);
        PortalRegistry registry = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });

        registry.load();

        PortalRecord loaded = registry.allRecords().get(0);
        assertThat(loaded.frameMaterialSnapshot()).isEmpty();
        registry.close();
    }

    @Test
    void updatesAndPersistsAnExistingPortalsFrameSnapshot() throws IOException {
        PortalRecord legacy = record().withFrameMaterialSnapshot(List.of());
        PortalRegistry registry = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });
        registry.load();
        assertThat(registry.register(legacy)).isTrue();

        PortalRecord updated = registry.updateFrameMaterialSnapshot(
                legacy, List.of(Material.DIRT, Material.DIRT, Material.DIRT,
                        Material.DIRT, Material.DIRT, Material.DIRT));

        assertThat(updated).isNotNull();
        assertThat(updated.frameMaterialSnapshot()).containsOnly(Material.DIRT);
        assertThat(registry.updateFrameMaterialSnapshot(legacy,
                List.of(Material.OBSIDIAN, Material.OBSIDIAN, Material.OBSIDIAN,
                        Material.OBSIDIAN, Material.OBSIDIAN, Material.OBSIDIAN))).isNull();
        PortalRecord refreshed = registry.updateFrameMaterialSnapshot(updated,
                List.of(Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.GRASS_BLOCK,
                        Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.GRASS_BLOCK));
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.frameMaterialSnapshot()).containsOnly(Material.GRASS_BLOCK);
        registry.close();

        PortalRegistry reloaded = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });
        reloaded.load();
        assertThat(reloaded.get(legacy.id()).frameMaterialSnapshot()).containsOnly(Material.GRASS_BLOCK);
        reloaded.close();
    }

    private PortalRecord record() {
        UUID worldId = UUID.randomUUID();
        return new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                worldId,
                "world",
                PortalAxis.X,
                new BlockPosition(1, 64, 1),
                List.of(new BlockPosition(1, 64, 1), new BlockPosition(2, 64, 1)),
                List.of(
                        new BlockPosition(0, 64, 1),
                        new BlockPosition(3, 64, 1),
                        new BlockPosition(1, 63, 1),
                        new BlockPosition(2, 63, 1),
                        new BlockPosition(1, 65, 1),
                        new BlockPosition(2, 65, 1)
                ),
                List.of(
                        Material.OBSIDIAN,
                        Material.OBSIDIAN,
                        Material.OBSIDIAN,
                        Material.OBSIDIAN,
                        Material.OBSIDIAN,
                        Material.OBSIDIAN
                ),
                1L,
                "tester"
        );
    }
}
