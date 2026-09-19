package com.volmit.shapedportals.integration;

import com.volmit.shapedportals.portal.BlockPosition;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class WormholesIntegrationTest {
    @Test
    void passesOnlyTheSelectedCellsOfAnIrregularShape() {
        Set<BlockVector> cells = WormholesIntegration.positions(List.of(
                new BlockPosition(-17, -10, 32),
                new BlockPosition(-16, -10, 32),
                new BlockPosition(-17, -9, 32),
                new BlockPosition(-17, -9, 32)
        ));

        assertThat(cells).containsExactlyInAnyOrder(
                new BlockVector(-17, -10, 32),
                new BlockVector(-16, -10, 32),
                new BlockVector(-17, -9, 32)
        );
        assertThat(cells).doesNotContain(new BlockVector(-16, -9, 32));
    }

    @Test
    void integrationEntryPointsLoadWithoutTheOptionalWormholesApi() {
        assertThat(WormholesIntegration.class.getDeclaredMethods()).isNotEmpty();
    }
}
