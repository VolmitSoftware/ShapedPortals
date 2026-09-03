package com.volmit.shapedportals.debug;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.portal.BlockPosition;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class ShapedDebugReportTest {
    @Test
    void createsAUsefulSanitizedReportWithoutPrivateRuntimeSources() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.portal.allowedWorlds = List.of("private-world-name");
        config.portal.deniedWorlds = List.of("another-private-world");
        BlockPosition anchor = new BlockPosition(4, 64, 8);
        PortalRecord portal = new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "private-world-name",
                PortalAxis.X,
                anchor,
                List.of(anchor),
                List.of(new BlockPosition(4, 63, 8)),
                List.of(Material.OBSIDIAN),
                1_700_000_000_000L,
                "private-player-name"
        );
        ShapedDebugSnapshot snapshot = new ShapedDebugSnapshot(
                Instant.parse("2026-08-28T12:00:00Z"),
                "2.0.0",
                "Paper\nInjected",
                "Paper 1.21.11",
                "1.21.11-R0.1-SNAPSHOT",
                "1.21.11",
                true,
                2,
                100,
                10,
                10,
                false,
                true,
                true,
                "SURVIVAL",
                10,
                0,
                7,
                3,
                Map.of("NORMAL", 1, "NETHER", 1, "THE_END", 1),
                Set.of(portal.worldId()),
                "Folia region",
                "20.00, 19.98, 19.95",
                "12.34",
                "en_US",
                List.of("en_US"),
                "ready",
                "30e3b4eea5852ffa879d8371d556cd4b9b9fdbe7",
                false,
                "player",
                config,
                true,
                true,
                4,
                18,
                List.of(portal),
                new PortalStats.Snapshot(10L, 4L, 6L, Map.of(
                        PortalStats.RejectionReason.OPEN_FRAME, 4L,
                        PortalStats.RejectionReason.EVENT_CANCELLED, 2L
                )),
                List.of(new ShapedDebugSnapshot.PluginState(
                        "ShapedPortals", "2.0.0", true, "com.volmit.shapedportals.ShapedPortals",
                        List.of("Volmit Software"), "POSTWORLD", "1.20",
                        List.of(), List.of("PlaceholderAPI")
                )),
                Path.of("."),
                null
        );

        String report = ShapedDebugReport.create(snapshot);

        assertThat(report)
                .contains("Format: 4")
                .contains("Version: 2.0.0")
                .contains("Implementation: Paper Injected")
                .contains("Managed portals: 4")
                .contains("Creation attempts: 10")
                .contains("Rejected open frame: 4")
                .contains("Rejected event cancelled: 2")
                .contains("Pending scheduler tasks: 7")
                .contains("Language catalog: ready")
                .contains("Language source reference: 30e3b4eea5852ffa879d8371d556cd4b9b9fdbe7")
                .contains("Portal registry details")
                .contains("Records in loaded worlds: 1")
                .contains("Records in unavailable worlds: 0")
                .contains("00000000-0000-0000-0000-000000000001 | schema=1 | axis=X | type=NETHER | interior=1 | frame=1")
                .contains("debug.uploadEnabled: true")
                .contains("metrics.enabled: true")
                .contains("portal.allowedWorlds count: 1")
                .contains("portal.deniedWorlds count: 1")
                .contains("portal.endPortalCreation: true")
                .contains("effects.endCreationSoundType: minecraft:block.end_portal.spawn")
                .contains("bStats integration: initialized")
                .contains("React metric provider: registered")
                .contains("ShapedPortals 2.0.0 | enabled=true")
                .doesNotContain("== Threads ==")
                .doesNotContain("Deadlocked:")
                .doesNotContain("Deadlock thread")
                .doesNotContain(" | state=")
                .doesNotContain("Privacy:")
                .doesNotContain("redacted")
                .doesNotContain("private-world-name")
                .doesNotContain("another-private-world")
                .doesNotContain("private-player-name")
                .doesNotContain("user.dir")
                .doesNotContain("sun.java.command")
                .doesNotContain("token=");
    }
}
