package com.volmit.shapedportals.portal;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PortalEventListener implements Listener {
    private final PortalService portalService;
    private final PortalRegistry registry;
    private final PortalIntegrityService integrity;

    public PortalEventListener(PortalService portalService, PortalRegistry registry, PortalIntegrityService integrity) {
        this.portalService = portalService;
        this.registry = registry;
        this.integrity = integrity;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Entity creator = event.getPlayer() == null
                ? responsibleCreator(event.getIgnitingEntity())
                : event.getPlayer();
        portalService.attempt(event.getBlock(), creator, event.getCause().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        dirty(block);
        if (block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE) {
            portalService.attempt(block, event.getPlayer(), "PLACED_FIRE");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Set<UUID> changed = registry.affectedBy(event.getBlock());
        Set<UUID> source = registry.affectedBy(event.getSourceBlock());
        if (changed.isEmpty()) {
            integrity.markDirty(source, 1L);
            return;
        }
        if (source.isEmpty() || changed.equals(source)) {
            integrity.markDirty(changed, 1L);
            return;
        }
        Set<UUID> affected = new HashSet<>(changed);
        affected.addAll(source);
        integrity.markDirty(affected, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        dirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        dirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        dirty(List.of(event.getBlock(), event.getToBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Block> blocks = new HashSet<>(event.getBlocks());
        blocks.add(event.getBlock());
        dirty(blocks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Block> blocks = new HashSet<>(event.getBlocks());
        blocks.add(event.getBlock());
        dirty(blocks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        dirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        dirty(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        dirty(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        dirty(List.of(event.getBlockClicked(), event.getBlockClicked().getRelative(event.getBlockFace())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        dirty(event.getBlockClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        integrity.markDirty(registry.inChunk(
                event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()), 1L);
    }

    private void dirty(Block block) {
        integrity.markDirty(registry.affectedBy(block), 1L);
    }

    private Entity responsibleCreator(Entity igniter) {
        if (igniter instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return igniter;
    }

    private void dirty(Iterable<Block> blocks) {
        Set<UUID> affected = new HashSet<>();
        for (Block block : blocks) {
            affected.addAll(registry.affectedBy(block));
        }
        integrity.markDirty(affected, 1L);
    }
}
