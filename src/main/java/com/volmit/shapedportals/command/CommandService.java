package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import com.volmit.shapedportals.presentation.FeedbackTone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class CommandService implements CommandExecutor, TabCompleter {
    private static final String ROOT_COMMAND = "shapedportals";

    private final ShapedPortals plugin;
    private final ShapedPortalsCommands commands;
    private final DirectorRuntimeEngine director;

    public CommandService(ShapedPortals plugin) {
        this.plugin = plugin;
        this.commands = new ShapedPortalsCommands(plugin);
        this.director = DirectorEngineFactory.create(
                commands,
                DirectorEngineOptions.builder()
                        .contexts(contexts())
                        .textResolver(plugin.getLanguageService().directorResolver())
                        .build()
        );
    }

    public void register() {
        PluginCommand command = plugin.getCommand(ROOT_COMMAND);
        if (command == null) {
            throw new IllegalStateException("Missing shapedportals command in plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return false;
        }
        UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
        return LanguageAudience.call(audience, () -> executeCommand(sender, label, args));
    }

    private boolean executeCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("shapedportals.command") && requiresCommandPermission(args)) {
            plugin.getPresentationService().command(sender, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return true;
        }
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
                return executeLanguageCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            if (sendHelp(sender, args)) {
                return true;
            }
            List<String> arguments = normalizeOptionalArguments(Arrays.asList(args));
            DirectorExecutionResult result = director.execute(
                    new DirectorInvocation(new BukkitDirectorSender(sender, plugin.getLanguageService()), label, arguments)
            );
            if (!result.isHandled()) {
                safeSendRootHelp(sender);
            }
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals command execution failed", exception);
            plugin.getPresentationService().command(sender, ShapedMessages.COMMAND_FAILED, FeedbackTone.FAILURE);
        }
        return true;
    }

    private boolean executeLanguageCommand(CommandSender sender, String[] arguments) {
        return plugin.getLanguageSwitcher().command(sender, arguments);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return List.of();
        }
        if (!mayTabComplete(sender, args)) {
            return List.of();
        }
        UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
        return LanguageAudience.call(audience, () -> complete(sender, alias, args));
    }

    private List<String> complete(CommandSender sender, String alias, String[] args) {
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
                return plugin.getLanguageSwitcher().complete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            return director.tabComplete(new DirectorInvocation(
                    new BukkitDirectorSender(sender, plugin.getLanguageService()), alias, Arrays.asList(args))
            );
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals tab completion failed", exception);
            return List.of();
        }
    }

    private DirectorContextRegistry contexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return sender.sender();
            }
            return null;
        });
        contexts.register(Player.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender
                    && sender.sender() instanceof Player player) {
                return player;
            }
            return null;
        });
        return contexts;
    }

    private boolean sendHelp(CommandSender sender, String[] args) {
        Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(director, Arrays.asList(args));
        if (page.isEmpty()) {
            return false;
        }
        DirectorMiniMenu.deliver(sender, page.get(), ChatMenuStyle.theme(),
                plugin.getLanguageService().directorResolver());
        return true;
    }

    private void sendRootHelp(CommandSender sender) {
        Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(director, List.of());
        page.ifPresent(helpPage -> DirectorMiniMenu.deliver(sender, helpPage,
                ChatMenuStyle.theme(), plugin.getLanguageService().directorResolver()));
    }

    private void safeSendRootHelp(CommandSender sender) {
        try {
            sendRootHelp(sender);
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals help rendering failed", exception);
        }
    }

    private boolean requiresCommandPermission(String[] args) {
        if (args.length == 0) {
            return true;
        }
        String subcommand = args[0];
        return !subcommand.equalsIgnoreCase("config")
                && !subcommand.equalsIgnoreCase("language")
                && !subcommand.equalsIgnoreCase("debug")
                && !subcommand.equalsIgnoreCase("portals")
                && !subcommand.equalsIgnoreCase("teleport")
                && !subcommand.equalsIgnoreCase("tp");
    }

    private boolean mayTabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return sender.hasPermission("shapedportals.command");
        }
        String subcommand = args[0];
        if (subcommand.equalsIgnoreCase("portals")) {
            return sender.hasPermission("shapedportals.portals");
        }
        if (subcommand.equalsIgnoreCase("teleport") || subcommand.equalsIgnoreCase("tp")) {
            return sender.hasPermission("shapedportals.teleport")
                    || sender.hasPermission("shapedportals.portals");
        }
        return sender.hasPermission("shapedportals.command") || !requiresCommandPermission(args);
    }

    static List<String> normalizeOptionalArguments(List<String> arguments) {
        if (arguments.size() != 2 || arguments.get(1).contains("=")) {
            return List.copyOf(arguments);
        }
        String command = arguments.get(0).toLowerCase(Locale.ROOT);
        String parameter = switch (command) {
            case "portals" -> "page";
            case "teleport", "tp" -> "portal";
            default -> null;
        };
        if (parameter == null) {
            return List.copyOf(arguments);
        }
        ArrayList<String> normalized = new ArrayList<>(arguments);
        normalized.set(1, parameter + "=" + arguments.get(1));
        return List.copyOf(normalized);
    }

    private record BukkitDirectorSender(CommandSender sender, LanguageService language) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.isBlank()) {
                ComponentMessenger.send(sender, ComponentText.markup(language.render(sender, ShapedMessages.PREFIX))
                        .append(ComponentText.literal(message)));
            }
        }
    }
}
