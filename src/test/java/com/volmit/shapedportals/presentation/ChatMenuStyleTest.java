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
}
