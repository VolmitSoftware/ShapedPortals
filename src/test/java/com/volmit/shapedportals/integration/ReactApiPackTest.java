package com.volmit.shapedportals.integration;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReactApiPackTest {
    @Test
    void publishesEveryCanonicalMetricThroughARestrictedIntegrationPack() {
        InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/react-api-packs/shapedportals-runtime.toml"),
                "ShapedPortals React API pack"
        );
        Toml pack = new Toml().read(input);

        assertThat(pack.getString("schema")).isEqualTo("react.plugin-api/v1");
        assertThat(pack.getString("id")).isEqualTo("volmit.shapedportals-runtime");
        assertThat(pack.getString("targetPlugin")).isEqualTo("ShapedPortals");
        assertThat(pack.<String>getList("targetVersions")).containsExactly("2.*");
        assertThat(pack.getBoolean("trusted")).isFalse();

        List<Toml> metrics = pack.getTables("metrics");
        Set<String> sourceKeys = new HashSet<>();
        int rateTransforms = 0;
        for (Toml metric : metrics) {
            Toml source = metric.getTable("source");
            assertThat(source.getString("type")).isEqualTo("integration");
            assertThat(source.getString("pluginId")).isEqualTo("shapedportals");
            assertThat(source.getBoolean("foliaSafe")).isTrue();
            assertThat(metric.getLong("sampleEveryMs")).isBetween(1_000L, 10_000L);
            assertThat(metric.getLong("staleAfterMs")).isBetween(
                    metric.getLong("sampleEveryMs"), 60_000L);
            sourceKeys.add(source.getString("key"));
            Toml transform = metric.getTable("transform");
            if (transform != null && "delta-per-second".equals(transform.getString("mode"))) {
                rateTransforms++;
            }
        }

        assertThat(metrics).hasSize(6);
        assertThat(sourceKeys).isEqualTo(IntegrationMetricSchema.shapedPortalsKeys());
        assertThat(rateTransforms).isEqualTo(3);
    }
}
