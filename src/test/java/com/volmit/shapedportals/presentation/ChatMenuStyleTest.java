package com.volmit.shapedportals.presentation;

import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ChatMenuStyleTest {
    @Test
    void usesTheShapedPortalsBrandWithReadableMenuText() {
        assertThat(ChatMenuStyle.theme().primaryLeft()).isEqualTo(DirectorThemes.SHAPEDPORTALS.getPrimaryHex());
        assertThat(ChatMenuStyle.theme().primaryRight()).isEqualTo(DirectorThemes.SHAPEDPORTALS.getSecondaryHex());
        assertThat(ChatMenuStyle.theme().description()).isEqualTo("#dec8f5");
        assertThat(ChatMenuStyle.theme().optional()).isEqualTo("#d9a7ff");
        assertThat(ChatMenuStyle.theme().muted()).isEqualTo("#9370aa");
    }

    @Test
    void usesTheVolmitSuiteHelpRowMarker() {
        assertThat(ChatMenuStyle.entry(ComponentText.literal("Entry")).plain()).isEqualTo("⇀ Entry");
    }

    @Test
    void commandHoversUseTheDirectorHelpPaletteAndStructure() {
        ComponentText hover = ChatMenuStyle.commandHover(
                "Language",
                "Choose the messages you see",
                "/sp language self <locale>"
        );

        assertThat(hover.plain())
                .isEqualTo("Language\n✎ Choose the messages you see\n✒ /sp language self <locale>");
        assertThat(hover.miniMessage().toLowerCase())
                .contains("<#35135f>language</#35135f>")
                .contains("<#dec8f5>✎ <font:uniform>choose the messages you see</font></#dec8f5>")
                .contains("<#d9a7ff>✒ <font:uniform>/sp language self");
    }
}
