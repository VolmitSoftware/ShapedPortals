package com.volmit.shapedportals.integration;

import art.arcane.wormholes.api.portal.NetherPortalShapes;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.portal.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockVector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class WormholesIntegration {
    private static volatile boolean incompatible;

    private WormholesIntegration() {
    }

    public static Result submit(World world, List<BlockPosition> interior, PortalAxis axis, Entity creator) {
        if (!available()) {
            return Result.UNAVAILABLE;
        }
        try {
            return Connected.submit(world, positions(interior), axis, creator);
        } catch (LinkageError error) {
            markIncompatible(error);
            return Result.UNAVAILABLE;
        }
    }

    public static boolean owns(World world, List<BlockPosition> interior) {
        if (!available()) {
            return false;
        }
        try {
            return Connected.owns(world, positions(interior));
        } catch (LinkageError error) {
            markIncompatible(error);
            return false;
        }
    }

    private static boolean available() {
        return !incompatible && Bukkit.getPluginManager().isPluginEnabled("Wormholes");
    }

    // Wormholes releases before 2.0.7 do not publish NetherPortalShapes.
    private static void markIncompatible(LinkageError error) {
        if (incompatible) {
            return;
        }
        incompatible = true;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ShapedPortals");
        if (plugin != null) {
            plugin.getLogger().log(Level.WARNING,
                    "The installed Wormholes does not provide the shaped portal API; ownership handoff is disabled.",
                    error);
        }
    }

    static Set<BlockVector> positions(List<BlockPosition> interior) {
        Set<BlockVector> positions = new HashSet<>(interior.size());
        for (BlockPosition position : interior) {
            positions.add(new BlockVector(position.x(), position.y(), position.z()));
        }
        return Set.copyOf(positions);
    }

    private static final class Connected {
        private static Result submit(World world, Set<BlockVector> cells, PortalAxis axis, Entity creator) {
            NetherPortalShapes shapes = Bukkit.getServicesManager().load(NetherPortalShapes.class);
            if (shapes == null) {
                return Result.UNAVAILABLE;
            }
            return switch (shapes.submit(world, cells, axis.bukkitAxis(), creator)) {
                case ACCEPTED -> Result.ACCEPTED;
                case UNAVAILABLE -> Result.UNAVAILABLE;
                case REJECTED -> Result.REJECTED;
            };
        }

        private static boolean owns(World world, Set<BlockVector> cells) {
            NetherPortalShapes shapes = Bukkit.getServicesManager().load(NetherPortalShapes.class);
            return shapes != null && shapes.owns(world, cells);
        }
    }

    public enum Result {
        ACCEPTED,
        UNAVAILABLE,
        REJECTED
    }
}
