package com.volmit.shapedportals.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigTest {
    @Test
    void defaultsAreValidAndDetached() {
        ShapedPortalsConfig source = new ShapedPortalsConfig();

        RuntimeConfig runtime = RuntimeConfig.from(source);
        source.portal.frameMaterials.clear();

        assertThat(runtime.frameMaterials()).containsExactlyInAnyOrder(Material.OBSIDIAN, Material.CRYING_OBSIDIAN);
        assertThat(runtime.scanLimits().minimumInteriorBlocks()).isEqualTo(2);
        assertThat(runtime.scanLimits().maximumInteriorBlocks()).isEqualTo(256);
        assertThat(runtime.debugUploadEnabled()).isTrue();
        assertThat(runtime.metricsEnabled()).isTrue();
        assertThat(runtime.requireCreatePermission()).isTrue();
    }

    @Test
    void carriesTheExplicitMetricsChoiceIntoTheRuntimeSnapshot() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.metrics.enabled = false;

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.metricsEnabled()).isFalse();
    }

    @Test
    void carriesTheExplicitDebugUploadOptOutIntoTheRuntimeSnapshot() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.debug.uploadEnabled = false;

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.debugUploadEnabled()).isFalse();
    }

    @Test
    void rejectsFrameAndInteriorOverlap() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.portal.interiorMaterials.add("OBSIDIAN");

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void rejectsUnsafeLimitsInsteadOfSilentlyClamping() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.portal.maximumInteriorBlocks = 5000;

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumInteriorBlocks");
    }

    @Test
    void rejectsPathTraversalLanguageNames() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.general.language = "../secrets";

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale-safe");
    }

    @Test
    void parsesOptionalPresentationChannels() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.presentation.commandOverlays = java.util.List.of("title", "boss_bar");
        config.presentation.portalNotices = java.util.List.of("chat", "action_bar");

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.commandOverlays()).containsExactlyInAnyOrder(
                PresentationChannel.TITLE, PresentationChannel.BOSS_BAR);
        assertThat(runtime.portalNotices()).containsExactlyInAnyOrder(
                PresentationChannel.CHAT, PresentationChannel.ACTION_BAR);
    }

    @Test
    void rejectsChatAsACommandOverlay() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.presentation.commandOverlays = java.util.List.of("CHAT");

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("always enabled");
    }

    @Test
    void acceptsNamespacedSoundKeys() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.effects.creationSoundType = "minecraft:block.end_portal.spawn";

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.creationSoundType()).isNotNull();
    }

    @Test
    void acceptsLegacyEnumStyleSoundNamesOnCurrentConfigurations() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.effects.creationSoundType = "BLOCK_END_PORTAL_SPAWN";

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.creationSoundType()).isNotNull();
    }
}
