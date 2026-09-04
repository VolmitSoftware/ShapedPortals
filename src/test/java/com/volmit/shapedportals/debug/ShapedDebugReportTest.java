package com.volmit.shapedportals.debug;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.portal.BlockPosition;
import com.volmit.shapedportals.portal.PortalRecord;
import com.volmit.shapedportals.portal.PortalStats;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
                "Folia region",
                "en_US",
                List.of("en_US"),
                "ready",
                "30e3b4eea5852ffa879d8371d556cd4b9b9fdbe7",
                false,
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
                Set.of(portal.worldId()),
                Path.of(".")
        );

        String report = ShapedDebugReport.create(snapshot);

        assertThat(report)
                .contains("Managed portals: 4")
                .contains("Creation attempts: 10")
                .contains("Rejected open frame: 4")
                .contains("Rejected event cancelled: 2")
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
