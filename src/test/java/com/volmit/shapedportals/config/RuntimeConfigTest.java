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
        assertThat(runtime.endPortalCreation()).isTrue();
        assertThat(runtime.endScanLimits().minimumInteriorBlocks()).isEqualTo(1);
        assertThat(runtime.endScanLimits().maximumInteriorBlocks()).isEqualTo(256);
        assertThat(runtime.endInteriorMaterials()).containsExactlyInAnyOrder(
                Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
        assertThat(runtime.endCreationSound()).isTrue();
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
        config.presentation.netherCreationNotices = java.util.List.of("title");
        config.presentation.endCreationNotices = java.util.List.of("boss_bar", "chat");

        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.commandOverlays()).containsExactlyInAnyOrder(
                PresentationChannel.TITLE, PresentationChannel.BOSS_BAR);
        assertThat(runtime.portalNotices()).containsExactlyInAnyOrder(
                PresentationChannel.CHAT, PresentationChannel.ACTION_BAR);
        assertThat(runtime.netherCreationNotices()).containsExactly(PresentationChannel.TITLE);
        assertThat(runtime.endCreationNotices()).containsExactlyInAnyOrder(
                PresentationChannel.BOSS_BAR, PresentationChannel.CHAT);
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

    @Test
    void validatesEndPortalLimitsAndSoundProfile() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.portal.endMaximumInteriorBlocks = 4097;

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endMaximumInteriorBlocks");

        config.portal.endMaximumInteriorBlocks = 256;
        config.effects.endCreationSoundType = "BLOCK_END_PORTAL_SPAWN";
        RuntimeConfig runtime = RuntimeConfig.from(config);

        assertThat(runtime.endCreationSoundType()).isNotNull();
    }

    @Test
    void rejectsPortalBlocksAsReplaceableEndInteriors() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.portal.endInteriorMaterials.add("END_PORTAL_FRAME");

        assertThatThrownBy(() -> RuntimeConfig.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("END_PORTAL_FRAME");
    }
}
