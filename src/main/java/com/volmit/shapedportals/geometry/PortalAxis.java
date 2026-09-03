package com.volmit.shapedportals.geometry;

import org.bukkit.Axis;

public enum PortalAxis {
    X,
    Z,
    Y;

    public Axis bukkitAxis() {
        return switch (this) {
            case X -> Axis.X;
            case Z -> Axis.Z;
            case Y -> throw new IllegalStateException("Horizontal End portals do not expose a Bukkit axis");
        };
    }

    public boolean horizontal() {
        return this == Y;
    }
}
