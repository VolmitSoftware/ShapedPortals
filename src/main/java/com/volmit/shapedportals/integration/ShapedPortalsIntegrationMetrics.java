package com.volmit.shapedportals.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import com.volmit.shapedportals.portal.PortalRegistry;
import com.volmit.shapedportals.portal.PortalStats;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ShapedPortalsIntegrationMetrics {
    private final PortalRegistry portalRegistry;
    private final PortalStats portalStats;

    public ShapedPortalsIntegrationMetrics(PortalRegistry portalRegistry, PortalStats portalStats) {
        this.portalRegistry = Objects.requireNonNull(portalRegistry, "portalRegistry");
        this.portalStats = Objects.requireNonNull(portalStats, "portalStats");
    }

    public Set<IntegrationMetricDescriptor> descriptors() {
        Set<IntegrationMetricDescriptor> descriptors = new LinkedHashSet<>();
        for (String key : IntegrationMetricSchema.shapedPortalsKeys()) {
            descriptors.add(IntegrationMetricSchema.descriptor(key));
        }
        return Set.copyOf(descriptors);
    }

    public Map<String, IntegrationMetricSample> sample(Set<String> metricKeys) {
        Set<String> requested = metricKeys == null || metricKeys.isEmpty()
                ? IntegrationMetricSchema.shapedPortalsKeys()
                : metricKeys;
        long sampledAt = System.currentTimeMillis();
        PortalStats.Snapshot stats = portalStats.snapshot();
        Map<String, IntegrationMetricSample> samples = new HashMap<>(requested.size());
        for (String key : requested) {
            samples.put(key, sample(key, stats, sampledAt));
        }
        return Map.copyOf(samples);
    }

    private IntegrationMetricSample sample(String key, PortalStats.Snapshot stats, long sampledAt) {
        return switch (key) {
            case IntegrationMetricSchema.SHAPEDPORTALS_MANAGED_PORTALS ->
                    available(key, portalRegistry.portalCount(), sampledAt);
            case IntegrationMetricSchema.SHAPEDPORTALS_INTERIOR_CELLS ->
                    available(key, portalRegistry.interiorCellCount(), sampledAt);
            case IntegrationMetricSchema.SHAPEDPORTALS_CREATION_ATTEMPTS_TOTAL ->
                    available(key, stats.attempts(), sampledAt);
            case IntegrationMetricSchema.SHAPEDPORTALS_CREATED_PORTALS_TOTAL ->
                    available(key, stats.created(), sampledAt);
            case IntegrationMetricSchema.SHAPEDPORTALS_REJECTED_ATTEMPTS_TOTAL ->
                    available(key, stats.rejected(), sampledAt);
            case IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT -> stats.attempts() == 0L
                    ? IntegrationMetricSample.unavailable(
                    IntegrationMetricSchema.descriptor(key), "no-attempts", sampledAt)
                    : available(key, stats.created() * 100D / stats.attempts(), sampledAt);
            default -> IntegrationMetricSample.unavailable(
                    IntegrationMetricSchema.descriptor(key), "unsupported-key", sampledAt);
        };
    }

    private IntegrationMetricSample available(String key, double value, long sampledAt) {
        return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, sampledAt);
    }
}
