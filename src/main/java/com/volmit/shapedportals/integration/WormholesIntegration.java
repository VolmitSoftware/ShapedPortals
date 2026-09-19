package com.volmit.shapedportals.integration;

import art.arcane.wormholes.api.portal.NetherPortalShapes;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.portal.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.BlockVector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WormholesIntegration {
    private WormholesIntegration() {
    }

    public static Result submit(World world, List<BlockPosition> interior, PortalAxis axis, Entity creator) {
        return Bukkit.getPluginManager().isPluginEnabled("Wormholes")
                ? Connected.submit(world, positions(interior), axis, creator)
                : Result.UNAVAILABLE;
    }

    public static boolean owns(World world, List<BlockPosition> interior) {
        return Bukkit.getPluginManager().isPluginEnabled("Wormholes")
                && Connected.owns(world, positions(interior));
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
