package com.volmit.shapedportals.portal;

import org.bukkit.block.Block;

import java.util.UUID;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey from(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
