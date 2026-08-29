package com.volmit.shapedportals.portal;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class UnsafeTeleportConfirmationStoreTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PORTAL = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_PORTAL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void consumesOnlyTheSamePortalOnce() {
        AtomicLong clock = new AtomicLong(100L);
        UnsafeTeleportConfirmationStore store = new UnsafeTeleportConfirmationStore(10L, clock::get);

        store.arm(PLAYER, PORTAL);

        assertThat(store.consume(PLAYER, PORTAL)).isTrue();
        assertThat(store.consume(PLAYER, PORTAL)).isFalse();
    }

    @Test
    void differentPortalConsumesThePendingConfirmationWithoutAuthorizingIt() {
        AtomicLong clock = new AtomicLong(100L);
        UnsafeTeleportConfirmationStore store = new UnsafeTeleportConfirmationStore(10L, clock::get);

        store.arm(PLAYER, PORTAL);

        assertThat(store.consume(PLAYER, OTHER_PORTAL)).isFalse();
        assertThat(store.consume(PLAYER, PORTAL)).isFalse();
    }

    @Test
    void rejectsExpiredConfirmation() {
        AtomicLong clock = new AtomicLong(100L);
        UnsafeTeleportConfirmationStore store = new UnsafeTeleportConfirmationStore(10L, clock::get);
        store.arm(PLAYER, PORTAL);
        clock.set(110L);

        assertThat(store.consume(PLAYER, PORTAL)).isFalse();
    }

    @Test
    void scheduledExpiryDoesNotRemoveAReplacement() {
        AtomicLong clock = new AtomicLong(100L);
        UnsafeTeleportConfirmationStore store = new UnsafeTeleportConfirmationStore(10L, clock::get);
        UnsafeTeleportConfirmationStore.Confirmation first = store.arm(PLAYER, PORTAL);
        store.arm(PLAYER, PORTAL);

        store.expire(PLAYER, first);

        assertThat(store.consume(PLAYER, PORTAL)).isTrue();
    }
}
