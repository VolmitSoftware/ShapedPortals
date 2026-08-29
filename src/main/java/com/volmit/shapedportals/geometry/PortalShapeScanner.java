package com.volmit.shapedportals.geometry;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortalShapeScanner {
    private PortalShapeScanner() {
    }

    public static ShapeScanResult scan(PortalAxis axis, CellLookup lookup, ScanLimits limits) {
        Objects.requireNonNull(axis, "Portal axis cannot be null");
        Objects.requireNonNull(lookup, "Cell lookup cannot be null");
        Objects.requireNonNull(limits, "Scan limits cannot be null");

        GridPoint origin = new GridPoint(0, 0);
        PortalCell originCell = lookup.cell(origin);
        if (originCell == PortalCell.UNOWNED) {
            return ShapeScanResult.failure(ShapeFailure.CROSS_REGION, origin);
        }
        if (originCell != PortalCell.INTERIOR) {
            return ShapeScanResult.failure(ShapeFailure.START_BLOCKED, origin);
        }

        ArrayDeque<GridPoint> pending = new ArrayDeque<>();
        Set<GridPoint> queued = new HashSet<>();
        Set<GridPoint> interior = new HashSet<>();
        Set<GridPoint> frame = new HashSet<>();
        pending.add(origin);
        queued.add(origin);

        int minimumHorizontal = 0;
        int maximumHorizontal = 0;
        int minimumVertical = 0;
        int maximumVertical = 0;

        while (!pending.isEmpty()) {
            GridPoint current = pending.removeFirst();
            interior.add(current);
            if (interior.size() > limits.maximumInteriorBlocks()) {
                return ShapeScanResult.failure(ShapeFailure.TOO_LARGE, current);
            }

            minimumHorizontal = Math.min(minimumHorizontal, current.horizontal());
            maximumHorizontal = Math.max(maximumHorizontal, current.horizontal());
            minimumVertical = Math.min(minimumVertical, current.vertical());
            maximumVertical = Math.max(maximumVertical, current.vertical());
            if (maximumHorizontal - minimumHorizontal + 1 > limits.maximumWidth()) {
                return ShapeScanResult.failure(ShapeFailure.TOO_WIDE, current);
            }
            if (maximumVertical - minimumVertical + 1 > limits.maximumHeight()) {
                return ShapeScanResult.failure(ShapeFailure.TOO_TALL, current);
            }

            for (GridPoint adjacent : neighbors(current)) {
                PortalCell cell = lookup.cell(adjacent);
                if (cell == PortalCell.FRAME) {
                    frame.add(adjacent);
                    continue;
                }
                if (cell == PortalCell.UNOWNED) {
                    return ShapeScanResult.failure(ShapeFailure.CROSS_REGION, adjacent);
                }
                if (cell == PortalCell.BLOCKED) {
                    return ShapeScanResult.failure(ShapeFailure.OPEN_FRAME, adjacent);
                }
                if (queued.add(adjacent)) {
                    pending.addLast(adjacent);
                }
            }
        }

        if (interior.size() < limits.minimumInteriorBlocks()) {
            return ShapeScanResult.failure(ShapeFailure.TOO_SMALL, origin);
        }
        return ShapeScanResult.success(new PortalShape(axis, interior, frame));
    }

    private static List<GridPoint> neighbors(GridPoint point) {
        return List.of(point.up(), point.down(), point.left(), point.right());
    }

    @FunctionalInterface
    public interface CellLookup {
        PortalCell cell(GridPoint point);
    }

    public record ScanLimits(
            int minimumInteriorBlocks,
            int maximumInteriorBlocks,
            int maximumWidth,
            int maximumHeight
    ) {
        public ScanLimits {
            if (minimumInteriorBlocks < 1 || maximumInteriorBlocks < minimumInteriorBlocks) {
                throw new IllegalArgumentException("Invalid interior block limits");
            }
            if (maximumWidth < 1 || maximumHeight < 1) {
                throw new IllegalArgumentException("Invalid portal dimensions");
            }
        }
    }
}
