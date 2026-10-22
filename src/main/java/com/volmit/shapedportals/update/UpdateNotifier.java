package com.volmit.shapedportals.update;

import art.arcane.volmlib.util.update.BukkitUpdateService;
import com.volmit.shapedportals.ShapedPortals;

public final class UpdateNotifier implements AutoCloseable {
    private final BukkitUpdateService updates;

    public UpdateNotifier(ShapedPortals plugin) {
        updates = BukkitUpdateService.register(plugin, new BukkitUpdateService.Options(
                "VolmitSoftware", "ShapedPortals", "shapedportals.update",
                () -> plugin.getConfigService().runtime().updateNotifications()));
    }

    public void reconfigure() {
        updates.reconfigure();
    }

    @Override
    public void close() {
        updates.close();
    }
}
