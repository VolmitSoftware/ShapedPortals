package com.volmit.shapedportals.util;

import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import com.volmit.shapedportals.ShapedPortals;
import net.md_5.bungee.api.ChatColor;

import java.util.logging.Level;

public final class SplashScreen {
    private static final String[] ART = {
            "███████╗██╗  ██╗ █████╗ ██████╗ ███████╗██████╗ ",
            "██╔════╝██║  ██║██╔══██╗██╔══██╗██╔════╝██╔══██╗",
            "███████╗███████║███████║██████╔╝█████╗  ██║  ██║",
            "╚════██║██╔══██║██╔══██║██╔═══╝ ██╔══╝  ██║  ██║",
            "███████║██║  ██║██║  ██║██║     ███████╗██████╔╝",
            "╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝     ╚══════╝╚═════╝ "
    };

    private SplashScreen() {
    }

    public static void print(ShapedPortals plugin, boolean success, long startupMillis) {
        try {
            printSplash(plugin, success, startupMillis);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals splash screen failed to render", exception);
        }
    }

    private static void printSplash(ShapedPortals plugin, boolean success, long startupMillis) {
        ChatColor fill = ChatColor.of("#35135f");
        ChatColor edge = ChatColor.of("#6f35c5");
        ChatColor meta = ChatColor.of("#bba8cf");
        ChatColor accent = ChatColor.of("#c778ff");
        ChatColor statusColor = success ? ChatColor.GREEN : ChatColor.RED;
        String status = success ? "READY" : "DEGRADED";
        String version = plugin.getDescription().getVersion();
        String[] column = {
                "",
                accent + "   ShapedPortals, " + meta + "Free-form Nether and End portals [" + SplashScreenSupport.releaseTrain(version) + " RELEASE]",
                meta + "   Version: " + accent + version + meta + " | By: " + accent + "Volmit Software",
                meta + "   Server: " + accent + SplashScreenSupport.serverVersionWithoutMcSuffix() + meta + " | MC Support: " + accent + "1.20.1+",
                meta + "   Java: " + accent + SplashScreenSupport.javaMajorVersion() + meta + " | Scheduler: " + accent + plugin.schedulerName(),
                meta + "   Date: " + accent + SplashScreenSupport.startupDate() + meta + " | Startup: " + statusColor + status + meta + " in " + accent + startupMillis + " ms"
        };

        StringBuilder splash = new StringBuilder("\n");
        for (int row = 0; row < ART.length; row++) {
            splash.append(colorize(ART[row], fill, edge)).append(column[row]).append('\n');
        }
        ComponentLog.logLegacy(plugin, plugin.getLogger(), "", Level.INFO, splash.toString(), null);
    }

    private static String colorize(String row, ChatColor fill, ChatColor edge) {
        StringBuilder output = new StringBuilder(row.length() * 2);
        boolean inFill = false;
        boolean inEdge = false;
        for (int index = 0; index < row.length(); index++) {
            char glyph = row.charAt(index);
            if (glyph == '█') {
                if (!inFill) {
                    output.append(fill);
                    inFill = true;
                    inEdge = false;
                }
            } else if (glyph != ' ' && !inEdge) {
                output.append(edge);
                inEdge = true;
                inFill = false;
            }
            output.append(glyph);
        }
        return output.toString();
    }
}
