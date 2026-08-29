package com.volmit.shapedportals.geometry;

import org.bukkit.Axis;

public enum PortalAxis {
    X(Axis.X),
    Z(Axis.Z);

    private final Axis bukkitAxis;

    PortalAxis(Axis bukkitAxis) {
        this.bukkitAxis = bukkitAxis;
    }

    public Axis bukkitAxis() {
        return bukkitAxis;
    }
}
