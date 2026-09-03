package com.volmit.shapedportals.gui;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigEditorGuiTest {
    @Test
    void exposesEveryPersistedConfigurationField() {
        Set<String> persistedPaths = new LinkedHashSet<>();
        for (Field section : ShapedPortalsConfig.class.getFields()) {
            for (Field setting : section.getType().getFields()) {
                persistedPaths.add(section.getName() + "." + setting.getName());
            }
        }

        assertThat(ConfigEditorGui.editablePaths()).containsExactlyInAnyOrderElementsOf(persistedPaths);
    }

    @Test
    void languageSettingUsesTheLocalePicker() {
        assertThat(ConfigEditorGui.languageUsesPicker()).isTrue();
        assertThat(ConfigEditorGui.languageStatus(true)).isEqualTo("&a✔&r");
        assertThat(ConfigEditorGui.languageStatus(false)).isEqualTo("&8•&r");
        assertThat(ConfigEditorGui.languageScopeLinksVisible(1)).isTrue();
        assertThat(ConfigEditorGui.languageScopeLinksVisible(2)).isFalse();
        assertThat(ConfigEditorGui.languageOptionText(true, "en_US", "English (United States)").plain())
                .isEqualTo("✔ en_US English (United States)")
                .doesNotContain("—", "--");
        assertThat(ConfigEditorGui.languageCommand("self", "fr_FR"))
                .isEqualTo("/shapedportals language self fr_FR");
        assertThat(ConfigEditorGui.languageCommand("server", "nl_NL"))
                .isEqualTo("/shapedportals language server nl_NL");
        assertThat(ConfigEditorGui.failureSettingName(true, " nl_NL ", "Active language locale"))
                .isEqualTo("nl_NL");
        assertThat(ConfigEditorGui.failureSettingName(false, null, "Debug uploads"))
                .isEqualTo("Debug uploads");
    }

    @Test
    void usesAFullChestWithACompleteLanguagePage() {
        assertThat(ConfigEditorGui.inventorySize()).isEqualTo(54);
        assertThat(ConfigEditorGui.inventorySize() % 9).isZero();
        assertThat(ConfigEditorGui.languageEditorPageSize()).isEqualTo(45);
    }

    @Test
    void everyRenderedCategoryUsesTheClickRoutingMap() {
        assertThat(ConfigEditorGui.rootCategorySlots())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        19, "GENERAL",
                        21, "PORTAL",
                        23, "EFFECTS",
                        25, "HOT_RELOAD",
                        28, "INTEGRITY",
                        30, "PRESENTATION",
                        32, "DEBUG",
                        34, "LANGUAGES"
                ));
        assertThat(ConfigEditorGui.navigationSlots())
                .doesNotContainAnyElementsOf(ConfigEditorGui.rootCategorySlots().keySet());
    }

    @Test
    void categorySettingsFitAboveTheNavigationRow() {
        assertThat(ConfigEditorGui.maximumCategorySize()).isLessThanOrEqualTo(45);
    }

    @Test
    void decodesOnlySupportedLanguageEditorEscapes() {
        assertThat(ConfigEditorGui.decodeLanguageInput("first\\nsecond"))
                .isEqualTo("first\nsecond");
        assertThat(ConfigEditorGui.decodeLanguageInput("path\\\\name\\tvalue"))
                .isEqualTo("path\\name\\tvalue");
    }

    @Test
    void wrapsFormattedLanguagePreviewsIntoBoundedLore() {
        List<String> lines = ConfigEditorGui.wrapLegacyPreview(
                "§d12345 67890 abcde\n§aSecond formatted line", 10, 6);

        assertThat(lines).hasSizeGreaterThan(2).hasSizeLessThanOrEqualTo(6);
        assertThat(lines).allSatisfy(line -> assertThat(ChatColor.stripColor(line).length())
                .isLessThanOrEqualTo(10));
        assertThat(lines.get(1)).startsWith("§d");
        assertThat(lines).anyMatch(line -> line.contains("Second"));
    }

    @Test
    void truncatesExcessiveLanguagePreviewHeight() {
        List<String> lines = ConfigEditorGui.wrapLegacyPreview("x".repeat(200), 10, 3);

        assertThat(lines).hasSize(3);
        assertThat(lines.get(2)).endsWith("§8…");
    }

    @Test
    void configSaveResultsUseTheDirectorMenuFrame() {
        assertThat(ConfigEditorGui.configResultMenu("saved").page())
                .isEqualTo(new DirectorMiniMenu.ContentPage(1, 1, 0, 1, 1));
    }

    @Test
    void languagePromptListsTheExactSortedPlaceholders() {
        assertThat(ConfigEditorGui.languageVariables(Set.of("prefix", "attempts", "created")))
                .isEqualTo("{attempts} {created} {prefix}");
        assertThat(ConfigEditorGui.languageVariables(Set.of())).isEqualTo("—");
    }
}
