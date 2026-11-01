package com.volmit.shapedportals.portal;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PortalPlacementCheckEvent extends BlockPlaceEvent {
    public PortalPlacementCheckEvent(Block placed, Block against, ItemStack item, Player player) {
        super(placed, placed.getState(), against, item, player, true, EquipmentSlot.HAND);
    }
}
