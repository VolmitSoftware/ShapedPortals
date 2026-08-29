package com.volmit.shapedportals.geometry;

import java.util.Set;

public record PortalShape(PortalAxis axis, Set<GridPoint> interior, Set<GridPoint> frame) {
    public PortalShape {
        interior = Set.copyOf(interior);
        frame = Set.copyOf(frame);
    }
}
