package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.portal.PortalStats;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import com.volmit.shapedportals.presentation.FeedbackTone;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

@Director(name = "shapedportals", aliases = {"shapedportal", "sp"}, description = "{prefix} help and administration", descriptionKey = "command.description.root")
public final class ShapedPortalsCommands {
    private final ShapedPortals plugin;
    private ShapedPortalsDebugCommands debug;

    public ShapedPortalsCommands(ShapedPortals plugin) {
        this.plugin = plugin;
        debug = new ShapedPortalsDebugCommands(plugin);
    }

    @Director(name = "version", hidden = true, sync = true, description = "Show the installed {prefix} version", descriptionKey = "command.description.version")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        debug.version(sender);
    }

    @Director(name = "config", sync = true, description = "Open the complete in-game configuration editor", descriptionKey = "command.description.config")
    public void config(@Param(name = "sender", contextual = true) CommandSender sender) {
        if (!sender.hasPermission("shapedportals.config")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.getPresentationService().command(sender, ShapedMessages.PLAYER_ONLY, FeedbackTone.FAILURE);
            return;
        }
        plugin.getConfigEditor().open(player);
        plugin.getPresentationService().command(player, ShapedMessages.COMMAND_CONFIG_OPENED, FeedbackTone.SUCCESS);
    }

    @Director(name = "language", sync = true, description = "Select an available {prefix} language", descriptionKey = "command.description.language")
    public void language(@Param(name = "sender", contextual = true) CommandSender sender) {
        plugin.getLanguageSwitcher().open(sender);
    }

    @Director(name = "portals", sync = true, description = "List every managed portal and its teleport shortcut", descriptionKey = "command.description.portals")
    public void portals(
            @Param(name = "page", defaultValue = "1", description = "One-based portal list page", descriptionKey = "command.parameter.page") int page,
            @Param(name = "sender", contextual = true) CommandSender sender
    ) {
        if (!sender.hasPermission("shapedportals.portals")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        plugin.getNavigationService().list(sender, page);
    }

    @Director(name = "teleport", aliases = {"tp"}, sync = true, description = "Teleport safely beside a managed portal or list portals when omitted", descriptionKey = "command.description.teleport")
    public void teleport(
            @Param(name = "portal", defaultValue = "list", description = "Portal UUID, unique prefix, or list", descriptionKey = "command.parameter.portal", customHandler = ShapedPortalsCommandHandlers.PortalId.class) String portal,
            @Param(name = "sender", contextual = true) CommandSender sender
    ) {
        if (portal.equalsIgnoreCase("list")) {
            portals(1, sender);
            return;
        }
        if (!sender.hasPermission("shapedportals.teleport")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.getPresentationService().command(sender, ShapedMessages.PLAYER_ONLY, FeedbackTone.FAILURE);
            return;
        }
        plugin.getNavigationService().teleport(player, portal);
    }

    @Director(name = "status", sync = true, description = "Show managed portal and attempt statistics", descriptionKey = "command.description.status")
    public void status(@Param(name = "sender", contextual = true) CommandSender sender) {
        LanguageService language = plugin.getLanguageService();
        if (!sender.hasPermission("shapedportals.command")) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }

        RuntimeConfig config = plugin.getConfigService().runtime();
        PortalStats.Snapshot stats = plugin.getPortalStats().snapshot();
        ArrayList<String> entries = new ArrayList<>();
        entries.add(statusEntry(sender, language, ShapedMessages.STATUS_CONFIG, MessageArgs.builder()
                .trusted("enabled", state(config.enabled()))
                .untrusted("language", config.language())
                .trusted("hot_reload", state(config.hotReloadEnabled()))
                .build()));
        entries.add(statusEntry(sender, language, ShapedMessages.STATUS_PORTALS, MessageArgs.builder()
                .trusted("portals", plugin.getPortalRegistry().portalCount())
                .trusted("cells", plugin.getPortalRegistry().interiorCellCount())
                .build()));
        entries.add(statusEntry(sender, language, ShapedMessages.STATUS_ATTEMPTS, MessageArgs.builder()
                .trusted("attempts", stats.attempts())
                .trusted("created", stats.created())
                .trusted("rejected", stats.rejected())
                .build()));
        entries.add(statusEntry(sender, language, ShapedMessages.STATUS_COMPATIBILITY, MessageArgs.builder()
                .untrusted("scheduler", plugin.schedulerName())
                .build()));
        String title = language.render(sender,
                ShapedMessages.STATUS_HEADER,
                MessageArgs.empty()
        ).plain();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                title,
                "/shapedportals status",
                "/shapedportals",
                entries,
                "",
                1,
                entries.size()
        );
        DirectorMiniMenu.deliverContent(sender, menu, ChatMenuStyle.theme(), language.directorResolver());
    }

    private String state(boolean enabled) {
        return enabled ? "&aenabled&r" : "&cdisabled&r";
    }

    private String statusEntry(CommandSender sender, LanguageService language, TextKey key, MessageArgs arguments) {
        ComponentText content = language.renderWithoutPrefix(sender, key, arguments);
        return ChatMenuStyle.entry(content).miniMessage();
    }
}
