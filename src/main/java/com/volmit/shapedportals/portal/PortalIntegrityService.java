package com.volmit.shapedportals.portal;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.RuntimeConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class PortalIntegrityService {
    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final PortalRegistry registry;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final AtomicInteger cursor = new AtomicInteger();
    private SchedulerUtils.TaskHandle sweepTask;
    private long accumulatedTicks;

    public PortalIntegrityService(ShapedPortals plugin, ConfigService configService, PortalRegistry registry) {
        this.plugin = plugin;
        this.configService = configService;
        this.registry = registry;
    }

    public void start() {
        sweepTask = SchedulerUtils.scheduleSyncTask(plugin, 20L, this::sweepTick, true);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        pending.clear();
    }

    public void markDirty(Set<UUID> portalIds, long delayTicks) {
        for (UUID portalId : portalIds) {
            markDirty(portalId, delayTicks);
        }
    }

    public void markDirty(UUID portalId, long delayTicks) {
        if (!configService.runtime().integrityEnabled()) {
            return;
        }
        PortalRecord record = registry.get(portalId);
        if (record == null || !pending.add(portalId)) {
            return;
        }
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            pending.remove(portalId);
            return;
        }
        boolean scheduled = FoliaScheduler.runRegion(plugin,
                new Location(world, record.anchor().x(), record.anchor().y(), record.anchor().z()),
                () -> {
                    pending.remove(portalId);
                    audit(record);
                }, Math.max(1L, delayTicks));
        if (!scheduled) {
            pending.remove(portalId);
        }
    }

    private void sweepTick() {
        RuntimeConfig config = configService.runtime();
        if (!config.integrityEnabled()) {
            accumulatedTicks = 0L;
            return;
        }
        accumulatedTicks += 20L;
        if (accumulatedTicks < config.integrityCheckIntervalTicks()) {
            return;
        }
        accumulatedTicks = 0L;

        List<PortalRecord> records = registry.allRecords();
        if (records.isEmpty()) {
            cursor.set(0);
            return;
        }
        int start = Math.floorMod(cursor.getAndAdd(config.maximumIntegrityChecksPerCycle()), records.size());
        int checks = Math.min(config.maximumIntegrityChecksPerCycle(), records.size());
        for (int index = 0; index < checks; index++) {
            markDirty(records.get((start + index) % records.size()).id(), 1L);
        }
    }

    private void audit(PortalRecord record) {
        RuntimeConfig config = configService.runtime();
        if (!config.integrityEnabled()) {
            return;
        }
        World world = Bukkit.getWorld(record.worldId());
        if (world == null || !allChunksLoadedAndOwned(world, record)) {
            return;
        }

        PortalRecord authoritative = synchronizeFrameMaterialSnapshot(world, record);
        if (authoritative == null) {
            return;
        }

        for (BlockPosition position : authoritative.interior()) {
            Block block = position.block(world);
            if (block.getType() == Material.NETHER_PORTAL) {
                if (!(block.getBlockData() instanceof Orientable orientable)
                        || orientable.getAxis() != authoritative.axis().bukkitAxis()) {
                    deactivate(world, authoritative);
                    return;
                }
                continue;
            }
            if (!config.interiorMaterials().contains(block.getType())) {
                deactivate(world, authoritative);
                return;
            }
        }

        BlockData portalData = Material.NETHER_PORTAL.createBlockData();
        ((Orientable) portalData).setAxis(authoritative.axis().bukkitAxis());
        try {
            for (BlockPosition position : authoritative.interior()) {
                Block block = position.block(world);
                if (block.getType() != Material.NETHER_PORTAL) {
                    block.setBlockData(portalData.clone(), false);
                }
            }
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to repair shaped portal " + authoritative.id(), exception);
        }
    }

    private PortalRecord synchronizeFrameMaterialSnapshot(World world, PortalRecord record) {
        ArrayList<Material> snapshot = new ArrayList<>(record.frame().size());
        for (BlockPosition position : record.frame()) {
            Material material = position.block(world).getType();
            if (!isFrameBoundaryMaterial(material)) {
                deactivate(world, record);
                return null;
            }
            snapshot.add(material);
        }
        if (snapshot.equals(record.frameMaterialSnapshot())) {
            return record;
        }
        return registry.updateFrameMaterialSnapshot(record, snapshot);
    }

    static boolean isFrameBoundaryMaterial(Material material) {
        return !material.isAir()
                && material != Material.FIRE
                && material != Material.SOUL_FIRE
                && material != Material.NETHER_PORTAL;
    }

    private void deactivate(World world, PortalRecord record) {
        try {
            for (BlockPosition position : record.interior()) {
                Block block = position.block(world);
                if (block.getType() == Material.NETHER_PORTAL) {
                    block.setType(Material.AIR, false);
                }
            }
            registry.unregister(record.id());
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deactivate invalid shaped portal " + record.id(), exception);
        }
    }

    private boolean allChunksLoadedAndOwned(World world, PortalRecord record) {
        for (ChunkKey chunk : record.chunks()) {
            if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                return false;
            }
            if (FoliaScheduler.isFoliaThreading(plugin.getServer())
                    && !FoliaScheduler.isOwnedByCurrentRegion(world, chunk.x(), chunk.z())) {
                return false;
            }
        }
        return true;
    }
}
