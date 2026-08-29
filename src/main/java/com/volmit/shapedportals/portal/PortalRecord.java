package com.volmit.shapedportals.portal;

import com.volmit.shapedportals.geometry.PortalAxis;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record PortalRecord(
        int schemaVersion,
        UUID id,
        UUID worldId,
        String worldName,
        PortalAxis axis,
        BlockPosition anchor,
        List<BlockPosition> interior,
        List<BlockPosition> frame,
        List<Material> frameMaterialSnapshot,
        long createdAtEpochMillis,
        String creator
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PortalRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported portal record schema: " + schemaVersion);
        }
        id = Objects.requireNonNull(id, "Portal id cannot be null");
        worldId = Objects.requireNonNull(worldId, "Portal world id cannot be null");
        worldName = Objects.requireNonNull(worldName, "Portal world name cannot be null");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("Portal world name cannot be blank");
        }
        axis = Objects.requireNonNull(axis, "Portal axis cannot be null");
        anchor = Objects.requireNonNull(anchor, "Portal anchor cannot be null");
        interior = List.copyOf(interior);
        frame = List.copyOf(frame);
        frameMaterialSnapshot = frameMaterialSnapshot == null ? List.of() : List.copyOf(frameMaterialSnapshot);
        if (interior.isEmpty() || frame.isEmpty()) {
            throw new IllegalArgumentException("Portal record must contain interior and frame blocks");
        }
        if (interior.size() > 4096 || frame.size() > 16384) {
            throw new IllegalArgumentException("Portal record exceeds safety limits");
        }
        Set<BlockPosition> uniqueInterior = new HashSet<>(interior);
        Set<BlockPosition> uniqueFrame = new HashSet<>(frame);
        if (uniqueInterior.size() != interior.size() || uniqueFrame.size() != frame.size()) {
            throw new IllegalArgumentException("Portal record contains duplicate coordinates");
        }
        if (!frameMaterialSnapshot.isEmpty() && frameMaterialSnapshot.size() != frame.size()) {
            throw new IllegalArgumentException("Portal frame material snapshot must match the frame coordinates");
        }
        if (!uniqueInterior.contains(anchor)) {
            throw new IllegalArgumentException("Portal anchor must be an interior coordinate");
        }
        uniqueInterior.retainAll(uniqueFrame);
        if (!uniqueInterior.isEmpty()) {
            throw new IllegalArgumentException("Portal frame and interior coordinates overlap");
        }
        creator = creator == null ? "unknown" : creator;
    }

    public boolean hasFrameMaterialSnapshot() {
        return !frameMaterialSnapshot.isEmpty();
    }

    public PortalRecord withFrameMaterialSnapshot(List<Material> materials) {
        return new PortalRecord(
                schemaVersion,
                id,
                worldId,
                worldName,
                axis,
                anchor,
                interior,
                frame,
                materials,
                createdAtEpochMillis,
                creator
        );
    }

    public Set<ChunkKey> chunks() {
        Set<ChunkKey> chunks = interior.stream()
                .map(position -> new ChunkKey(worldId, position.chunkX(), position.chunkZ()))
                .collect(Collectors.toSet());
        for (BlockPosition position : frame) {
            chunks.add(new ChunkKey(worldId, position.chunkX(), position.chunkZ()));
        }
        return chunks;
    }
}
