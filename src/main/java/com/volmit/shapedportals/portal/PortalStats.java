package com.volmit.shapedportals.portal;

import com.volmit.shapedportals.geometry.ShapeFailure;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

public final class PortalStats {
    private final LongAdder attempts = new LongAdder();
    private final LongAdder created = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final Map<RejectionReason, LongAdder> rejectionReasons = new EnumMap<>(RejectionReason.class);

    public PortalStats() {
        for (RejectionReason reason : RejectionReason.values()) {
            rejectionReasons.put(reason, new LongAdder());
        }
    }

    public void attempted() {
        attempts.increment();
    }

    public void created() {
        created.increment();
    }

    public void rejected(RejectionReason reason) {
        RejectionReason requiredReason = Objects.requireNonNull(reason, "reason");
        rejected.increment();
        rejectionReasons.get(requiredReason).increment();
    }

    public Snapshot snapshot() {
        EnumMap<RejectionReason, Long> reasons = new EnumMap<>(RejectionReason.class);
        for (Map.Entry<RejectionReason, LongAdder> entry : rejectionReasons.entrySet()) {
            reasons.put(entry.getKey(), entry.getValue().sum());
        }
        return new Snapshot(attempts.sum(), created.sum(), rejected.sum(), reasons);
    }

    public enum RejectionReason {
        REGION_SCHEDULING_UNAVAILABLE,
        START_BLOCKED,
        OPEN_FRAME,
        TOO_SMALL,
        TOO_LARGE,
        TOO_WIDE,
        TOO_TALL,
        CROSS_REGION,
        AMBIGUOUS_AXIS,
        EVENT_CANCELLED,
        FRAME_CHANGED,
        OVERLAPPING_PORTAL,
        WORLD_MUTATION_FAILED;

        public static RejectionReason fromShapeFailure(ShapeFailure failure) {
            return switch (Objects.requireNonNull(failure, "failure")) {
                case START_BLOCKED -> START_BLOCKED;
                case OPEN_FRAME -> OPEN_FRAME;
                case TOO_SMALL -> TOO_SMALL;
                case TOO_LARGE -> TOO_LARGE;
                case TOO_WIDE -> TOO_WIDE;
                case TOO_TALL -> TOO_TALL;
                case CROSS_REGION -> CROSS_REGION;
                case AMBIGUOUS_AXIS -> AMBIGUOUS_AXIS;
                case NONE -> throw new IllegalArgumentException("A successful shape cannot be a rejection reason");
            };
        }
    }

    public record Snapshot(
            long attempts,
            long created,
            long rejected,
            Map<RejectionReason, Long> rejectionReasons
    ) {
        public Snapshot {
            rejectionReasons = Map.copyOf(rejectionReasons);
        }
    }
}
