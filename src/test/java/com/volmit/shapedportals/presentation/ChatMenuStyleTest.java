package com.volmit.shapedportals.presentation;

import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ChatMenuStyleTest {
    @Test
    void usesTheReadableRiftPurpleMenuPalette() {
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
                .contains("<#d16ba5>language</#d16ba5>")
                .contains("<#dec8f5>✎ <font:uniform>choose the messages you see</font></#dec8f5>")
                .contains("<#d9a7ff>✒ <font:uniform>/sp language self");
    }
}
