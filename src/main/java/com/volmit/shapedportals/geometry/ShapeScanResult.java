package com.volmit.shapedportals.geometry;

public record ShapeScanResult(PortalShape shape, ShapeFailure failure, GridPoint blocker) {
    public static ShapeScanResult success(PortalShape shape) {
        return new ShapeScanResult(shape, ShapeFailure.NONE, null);
    }

    public static ShapeScanResult failure(ShapeFailure failure, GridPoint blocker) {
        return new ShapeScanResult(null, failure, blocker);
    }

    public boolean valid() {
        return shape != null;
    }
}
