package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class MessagePrefixTest {
    @TempDir
    Path directory;

    @Test
    void editedPrefixAppliesToChatInlineNamesAndEditorPreviewsWithoutLeakingStyles() throws Exception {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            Path file = service.languageFile("en_US").toPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    [runtime]
                    prefix = "&c&lLocal"
                    [command.feedback.debug]
                    started = "{prefix}&r &7› &7Capturing {prefix} diagnostics."
                    """);
            service.install(service.prepare("en_US"));

            ComponentText chat = service.render(ShapedMessages.DEBUG_STARTED);
            assertThat(chat.plain()).isEqualTo("Local › Capturing Local diagnostics.");
            assertThat(chat.legacy()).contains("§r §7› Capturing ", "§7 diagnostics.");
            assertThat(service.renderWithoutPrefix(ShapedMessages.DEBUG_STARTED, MessageArgs.empty()).plain())
                    .isEqualTo("Capturing Local diagnostics.");
            assertThat(ComponentText.markup(service.directorResolver().resolve(ShapedMessages.COMMAND_ROOT)).plain())
                    .isEqualTo("Local help and administration");
            assertThat(service.render(ShapedMessages.GUI_ROOT_TITLE).plain())
                    .isEqualTo("Local › Configuration");
            assertThat(service.editableMessages("en_US").stream()
                    .filter(message -> message.id().equals(ShapedMessages.DEBUG_STARTED.id()))
                    .map(message -> ComponentText.markup(message.previewValue()).plain()))
                    .containsExactly("Local › Capturing Local diagnostics.");

            Files.writeString(file, "[runtime]\nprefix = \"&aChanged\"\n");
            service.install(service.prepare("en_US"));
            assertThat(service.render(ShapedMessages.VERSION,
                    MessageArgs.builder().untrusted("version", "3.2.1").build()).plain())
                    .isEqualTo("Changed v3.2.1");
        } finally {
            service.close();
        }
    }

    @Test
    void versionUsesOneHelpGradientAcrossTheEditedNameAndVersion() throws Exception {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            Path file = service.languageFile("en_US").toPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "[runtime]\nprefix = \"&c&l&nLocal\"\n");
            service.install(service.prepare("en_US"));

            MessageArgs arguments = MessageArgs.builder().untrusted("version", "3.2.1").build();
            ComponentText expected = ComponentText.markup(DirectorMiniMenu.version("Local", "3.2.1", ChatMenuStyle.theme()));
            ComponentText version = service.render(ShapedMessages.VERSION, arguments);
            assertThat(version.plain()).isEqualTo("Local v3.2.1");
            assertThat(version.legacy()).isEqualTo(expected.legacy()).doesNotContain("§l", "§n");
            assertThat(service.renderWithoutPrefix(ShapedMessages.VERSION, arguments).legacy()).isEqualTo(expected.legacy());
            assertThat(ComponentText.component(MiniMessage.miniMessage().deserialize(
                    service.directorResolver().resolve(ShapedMessages.VERSION, arguments))).legacy())
                    .isEqualTo(expected.legacy());
            assertThat(service.editableMessages("en_US").stream()
                    .filter(message -> message.id().equals(ShapedMessages.VERSION.id()))
                    .map(message -> ComponentText.component(MiniMessage.miniMessage().deserialize(message.previewValue())).legacy()))
                    .containsExactly(ComponentText.markup(DirectorMiniMenu.version("Local", "[version]", ChatMenuStyle.theme())).legacy());
        } finally {
            service.close();
        }
    }

    @Test
    void prefixedMessagesKeepUntrustedFormattingLiteral() {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            String portal = "&4[12ABef]<red>unsafe</red>";
            assertThat(service.render(ShapedMessages.PORTAL_NOT_FOUND,
                    MessageArgs.builder().untrusted("portal", portal).build()).plain())
                    .isEqualTo("ShapedPortals › No managed portal matches " + portal + ".");
            String serialized = service.directorResolver().resolve(ShapedMessages.PORTAL_NOT_FOUND,
                    MessageArgs.builder().untrusted("portal", portal + "§c").build());
            assertThat(ComponentText.component(MiniMessage.miniMessage().deserialize(serialized)).plain())
                    .isEqualTo("ShapedPortals › No managed portal matches " + portal + ".");
        } finally {
            service.close();
        }
    }

    @Test
    void languagePreparationUsesTheEditedOwnNameAndPreservesOtherTargets() throws Exception {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            Path file = service.languageFile("en_US").toPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "[runtime]\nprefix = \"&6Custom\"\n");
            service.install(service.prepare("en_US"));
            TextKey preparing = (TextKey) ShapedMessages.catalog().require("language.selection.preparing");
            assertThat(service.render(preparing,
                    MessageArgs.builder().untrusted("target", "ShapedPortals").untrusted("locale", "en_US").build()).plain())
                    .isEqualTo("Custom › Preparing language en_US for Custom...");
            assertThat(service.render(preparing,
                    MessageArgs.builder().untrusted("target", "OtherProvider").untrusted("locale", "en_US").build()).plain())
                    .isEqualTo("Custom › Preparing language en_US for OtherProvider...");
        } finally {
            service.close();
        }
    }

    @Test
    void sharedLanguageFeedbackPreservesTheTargetProviderName() {
        LanguageService service = new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
        try {
            assertThat(service.render(BukkitLanguageMessages.SERVER_SELECTED,
                    MessageArgs.builder().untrusted("plugin", "OtherProvider").untrusted("locale", "de_DE").build()).plain())
                    .startsWith("OtherProvider › ")
                    .doesNotContain("ShapedPortals");
        } finally {
            service.close();
        }
    }

    @Test
    void everyLocaleUsesOneEditableProductNameAndResolvesEveryTemplate() throws Exception {
        for (String locale : VolmitLocales.nonEnglish()) {
            LanguageService service = new LanguageService(directory.resolve(locale).toFile(), Logger.getAnonymousLogger());
            try {
                Path target = service.languageFile(locale).toPath();
                Files.createDirectories(target.getParent());
                String source = Files.readString(Path.of("src/main/resources/languages", locale + ".toml"));
                Map<String, String> messages = TomlLanguageParser.parseText(source);
                assertThat(messages.get(ShapedMessages.PREFIX.id())).isEqualTo(ShapedMessages.PREFIX.english());
                assertThat(messages.get(ShapedMessages.VERSION.id())).isEqualTo(ShapedMessages.VERSION.english());
                Files.writeString(target, source.replace(ShapedMessages.PREFIX.english(), "&6&lLocaleName"));
                service.install(service.prepare(locale));
                assertThat(service.render(ShapedMessages.VERSION,
                        MessageArgs.builder().untrusted("version", "3.2.1").build()).legacy())
                        .describedAs("full version gradient in %s", locale)
                        .isEqualTo(ComponentText.markup(DirectorMiniMenu.version("LocaleName", "3.2.1", ChatMenuStyle.theme())).legacy());
                for (MessageKey key : ShapedMessages.catalog().keys()) {
                    if (key.equals(ShapedMessages.PREFIX)) {
                        continue;
                    }
                    assertThat(messages.get(key.id())).describedAs("%s / %s", locale, key.id())
                            .doesNotContain("ShapedPortals", "{plugin}");
                    MessageArgs.Builder arguments = MessageArgs.builder();
                    for (String placeholder : key.placeholders()) {
                        if (!placeholder.equals("prefix")) {
                            arguments.untrusted(placeholder, "sample-" + placeholder);
                        }
                    }
                    assertThat(service.render((TextKey) key, arguments.build()).plain())
                            .describedAs("%s / %s", locale, key.id())
                            .doesNotContain("{prefix}", "ShapedPortals", "sample-prefix");
                }
                assertThat(ComponentText.markup(service.directorResolver().resolve(BukkitLanguageMessages.SERVER_SELECTED,
                        MessageArgs.builder().untrusted("plugin", "ShapedPortals").untrusted("locale", locale).build())).plain())
                        .startsWith("LocaleName › ");
            } finally {
                service.close();
            }
        }
    }
}
