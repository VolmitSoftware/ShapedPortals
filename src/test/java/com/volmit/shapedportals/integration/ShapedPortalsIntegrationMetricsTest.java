package com.volmit.shapedportals.integration;

import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.portal.BlockPosition;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalRegistry;
import com.volmit.shapedportals.portal.PortalStats;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShapedPortalsIntegrationMetricsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void samplesRegistryAndSessionCreationMetrics() throws IOException {
        PortalRegistry registry = registry();
        PortalStats stats = new PortalStats();
        ShapedPortalsIntegrationMetrics metrics = new ShapedPortalsIntegrationMetrics(registry, stats);
        registry.register(record(), false);
        stats.attempted();
        stats.created();
        stats.attempted();
        stats.rejected(PortalStats.RejectionReason.OPEN_FRAME);

        Map<String, IntegrationMetricSample> samples = metrics.sample(Set.of());

        assertThat(samples).hasSize(6);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_MANAGED_PORTALS, 1D);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_INTERIOR_CELLS, 2D);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_CREATION_ATTEMPTS_TOTAL, 2D);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_CREATED_PORTALS_TOTAL, 1D);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_REJECTED_ATTEMPTS_TOTAL, 1D);
        assertAvailable(samples, IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT, 50D);
        assertThat(metrics.descriptors()).extracting(descriptor -> descriptor.tags().get("plugin"))
                .containsOnly("shapedportals");
        registry.close();
    }

    @Test
    void reportsNoSuccessPercentageBeforeTheFirstAttemptAndHonorsRequestedKeys() throws IOException {
        PortalRegistry registry = registry();
        ShapedPortalsIntegrationMetrics metrics = new ShapedPortalsIntegrationMetrics(registry, new PortalStats());

        Map<String, IntegrationMetricSample> samples = metrics.sample(Set.of(
                IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT
        ));

        assertThat(samples).containsOnlyKeys(IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT);
        IntegrationMetricSample sample = samples.get(IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT);
        assertThat(sample.available()).isFalse();
        assertThat(sample.message()).isEqualTo("no-attempts");
        assertThat(sample.sampledAtMs()).isPositive();
        registry.close();
    }

    private PortalRegistry registry() throws IOException {
        PortalRegistry registry = new PortalRegistry(temporaryDirectory.toFile(), ignored -> {
        });
        registry.load();
        return registry;
    }

    private PortalRecord record() {
        return new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "world",
                PortalAxis.X,
                new BlockPosition(1, 64, 1),
                List.of(new BlockPosition(1, 64, 1), new BlockPosition(2, 64, 1)),
                List.of(new BlockPosition(0, 64, 1), new BlockPosition(3, 64, 1)),
                List.of(Material.OBSIDIAN, Material.OBSIDIAN),
                1L,
                "tester"
        );
    }

    private void assertAvailable(Map<String, IntegrationMetricSample> samples, String key, double value) {
        IntegrationMetricSample sample = samples.get(key);
        assertThat(sample.available()).isTrue();
        assertThat(sample.numericValue()).isEqualTo(value);
        assertThat(sample.descriptor()).isEqualTo(IntegrationMetricSchema.descriptor(key));
    }
}
