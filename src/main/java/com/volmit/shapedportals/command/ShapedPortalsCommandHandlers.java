package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import com.volmit.shapedportals.ShapedPortals;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShapedPortalsCommandHandlers {
    private ShapedPortalsCommandHandlers() {
    }

    public static final class PortalId implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            ShapedPortals plugin = JavaPlugin.getPlugin(ShapedPortals.class);
            return new KList<>(plugin.getNavigationService().suggestions());
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            if (input == null || input.isBlank()) {
                throw new DirectorParsingException("Portal identifier cannot be empty");
            }
            return input.trim();
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }
}
