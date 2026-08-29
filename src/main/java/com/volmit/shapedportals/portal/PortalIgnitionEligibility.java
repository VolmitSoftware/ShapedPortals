package com.volmit.shapedportals.portal;

import com.volmit.shapedportals.config.RuntimeConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.function.IntFunction;

final class PortalIgnitionEligibility {
    private PortalIgnitionEligibility() {
    }

    static boolean hasLowerFrameBoundary(Block ignition, RuntimeConfig config) {
        World world = ignition.getWorld();
        int maximumDepth = Math.min(
                config.scanLimits().maximumHeight(),
                ignition.getY() - world.getMinHeight()
        );
        return hasLowerFrameBoundary(
                config.frameMaterials(),
                config.interiorMaterials(),
                maximumDepth,
                depth -> world.getBlockAt(ignition.getX(), ignition.getY() - depth, ignition.getZ()).getType()
        );
    }

    static boolean hasLowerFrameBoundary(
            Set<Material> frameMaterials,
            Set<Material> interiorMaterials,
            int maximumDepth,
            IntFunction<Material> materialAtDepth
    ) {
        for (int depth = 0; depth <= maximumDepth; depth++) {
            Material material = materialAtDepth.apply(depth);
            if (frameMaterials.contains(material)) {
                return depth > 0;
            }
            if (!interiorMaterials.contains(material)) {
                return false;
            }
        }
        return false;
    }
}
