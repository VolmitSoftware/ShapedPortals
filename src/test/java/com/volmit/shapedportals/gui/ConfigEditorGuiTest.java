package com.volmit.shapedportals.gui;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
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
        assertThat(ConfigEditorGui.failureSettingName(true, " nl_NL ", "Active language locale"))
                .isEqualTo("nl_NL");
        assertThat(ConfigEditorGui.failureSettingName(false, null, "Debug uploads"))
                .isEqualTo("Debug uploads");
    }

    @Test
    void usesAFullChest() {
        assertThat(ConfigEditorGui.inventorySize()).isEqualTo(54);
        assertThat(ConfigEditorGui.inventorySize() % 9).isZero();
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
    void configSaveResultsUseTheDirectorMenuFrame() {
        assertThat(ConfigEditorGui.configResultMenu("saved").page())
                .isEqualTo(new DirectorMiniMenu.ContentPage(1, 1, 0, 1, 1));
    }

}
