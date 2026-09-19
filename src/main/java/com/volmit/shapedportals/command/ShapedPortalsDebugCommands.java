package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgs;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.FeedbackTone;
import org.bukkit.command.CommandSender;

@Director(name = "debug", description = "{prefix} diagnostic tools", descriptionKey = "command.description.debug")
public final class ShapedPortalsDebugCommands {
    private final ShapedPortals plugin;

    public ShapedPortalsDebugCommands(ShapedPortals plugin) {
        this.plugin = plugin;
    }

    @Director(name = "version", sync = true, description = "Show the installed {prefix} version", descriptionKey = "command.description.version")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        if (!sender.hasPermission("shapedportals.command")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        plugin.getLanguageService().send(sender, ShapedMessages.VERSION,
                MessageArgs.builder().untrusted("version", plugin.getDescription().getVersion()).build());
    }

    @Director(name = "dump", sync = true, description = "Create a comprehensive {prefix} diagnostic report", descriptionKey = "command.description.debug_dump")
    public void dump(
            @Param(name = "upload", defaultValue = "true", description = "Upload the report when public uploads are enabled", descriptionKey = "command.parameter.upload") boolean upload,
            @Param(name = "sender", contextual = true) CommandSender sender
    ) {
        if (!sender.hasPermission("shapedportals.debug")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        plugin.getDebugDump().request(sender, upload);
    }
}
