package com.volmit.shapedportals.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class PortalStatsTest {
    @Test
    void snapshotsBoundedRejectionReasons() {
        PortalStats stats = new PortalStats();
        stats.attempted();
        stats.attempted();
        stats.created();
        stats.rejected(PortalStats.RejectionReason.OPEN_FRAME);

        PortalStats.Snapshot snapshot = stats.snapshot();

        assertThat(snapshot.attempts()).isEqualTo(2L);
        assertThat(snapshot.created()).isEqualTo(1L);
        assertThat(snapshot.rejected()).isEqualTo(1L);
        assertThat(snapshot.rejectionReasons())
                .hasSize(PortalStats.RejectionReason.values().length)
                .containsEntry(PortalStats.RejectionReason.OPEN_FRAME, 1L)
                .containsEntry(PortalStats.RejectionReason.EVENT_CANCELLED, 0L);
    }
}
