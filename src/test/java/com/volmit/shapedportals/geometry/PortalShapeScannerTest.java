package com.volmit.shapedportals.geometry;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PortalShapeScannerTest {
    @Test
    void findsClosedConcaveShapeWithoutRecursion() {
        Set<GridPoint> interior = Set.of(
                new GridPoint(0, 0),
                new GridPoint(1, 0),
                new GridPoint(2, 0),
                new GridPoint(0, 1),
                new GridPoint(0, 2)
        );

        ShapeScanResult result = scan(interior, boundary(interior), 2, 16);

        assertThat(result.valid()).isTrue();
        assertThat(result.shape().interior()).containsExactlyInAnyOrderElementsOf(interior);
        assertThat(result.shape().frame()).containsExactlyInAnyOrderElementsOf(boundary(interior));
    }

    @Test
    void exactMaximumIsInclusiveAndMaximumPlusOneFails() {
        Set<GridPoint> exact = Set.of(
                new GridPoint(0, 0),
                new GridPoint(0, 1),
                new GridPoint(0, 2),
                new GridPoint(0, 3)
        );
        Set<GridPoint> oversized = new HashSet<>(exact);
        oversized.add(new GridPoint(0, 4));

        assertThat(scan(exact, boundary(exact), 1, 4).valid()).isTrue();
        assertThat(scan(oversized, boundary(oversized), 1, 4).failure()).isEqualTo(ShapeFailure.TOO_LARGE);
    }

    @Test
    void rejectsAnOpenBoundary() {
        Set<GridPoint> interior = Set.of(new GridPoint(0, 0), new GridPoint(0, 1));
        Set<GridPoint> frame = new HashSet<>(boundary(interior));
        frame.remove(new GridPoint(1, 0));

        ShapeScanResult result = scan(interior, frame, 1, 8);

        assertThat(result.valid()).isFalse();
        assertThat(result.failure()).isEqualTo(ShapeFailure.OPEN_FRAME);
        assertThat(result.blocker()).isEqualTo(new GridPoint(1, 0));
    }

    @Test
    void rejectsCrossRegionLookupBeforeReadingFurther() {
        PortalShapeScanner.ScanLimits limits = new PortalShapeScanner.ScanLimits(1, 8, 8, 8);

        ShapeScanResult result = PortalShapeScanner.scan(PortalAxis.Z, point -> {
            if (point.equals(new GridPoint(0, 0))) {
                return PortalCell.INTERIOR;
            }
            if (point.equals(new GridPoint(0, 1))) {
                return PortalCell.UNOWNED;
            }
            return PortalCell.FRAME;
        }, limits);

        assertThat(result.failure()).isEqualTo(ShapeFailure.CROSS_REGION);
        assertThat(result.blocker()).isEqualTo(new GridPoint(0, 1));
    }

    @Test
    void enforcesIndependentWidthAndHeightLimits() {
        Set<GridPoint> wide = Set.of(
                new GridPoint(0, 0),
                new GridPoint(1, 0),
                new GridPoint(2, 0)
        );
        Set<GridPoint> tall = Set.of(
                new GridPoint(0, 0),
                new GridPoint(0, 1),
                new GridPoint(0, 2)
        );

        assertThat(scan(wide, boundary(wide), 1, 8, 2, 8).failure()).isEqualTo(ShapeFailure.TOO_WIDE);
        assertThat(scan(tall, boundary(tall), 1, 8, 8, 2).failure()).isEqualTo(ShapeFailure.TOO_TALL);
    }

    @Test
    void supportsHorizontalEndPortalPlanes() {
        Set<GridPoint> interior = Set.of(
                new GridPoint(0, 0),
                new GridPoint(1, 0),
                new GridPoint(0, 1),
                new GridPoint(1, 1)
        );
        PortalShapeScanner.ScanLimits limits = new PortalShapeScanner.ScanLimits(1, 16, 8, 8);

        ShapeScanResult result = PortalShapeScanner.scan(PortalAxis.Y, point -> {
            if (interior.contains(point)) {
                return PortalCell.INTERIOR;
            }
            return boundary(interior).contains(point) ? PortalCell.FRAME : PortalCell.BLOCKED;
        }, limits);

        assertThat(result.valid()).isTrue();
        assertThat(result.shape().axis()).isEqualTo(PortalAxis.Y);
        assertThat(result.shape().interior()).containsExactlyInAnyOrderElementsOf(interior);
    }

    private ShapeScanResult scan(Set<GridPoint> interior, Set<GridPoint> frame, int minimum, int maximum) {
        return scan(interior, frame, minimum, maximum, 16, 16);
    }

    private ShapeScanResult scan(
            Set<GridPoint> interior,
            Set<GridPoint> frame,
            int minimum,
            int maximum,
            int maximumWidth,
            int maximumHeight
    ) {
        PortalShapeScanner.ScanLimits limits = new PortalShapeScanner.ScanLimits(
                minimum, maximum, maximumWidth, maximumHeight);
        return PortalShapeScanner.scan(PortalAxis.X, point -> {
            if (interior.contains(point)) {
                return PortalCell.INTERIOR;
            }
            if (frame.contains(point)) {
                return PortalCell.FRAME;
            }
            return PortalCell.BLOCKED;
        }, limits);
    }

    private Set<GridPoint> boundary(Set<GridPoint> interior) {
        Set<GridPoint> frame = new HashSet<>();
        for (GridPoint point : interior) {
            frame.add(point.up());
            frame.add(point.down());
            frame.add(point.left());
            frame.add(point.right());
        }
        frame.removeAll(interior);
        return frame;
    }
}
