package com.volmit.shapedportals.portal;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class UnsafeTeleportConfirmationStore {
    private final long lifetimeNanos;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    UnsafeTeleportConfirmationStore(long lifetimeNanos, LongSupplier clock) {
        if (lifetimeNanos <= 0L) {
            throw new IllegalArgumentException("lifetimeNanos must be positive");
        }
        this.lifetimeNanos = lifetimeNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Confirmation arm(UUID playerId, UUID portalId) {
        UUID requiredPlayerId = Objects.requireNonNull(playerId, "playerId");
        Confirmation confirmation = new Confirmation(
                portalId, clock.getAsLong() + lifetimeNanos, generations.incrementAndGet());
        confirmations.put(requiredPlayerId, confirmation);
        return confirmation;
    }

    boolean consume(UUID playerId, UUID portalId) {
        Confirmation confirmation = confirmations.remove(Objects.requireNonNull(playerId, "playerId"));
        return confirmation != null && confirmation.accepts(portalId, clock.getAsLong());
    }

    void expire(UUID playerId, Confirmation confirmation) {
        confirmations.remove(Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(confirmation, "confirmation"));
    }

    record Confirmation(UUID portalId, long expiresAtNanos, long generation) {
        Confirmation {
            Objects.requireNonNull(portalId, "portalId");
        }

        boolean accepts(UUID requestedPortalId, long nowNanos) {
            return portalId.equals(requestedPortalId) && nowNanos - expiresAtNanos < 0L;
        }
    }
}
