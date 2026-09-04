package com.volmit.shapedportals.gui;

import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.localization.ShapedMessages;
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
                .containsExactlyInAnyOrder(45, 53)
                .doesNotContainAnyElementsOf(ConfigEditorGui.rootCategorySlots().keySet());
    }

    @Test
    void categorySettingsFitAboveTheNavigationRow() {
        assertThat(ConfigEditorGui.maximumCategorySize()).isLessThanOrEqualTo(45);
    }

    @Test
    void configSaveResultsDescribeThePreviousAndAppliedValues() {
        assertThat(ShapedMessages.CONFIG_SAVED.placeholders())
                .containsExactlyInAnyOrder("prefix", "setting", "old", "new");
    }

}
