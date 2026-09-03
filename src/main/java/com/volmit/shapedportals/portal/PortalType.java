package com.volmit.shapedportals.portal;

import com.volmit.shapedportals.geometry.PortalAxis;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

public enum PortalType {
    NETHER(Material.NETHER_PORTAL),
    END(Material.END_PORTAL);

    private final Material interiorMaterial;

    PortalType(Material interiorMaterial) {
        this.interiorMaterial = interiorMaterial;
    }

    public static PortalType from(PortalAxis axis) {
        return axis.horizontal() ? END : NETHER;
    }

    public Material interiorMaterial() {
        return interiorMaterial;
    }

    public BlockData createBlockData(PortalAxis axis) {
        BlockData data = interiorMaterial.createBlockData();
        if (this == NETHER) {
            ((Orientable) data).setAxis(axis.bukkitAxis());
        }
        return data;
    }

    public boolean matches(BlockData data, PortalAxis axis) {
        if (data.getMaterial() != interiorMaterial) {
            return false;
        }
        return this == END || data instanceof Orientable orientable && orientable.getAxis() == axis.bukkitAxis();
    }
}
