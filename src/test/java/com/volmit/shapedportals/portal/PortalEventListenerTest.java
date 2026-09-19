package com.volmit.shapedportals.portal;

import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortalEventListenerTest {
    @Test
    void routesAnEyedFrameFromTheAcceptedPlacementEvent() {
        PortalService portalService = mock(PortalService.class);
        PortalRegistry registry = mock(PortalRegistry.class);
        PortalIntegrityService integrity = mock(PortalIntegrityService.class);
        PortalEventListener listener = new PortalEventListener(portalService, registry, integrity);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        EndPortalFrame frame = mock(EndPortalFrame.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(event.canBuild()).thenReturn(true);
        when(block.getBlockData()).thenReturn(frame);
        when(frame.hasEye()).thenReturn(true);
        when(registry.affectedBy(block)).thenReturn(Set.of());

        listener.onPlace(event);

        verify(portalService).attemptEnd(block, player);
    }

    @Test
    void doesNotActivateFromTheEarlierProposedStateEvent() {
        PortalService portalService = mock(PortalService.class);
        PortalRegistry registry = mock(PortalRegistry.class);
        PortalIntegrityService integrity = mock(PortalIntegrityService.class);
        PortalEventListener listener = new PortalEventListener(portalService, registry, integrity);
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        Block block = mock(Block.class);
        when(event.getBlock()).thenReturn(block);
        when(registry.affectedBy(block)).thenReturn(Set.of());

        listener.onEntityChangeBlock(event);

        verifyNoInteractions(portalService);
    }

    @Test
    void doesNotActivateWhenThePlacementBuildResultIsDenied() {
        PortalService portalService = mock(PortalService.class);
        PortalRegistry registry = mock(PortalRegistry.class);
        PortalIntegrityService integrity = mock(PortalIntegrityService.class);
        PortalEventListener listener = new PortalEventListener(portalService, registry, integrity);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Block block = mock(Block.class);
        EndPortalFrame frame = mock(EndPortalFrame.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.canBuild()).thenReturn(false);
        when(block.getBlockData()).thenReturn(frame);
        when(frame.hasEye()).thenReturn(true);
        when(registry.affectedBy(block)).thenReturn(Set.of());

        listener.onPlace(event);

        verifyNoInteractions(portalService);
    }
}
