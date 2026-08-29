package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import com.volmit.shapedportals.localization.ShapedMessages;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDescriptionCatalogTest {
    @Test
    void directorDescriptionsMatchCatalogDefinitions() {
        MessageCatalog catalog = ShapedMessages.catalog();
        assertDescription(ShapedPortalsCommands.class.getAnnotation(Director.class), catalog);
        for (Method method : ShapedPortalsCommands.class.getDeclaredMethods()) {
            Director director = method.getAnnotation(Director.class);
            if (director != null) {
                assertDescription(director, catalog);
            }
        }
    }

    @Test
    void omitsRedundantManualReloadAndVersionCommands() {
        assertThat(Arrays.stream(ShapedPortalsCommands.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Director.class))
                .filter(Objects::nonNull)
                .map(Director::name))
                .doesNotContain("reload", "version");
    }

    private void assertDescription(Director director, MessageCatalog catalog) {
        MessageKey key = catalog.require(director.descriptionKey());

        assertThat(key).isInstanceOf(TextKey.class);
        assertThat(((TextKey) key).english()).isEqualTo(director.description());
    }
}
