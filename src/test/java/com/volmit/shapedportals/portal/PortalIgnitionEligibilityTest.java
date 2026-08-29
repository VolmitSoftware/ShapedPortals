package com.volmit.shapedportals.portal;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class PortalIgnitionEligibilityTest {
    private static final Set<Material> FRAMES = Set.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN);
    private static final Set<Material> INTERIORS = Set.of(Material.FIRE, Material.AIR);

    @Test
    void ignoresOrdinaryFireAndBlockedColumns() {
        assertThat(eligible(4, Material.FIRE, Material.DIRT)).isFalse();
        assertThat(eligible(4, Material.FIRE, Material.AIR, Material.STONE, Material.OBSIDIAN)).isFalse();
    }

    @Test
    void recognizesLowerFrameBoundariesBelowAnyInteriorCell() {
        assertThat(eligible(4, Material.FIRE, Material.OBSIDIAN)).isTrue();
        assertThat(eligible(4, Material.FIRE, Material.AIR, Material.AIR, Material.CRYING_OBSIDIAN)).isTrue();
    }

    @Test
    void enforcesTheMaximumHeightBoundary() {
        assertThat(eligible(2, Material.FIRE, Material.AIR, Material.OBSIDIAN)).isTrue();
        assertThat(eligible(2, Material.FIRE, Material.AIR, Material.AIR, Material.OBSIDIAN)).isFalse();
    }

    private boolean eligible(int maximumDepth, Material... materials) {
        List<Material> column = List.of(materials);
        return PortalIgnitionEligibility.hasLowerFrameBoundary(
                FRAMES,
                INTERIORS,
                maximumDepth,
                depth -> depth < column.size() ? column.get(depth) : Material.AIR
        );
    }
}
