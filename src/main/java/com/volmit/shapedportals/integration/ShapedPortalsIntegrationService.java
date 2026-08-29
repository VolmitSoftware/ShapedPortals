package com.volmit.shapedportals.integration;

import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ShapedPortalsIntegrationService implements IntegrationServiceContract {
    private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
            new IntegrationProtocolVersion(1, 0),
            new IntegrationProtocolVersion(1, 1)
    );
    private static final Set<String> CAPABILITIES = Set.of("handshake", "heartbeat", "metrics");

    private final ShapedPortals plugin;
    private final ShapedPortalsIntegrationMetrics metrics;
    private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);
    private volatile boolean registered;

    public ShapedPortalsIntegrationService(ShapedPortals plugin, ShapedPortalsIntegrationMetrics metrics) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public void register() {
        if (registered) {
            return;
        }
        Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, plugin, ServicePriority.Normal);
        registered = true;
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
        registered = false;
    }

    public boolean registered() {
        return registered;
    }

    @Override
    public String pluginId() {
        return "shapedportals";
    }

    @Override
    public String pluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public Set<IntegrationProtocolVersion> supportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

    @Override
    public Set<String> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public Set<IntegrationMetricDescriptor> metricDescriptors() {
        return metrics.descriptors();
    }

    @Override
    public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
        long now = System.currentTimeMillis();
        if (request == null) {
            return new IntegrationHandshakeResponse(
                    pluginId(), pluginVersion(), false, null,
                    SUPPORTED_PROTOCOLS, CAPABILITIES, "missing request", now
            );
        }

        Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
                SUPPORTED_PROTOCOLS,
                request.supportedProtocols()
        );
        if (negotiated.isEmpty()) {
            return new IntegrationHandshakeResponse(
                    pluginId(), pluginVersion(), false, null,
                    SUPPORTED_PROTOCOLS, CAPABILITIES, "no-common-protocol", now
            );
        }

        negotiatedProtocol = negotiated.get();
        return new IntegrationHandshakeResponse(
                pluginId(), pluginVersion(), true, negotiatedProtocol,
                SUPPORTED_PROTOCOLS, CAPABILITIES, "ok", now
        );
    }

    @Override
    public IntegrationHeartbeat heartbeat() {
        return new IntegrationHeartbeat(negotiatedProtocol, true, System.currentTimeMillis(), "ok");
    }

    @Override
    public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
        return metrics.sample(metricKeys);
    }
}
