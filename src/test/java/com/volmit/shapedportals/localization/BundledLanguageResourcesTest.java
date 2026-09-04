package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BundledLanguageResourcesTest {
    private static final Path LANGUAGE_ROOT = Path.of("src/main/resources/languages");
    private static final Pattern MINI_MESSAGE_FORMATTING = Pattern.compile(
            "</?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|bold|underlined|italic|strikethrough|obfuscated|gradient(?::[^>]*)?|newline)>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AMPERSAND_FORMATTING = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    @Test
    void providesExactlyTheCanonicalRemoteLanguageSources() throws Exception {
        List<String> expected = VolmitLocales.nonEnglish().stream()
                .map(locale -> locale + ".toml")
                .sorted()
                .toList();
        List<String> actual;
        try (Stream<Path> resources = Files.list(LANGUAGE_ROOT)) {
            actual = resources
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".toml"))
                    .sorted()
                    .toList();
        }

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(VolmitLocales.nonEnglish()).contains("ja-JP", "vi_VI");
        assertThat(actual).contains("ja-JP.toml", "vi_VI.toml");
    }

    @Test
    void everyRemoteLocaleSourceIsCompleteValidAndMeaningfullyTranslated() throws Exception {
        MessageCatalog catalog = ShapedMessages.catalog();
        Set<String> expectedPlaceholders = catalog.keys().stream()
                .flatMap(key -> key.placeholders().stream())
                .collect(Collectors.toSet());
        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            String content = Files.readString(resource);
            String header = content.substring(0, content.indexOf("\n[runtime]"));
            Map<String, String> messages = TomlLanguageParser.parseText(content);

            assertThat(header)
                    .describedAs("localized header in %s", resource)
                    .doesNotContain("Every message is editable")
                    .doesNotContain("Placeholders are message-specific");
            assertThat(placeholders(header))
                    .describedAs("placeholder reference in %s", resource)
                    .containsExactlyInAnyOrderElementsOf(expectedPlaceholders);
            assertThat(content)
                    .describedAs("sectioned TOML in %s", resource)
                    .contains("[runtime]", "[language.menu]", "[portal.navigation.list]")
                    .doesNotContain("[messages]");
            assertThat(messages.get("portal.navigation.list.hover"))
                    .describedAs("two-line portal hover in %s", resource)
                    .contains("\n");
            assertThat(messages.get("portal.navigation.list.entry").lines().toList())
                    .describedAs("three-line portal card in %s", resource)
                    .hasSize(3)
                    .allMatch(line -> !line.isBlank());
            assertThat(messages.keySet())
                    .describedAs("catalog coverage in %s", resource)
                    .containsExactlyInAnyOrderElementsOf(catalog.ids());
            for (MessageKey key : BukkitLanguageMessages.keys()) {
                assertThat(messages.get(key.id()))
                        .describedAs("translated shared language menu key %s in %s", key.id(), resource)
                        .isNotEqualTo(((TextKey) key).english());
            }

            int changed = 0;
            int ampersandFormatted = 0;
            List<String> invalid = new ArrayList<>();
            for (MessageKey key : catalog.keys()) {
                String template = messages.get(key.id());
                if (template == null || template.isBlank()) {
                    invalid.add(key.id() + " is blank or not text");
                    continue;
                }
                if (template.contains("\uFFFD")) {
                    invalid.add(key.id() + " contains a replacement character");
                }
                if (MINI_MESSAGE_FORMATTING.matcher(template).find()) {
                    invalid.add(key.id() + " uses MiniMessage formatting instead of classic ampersand codes");
                }
                if (AMPERSAND_FORMATTING.matcher(template).find()) {
                    ampersandFormatted++;
                }
                if (!new TextValue(template).placeholders().equals(key.placeholders())) {
                    invalid.add(key.id() + " changed placeholders");
                }
                if (key instanceof TextKey textKey && !template.equals(textKey.english())) {
                    changed++;
                }
            }

            assertThat(invalid).describedAs("translation integrity in %s", resource).isEmpty();
            assertThat(changed)
                    .describedAs("meaningful non-English coverage in %s", resource)
                    .isGreaterThanOrEqualTo(catalog.keys().size() * 3 / 4);
            assertThat(ampersandFormatted)
                    .describedAs("classic ampersand formatting in %s", resource)
                    .isGreaterThan(0);
        }
    }

    private Set<String> placeholders(String header) {
        HashSet<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(header);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return Set.copyOf(placeholders);
    }
}
