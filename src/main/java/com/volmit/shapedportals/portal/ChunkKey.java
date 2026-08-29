package com.volmit.shapedportals.portal;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
}
