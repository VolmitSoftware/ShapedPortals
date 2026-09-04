package com.volmit.shapedportals.presentation;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.plugin.ComponentText;

public final class ChatMenuStyle {
    private static final DirectorMiniMenu.Theme THEME = new DirectorMiniMenu.Theme(
            "#6f2dbd",
            "#d16ba5",
            "#31104f",
            "#7d3cc8",
            "#dec8f5",
            "#ff6666",
            "#d9a7ff",
            "#9370aa"
    );

    private ChatMenuStyle() {
    }

    public static DirectorMiniMenu.Theme theme() {
        return THEME;
    }

    public static ComponentText entry(ComponentText content) {
        return ComponentText.markup("<" + THEME.muted() + ">⇀</" + THEME.muted() + "> ")
                .append(content);
    }

    public static ComponentText commandHover(String title, String description, String usage) {
        return ComponentText.markup(
                "<" + THEME.primaryRight() + ">" + DirectorMiniMenu.escapeText(title)
                        + "</" + THEME.primaryRight() + "><reset>\n"
                        + "<" + THEME.description() + ">✎ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(description) + "</font></" + THEME.description() + "><reset>\n"
                        + "<" + THEME.optional() + ">✒ <font:minecraft:uniform>"
                        + DirectorMiniMenu.escapeText(usage) + "</font></" + THEME.optional() + ">"
        );
    }
}
