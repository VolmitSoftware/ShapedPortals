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

    public static void print(ShapedPortals plugin) {
        try {
            printSplash(plugin);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "ShapedPortals splash screen failed to render", exception);
        }
    }

    private static void printSplash(ShapedPortals plugin) {
        ChatColor fill = ChatColor.of("#35135f");
        ChatColor edge = ChatColor.of("#6f35c5");
        ChatColor meta = ChatColor.of("#bba8cf");
        ChatColor accent = ChatColor.of("#c778ff");
        String[] column = details(plugin.getDescription().getVersion(),
                SplashScreenSupport.serverVersionWithoutMcSuffix(),
                Integer.toString(SplashScreenSupport.javaMajorVersion()), SplashScreenSupport.startupDate());
        for (int index = 1; index < column.length; index++) {
            column[index] = meta + "   " + column[index]
                    .replace("ShapedPortals", accent + "ShapedPortals" + meta)
                    .replace(": ", ": " + accent)
                    .replace(" | ", meta + " | ");
        }

        StringBuilder splash = new StringBuilder("\n");
        for (int row = 0; row < ART.length; row++) {
            splash.append(colorize(ART[row], fill, edge)).append(column[row]).append('\n');
        }
        ComponentLog.logLegacy(plugin, plugin.getLogger(), "", Level.INFO, splash.toString(), null);
    }

    static String[] details(String version, String server, String javaVersion, String date) {
        return new String[]{
                "",
                "ShapedPortals, Free-form Nether and End Portals",
                "Version: " + version,
                "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                "Server: " + server + " | MC Support: 1.20.1 - 26.x",
                "Java: " + javaVersion + " | Date: " + date
        };
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
