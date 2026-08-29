package com.volmit.shapedportals.portal;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortalIntegrityServiceTest {
    @Test
    void solidFrameMaterialsRemainBoundariesRegardlessOfTheCreationWhitelist() {
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.DIRT)).isTrue();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.GRASS_BLOCK)).isTrue();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.GLASS)).isTrue();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.AIR)).isFalse();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.CAVE_AIR)).isFalse();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.FIRE)).isFalse();
        assertThat(PortalIntegrityService.isFrameBoundaryMaterial(Material.NETHER_PORTAL)).isFalse();
    }
}
