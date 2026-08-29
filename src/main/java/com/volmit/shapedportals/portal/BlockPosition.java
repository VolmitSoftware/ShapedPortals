package com.volmit.shapedportals.portal;

import org.bukkit.World;
import org.bukkit.block.Block;

public record BlockPosition(int x, int y, int z) {
    public static BlockPosition from(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
