package com.volmit.shapedportals.portal;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.geometry.GridPoint;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.geometry.PortalShape;
import com.volmit.shapedportals.geometry.PortalShapeScanner;
import com.volmit.shapedportals.geometry.ShapeFailure;
import com.volmit.shapedportals.geometry.ShapeScanResult;
import com.volmit.shapedportals.integration.WormholesIntegration;
import com.volmit.shapedportals.presentation.PresentationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class WormholesOwnershipTest {
    @ParameterizedTest
    @EnumSource(value = WormholesIntegration.Result.class, names = {"ACCEPTED", "REJECTED"})
    void handledShapeDoesNotWriteBlocksEmitVanillaEventsOrRegisterPhysicalPortal(WormholesIntegration.Result result) throws Exception {
        ShapedPortals plugin = mock(ShapedPortals.class);
        ConfigService configs = mock(ConfigService.class);
        RuntimeConfig config = mock(RuntimeConfig.class);
        PortalRegistry registry = mock(PortalRegistry.class);
        World world = mock(World.class);
        Block ignition = mock(Block.class);
        when(configs.runtime()).thenReturn(config);
        when(config.enabled()).thenReturn(true);
        when(config.allowsWorld(world)).thenReturn(true);
        when(config.allowsIgnition("FIRE")).thenReturn(true);
        when(ignition.getWorld()).thenReturn(world);
        when(ignition.getType()).thenReturn(Material.FIRE);
        when(world.getBlockAt(any(Location.class))).thenReturn(ignition);
        PortalStats stats = new PortalStats();
        PortalService service = new PortalService(plugin, configs, mock(PresentationService.class), registry, stats);
        PortalShape shape = new PortalShape(PortalAxis.X, Set.of(new GridPoint(0, 0)), Set.of(new GridPoint(0, -1)));
        try (MockedStatic<PortalIgnitionEligibility> eligibility = mockStatic(PortalIgnitionEligibility.class);
             MockedStatic<PortalShapeScanner> scanner = mockStatic(PortalShapeScanner.class);
             MockedStatic<WormholesIntegration> wormholes = mockStatic(WormholesIntegration.class)) {
            eligibility.when(() -> PortalIgnitionEligibility.hasLowerFrameBoundary(ignition, config)).thenReturn(true);
            scanner.when(() -> PortalShapeScanner.scan(any(), any(), any()))
                    .thenReturn(ShapeScanResult.success(shape), ShapeScanResult.failure(ShapeFailure.OPEN_FRAME, new GridPoint(0, 0)));
            wormholes.when(() -> WormholesIntegration.submit(eq(world), any(), eq(PortalAxis.X), eq(null)))
                    .thenReturn(result);
            Method attempt = PortalService.class.getDeclaredMethod("attemptOwned", Location.class, Entity.class, String.class, String.class);
            attempt.setAccessible(true);
            attempt.invoke(service, new Location(world, 0, 0, 0), null, "environment", "FIRE");
        }
        verifyNoInteractions(plugin, registry);
        verify(ignition, never()).getState();
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertThat(stats.snapshot().created()).isEqualTo(result == WormholesIntegration.Result.ACCEPTED ? 1 : 0);
        assertThat(stats.snapshot().rejectionReasons().get(PortalStats.RejectionReason.EVENT_CANCELLED))
                .isEqualTo(result == WormholesIntegration.Result.REJECTED ? 1L : 0L);
    }

    @Test
    void ownedPortalIsRelinquishedWithoutPhysicalRepair() throws Exception {
        ShapedPortals plugin = mock(ShapedPortals.class);
        ConfigService configs = mock(ConfigService.class);
        RuntimeConfig config = mock(RuntimeConfig.class);
        PortalRegistry registry = mock(PortalRegistry.class);
        World world = mock(World.class);
        when(configs.runtime()).thenReturn(config);
        when(config.integrityEnabled()).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        PortalRecord record = new PortalRecord(PortalRecord.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), UUID.randomUUID(),
                "world", PortalAxis.X, new BlockPosition(0, 64, 0), List.of(new BlockPosition(0, 64, 0)),
                List.of(new BlockPosition(0, 63, 0)), List.of(Material.OBSIDIAN), 1L, "tester");
        PortalIntegrityService service = new PortalIntegrityService(plugin, configs, registry);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class);
             MockedStatic<WormholesIntegration> wormholes = mockStatic(WormholesIntegration.class)) {
            bukkit.when(() -> Bukkit.getWorld(record.worldId())).thenReturn(world);
            wormholes.when(() -> WormholesIntegration.owns(world, record.interior())).thenReturn(true);
            wormholes.when(() -> WormholesIntegration.submit(world, record.interior(), record.axis(), null))
                    .thenReturn(WormholesIntegration.Result.ACCEPTED);
            Method audit = PortalIntegrityService.class.getDeclaredMethod("audit", PortalRecord.class);
            audit.setAccessible(true);
            audit.invoke(service, record);
            wormholes.verify(() -> WormholesIntegration.submit(world, record.interior(), record.axis(), null));
        }
        verify(registry).unregister(record.id());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
}
