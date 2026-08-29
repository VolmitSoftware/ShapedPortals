package com.volmit.shapedportals.portal;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.geometry.GridPoint;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.geometry.PortalCell;
import com.volmit.shapedportals.geometry.PortalShape;
import com.volmit.shapedportals.geometry.PortalShapeScanner;
import com.volmit.shapedportals.geometry.ShapeFailure;
import com.volmit.shapedportals.geometry.ShapeScanResult;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.portal.PortalStats.RejectionReason;
import com.volmit.shapedportals.presentation.FeedbackTone;
import com.volmit.shapedportals.presentation.PresentationService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PortalService {
    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final PresentationService presentation;
    private final PortalRegistry registry;
    private final PortalStats stats;
    private final Map<BlockKey, Long> recentIgnitions = new ConcurrentHashMap<>();

    public PortalService(
            ShapedPortals plugin,
            ConfigService configService,
            PresentationService presentation,
            PortalRegistry registry,
            PortalStats stats
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.presentation = presentation;
        this.registry = registry;
        this.stats = stats;
    }

    public void attempt(Block ignition, Entity creator, String cause) {
        RuntimeConfig config = configService.runtime();
        if (!config.enabled() || !config.allowsWorld(ignition.getWorld()) || !config.allowsIgnition(cause)) {
            return;
        }
        if (!PortalIgnitionEligibility.hasLowerFrameBoundary(ignition, config)) {
            return;
        }
        if (creator instanceof Player player && config.requireCreatePermission()
                && !player.hasPermission("shapedportals.create")) {
            failPermission(player);
            return;
        }
        if (isDuplicate(ignition, config.deduplicationMillis())) {
            return;
        }

        Location location = ignition.getLocation();
        String creatorName = creator == null ? "environment" : creator.getName();
        boolean scheduled = FoliaScheduler.runRegion(plugin, location,
                () -> attemptOwned(location, creator, creatorName, cause), 1L);
        if (!scheduled) {
            stats.attempted();
            stats.rejected(RejectionReason.REGION_SCHEDULING_UNAVAILABLE);
            if (creator instanceof Player player) {
                fail(player, "the owning region was unavailable");
            }
        }
    }

    private void attemptOwned(Location location, Entity creator, String creatorName, String cause) {
        RuntimeConfig config = configService.runtime();
        Block ignition = location.getBlock();
        if (!config.enabled()) {
            return;
        }
        if (!config.allowsWorld(ignition.getWorld())) {
            return;
        }
        if (!config.allowsIgnition(cause)) {
            return;
        }
        if (ignition.getType() == Material.NETHER_PORTAL) {
            return;
        }
        if (!PortalIgnitionEligibility.hasLowerFrameBoundary(ignition, config)) {
            return;
        }

        stats.attempted();
        ShapeScanResult selected = analyze(ignition, config);
        if (!selected.valid()) {
            stats.rejected(RejectionReason.fromShapeFailure(selected.failure()));
            if (creator instanceof Player player) {
                fail(player, failureText(selected.failure()));
            }
            return;
        }

        PortalShape shape = selected.shape();
        List<BlockPosition> interior = absolutePositions(ignition, shape.axis(), shape.interior());
        List<BlockPosition> frame = absolutePositions(ignition, shape.axis(), shape.frame());
        World world = ignition.getWorld();
        BlockData portalData = createPortalData(shape.axis());
        List<BlockState> originals = new ArrayList<>(interior.size());
        List<BlockState> proposed = new ArrayList<>(interior.size());
        for (BlockPosition position : interior) {
            Block block = position.block(world);
            originals.add(block.getState());
            BlockState state = block.getState();
            state.setType(Material.NETHER_PORTAL);
            state.setBlockData(portalData.clone());
            proposed.add(state);
        }

        Entity eventCreator = eventCreator(creator);
        PortalCreateEvent event = new PortalCreateEvent(
                proposed, world, eventCreator, PortalCreateEvent.CreateReason.FIRE);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            stats.rejected(RejectionReason.EVENT_CANCELLED);
            if (creator instanceof Player player) {
                fail(player, "another plugin cancelled creation");
            }
            return;
        }

        ShapeScanResult revalidated = analyze(ignition, config);
        if (!revalidated.valid() || !revalidated.shape().equals(shape)) {
            stats.rejected(RejectionReason.FRAME_CHANGED);
            if (creator instanceof Player player) {
                fail(player, "the frame changed during creation");
            }
            return;
        }

        PortalRecord record = new PortalRecord(
                PortalRecord.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                world.getUID(),
                world.getName(),
                shape.axis(),
                BlockPosition.from(ignition),
                interior,
                frame,
                frame.stream().map(position -> position.block(world).getType()).toList(),
                System.currentTimeMillis(),
                creatorName
        );
        if (!registry.register(record, false)) {
            stats.rejected(RejectionReason.OVERLAPPING_PORTAL);
            if (creator instanceof Player player) {
                fail(player, "the shape overlaps an existing managed portal");
            }
            return;
        }

        try {
            for (BlockPosition position : interior) {
                position.block(world).setBlockData(portalData.clone(), false);
            }
        } catch (Throwable exception) {
            registry.unregister(record.id());
            restore(originals);
            plugin.getLogger().log(Level.SEVERE, "Failed to commit shaped portal " + record.id(), exception);
            stats.rejected(RejectionReason.WORLD_MUTATION_FAILED);
            if (creator instanceof Player player) {
                fail(player, "the world mutation failed");
            }
            return;
        }

        registry.requestSave();
        stats.created();
        if (config.creationSound()) {
            world.playSound(location, config.creationSoundType(), SoundCategory.BLOCKS,
                    config.creationSoundVolume(), config.creationSoundPitch());
        }
        if (creator instanceof Player player) {
            MessageArgs arguments = MessageArgs.builder().trusted("blocks", interior.size()).build();
            presentation.portal(player, ShapedMessages.PORTAL_CREATED, arguments, FeedbackTone.SUCCESS);
        }
    }

    private ShapeScanResult analyze(Block ignition, RuntimeConfig config) {
        ShapeScanResult x = scan(ignition, PortalAxis.X, config);
        ShapeScanResult z = scan(ignition, PortalAxis.Z, config);
        if (x.valid() && z.valid()) {
            return ShapeScanResult.failure(ShapeFailure.AMBIGUOUS_AXIS, new GridPoint(0, 0));
        }
        if (x.valid()) {
            return x;
        }
        if (z.valid()) {
            return z;
        }
        return preferredFailure(x, z);
    }

    private ShapeScanResult scan(Block ignition, PortalAxis axis, RuntimeConfig config) {
        World world = ignition.getWorld();
        return PortalShapeScanner.scan(axis, point -> {
            int x = axis == PortalAxis.X ? ignition.getX() + point.horizontal() : ignition.getX();
            int z = axis == PortalAxis.Z ? ignition.getZ() + point.horizontal() : ignition.getZ();
            int y = ignition.getY() + point.vertical();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return PortalCell.BLOCKED;
            }
            if (FoliaScheduler.isFoliaThreading(plugin.getServer())
                    && !FoliaScheduler.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
                return PortalCell.UNOWNED;
            }
            Material material = world.getBlockAt(x, y, z).getType();
            if (config.frameMaterials().contains(material)) {
                return PortalCell.FRAME;
            }
            if (config.interiorMaterials().contains(material)) {
                return PortalCell.INTERIOR;
            }
            return PortalCell.BLOCKED;
        }, config.scanLimits());
    }

    private ShapeScanResult preferredFailure(ShapeScanResult first, ShapeScanResult second) {
        List<ShapeScanResult> results = List.of(first, second).stream()
                .sorted(Comparator.comparingInt(result -> failurePriority(result.failure())))
                .toList();
        return results.get(0);
    }

    private int failurePriority(ShapeFailure failure) {
        return switch (failure) {
            case CROSS_REGION -> 0;
            case TOO_LARGE, TOO_WIDE, TOO_TALL -> 1;
            case TOO_SMALL -> 2;
            case OPEN_FRAME -> 3;
            default -> 4;
        };
    }

    private List<BlockPosition> absolutePositions(Block origin, PortalAxis axis, Set<GridPoint> points) {
        return points.stream()
                .map(point -> new BlockPosition(
                        axis == PortalAxis.X ? origin.getX() + point.horizontal() : origin.getX(),
                        origin.getY() + point.vertical(),
                        axis == PortalAxis.Z ? origin.getZ() + point.horizontal() : origin.getZ()
                ))
                .sorted(Comparator.comparingInt(BlockPosition::y)
                        .thenComparingInt(BlockPosition::x)
                        .thenComparingInt(BlockPosition::z))
                .toList();
    }

    private BlockData createPortalData(PortalAxis axis) {
        Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
        portal.setAxis(axis.bukkitAxis());
        return portal;
    }

    private Entity eventCreator(Entity creator) {
        if (creator == null) {
            return null;
        }
        if (FoliaScheduler.isFoliaThreading(plugin.getServer())
                && !FoliaScheduler.isOwnedByCurrentRegion(creator)) {
            return null;
        }
        return creator;
    }

    private boolean isDuplicate(Block ignition, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        BlockKey key = BlockKey.from(ignition);
        Long previous = recentIgnitions.put(key, now);
        if (recentIgnitions.size() > 1024) {
            recentIgnitions.entrySet().removeIf(entry -> now - entry.getValue() > cooldownMillis);
        }
        return previous != null && now - previous < cooldownMillis;
    }

    private void fail(Player player, String reason) {
        if (!configService.runtime().failureFeedback()) {
            return;
        }
        MessageArgs arguments = MessageArgs.builder().untrusted("reason", reason).build();
        presentation.portal(player, ShapedMessages.PORTAL_FAILED, arguments, FeedbackTone.FAILURE);
    }

    private void failPermission(Player player) {
        if (!configService.runtime().failureFeedback()) {
            return;
        }
        presentation.portal(player, ShapedMessages.NO_PERMISSION, MessageArgs.empty(), FeedbackTone.FAILURE);
    }

    private String failureText(ShapeFailure failure) {
        return switch (failure) {
            case CROSS_REGION -> "the shape crosses independently owned Folia regions";
            case TOO_LARGE -> "the interior exceeds the configured block limit";
            case TOO_WIDE -> "the interior exceeds the configured width limit";
            case TOO_TALL -> "the interior exceeds the configured height limit";
            case TOO_SMALL -> "the interior is smaller than the configured minimum";
            case AMBIGUOUS_AXIS -> "the frame is valid on both axes";
            case START_BLOCKED -> "the ignition block is no longer replaceable";
            default -> "the frame is open or contains a blocked interior cell";
        };
    }

    private void restore(List<BlockState> originals) {
        for (BlockState original : originals) {
            try {
                original.update(true, false);
            } catch (Throwable exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to roll back portal block at " + original.getLocation(), exception);
            }
        }
    }
}
