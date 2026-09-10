package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class DebugMessageLocalizationTest {
    @TempDir
    Path directory;

    @Test
    void resolvesEverySharedDebugMessageThroughThePluginResolver() {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            for (MessageKey definition : BukkitDebugMessages.keys()) {
                TextKey key = (TextKey) definition;
                MessageArgs.Builder arguments = MessageArgs.builder();
                for (String placeholder : key.placeholders()) {
                    arguments.untrusted(placeholder, "sample-" + placeholder);
                }
                MessageArgs values = arguments.build();

                assertThat(service.directorResolver().resolve(key, values))
                        .describedAs("shared debug message %s", key.id())
                        .isEqualTo(DirectorTextResolver.ENGLISH.resolve(key, values));
            }
        } finally {
            service.close();
        }
    }

    @Test
    void preservesEditedDebugMessagesAndUsesEnglishForMissingEntriesAfterReload() throws IOException {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            Path languageFile = service.languageFile("en_US").toPath();
            Files.createDirectories(languageFile.getParent());
            String edited = "[debug]\npreparing = \"Capturing {plugin} diagnostics\"\n";
            Files.writeString(languageFile, edited);

            service.install(service.prepare("en_US"));
            service.install(service.prepare("en_US"));

            assertThat(service.directorResolver().resolve(BukkitDebugMessages.PREPARING,
                    MessageArgs.builder().untrusted("plugin", "ShapedPortals").build()))
                    .isEqualTo("Capturing ShapedPortals diagnostics");
            assertThat(service.directorResolver().resolve(BukkitDebugMessages.PROVIDER_CLOSED))
                    .isEqualTo("The debug provider is closed.");
            assertThat(Files.readString(languageFile)).isEqualTo(edited);
        } finally {
            service.close();
        }
    }
}
