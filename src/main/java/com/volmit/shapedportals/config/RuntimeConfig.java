package com.volmit.shapedportals.config;

import com.volmit.shapedportals.geometry.PortalShapeScanner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.event.block.BlockIgniteEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record RuntimeConfig(
        ShapedPortalsConfig source,
        boolean enabled,
        String language,
        boolean requireCreatePermission,
        boolean failureFeedback,
        boolean updateNotifications,
        boolean metricsEnabled,
        Set<Material> frameMaterials,
        Set<Material> interiorMaterials,
        Set<String> ignitionCauses,
        Set<String> allowedWorlds,
        Set<String> deniedWorlds,
        long deduplicationMillis,
        PortalShapeScanner.ScanLimits scanLimits,
        boolean endPortalCreation,
        Set<Material> endInteriorMaterials,
        PortalShapeScanner.ScanLimits endScanLimits,
        boolean creationSound,
        Sound creationSoundType,
        float creationSoundVolume,
        float creationSoundPitch,
        boolean endCreationSound,
        Sound endCreationSoundType,
        float endCreationSoundVolume,
        float endCreationSoundPitch,
        boolean hotReloadEnabled,
        long hotReloadPollMillis,
        long hotReloadCooldownMillis,
        boolean hotReloadNotifyOperators,
        boolean integrityEnabled,
        long integrityCheckIntervalTicks,
        int maximumIntegrityChecksPerCycle,
        boolean splashScreen,
        boolean commandSounds,
        Set<PresentationChannel> commandOverlays,
        Set<PresentationChannel> portalNotices,
        Set<PresentationChannel> netherCreationNotices,
        Set<PresentationChannel> endCreationNotices,
        long overlayDurationTicks,
        int titleFadeInTicks,
        int titleStayTicks,
        int titleFadeOutTicks,
        boolean debugUploadEnabled
) {
    private static final int HARD_MAXIMUM_INTERIOR = 4096;
    private static final int HARD_MAXIMUM_DIMENSION = 512;
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");

    public RuntimeConfig {
        source = source.copy();
        frameMaterials = Set.copyOf(frameMaterials);
        interiorMaterials = Set.copyOf(interiorMaterials);
        endInteriorMaterials = Set.copyOf(endInteriorMaterials);
        ignitionCauses = Set.copyOf(ignitionCauses);
        allowedWorlds = Set.copyOf(allowedWorlds);
        deniedWorlds = Set.copyOf(deniedWorlds);
        commandOverlays = Set.copyOf(commandOverlays);
        portalNotices = Set.copyOf(portalNotices);
        netherCreationNotices = Set.copyOf(netherCreationNotices);
        endCreationNotices = Set.copyOf(endCreationNotices);
    }

    public static RuntimeConfig from(ShapedPortalsConfig config) {
        requireSections(config);
        String language = requireLocale(config.general.language);
        Set<Material> frames = parseMaterials(config.portal.frameMaterials, "portal.frameMaterials");
        Set<Material> interiors = parseMaterials(config.portal.interiorMaterials, "portal.interiorMaterials");
        Set<Material> endInteriors = parseMaterials(config.portal.endInteriorMaterials, "portal.endInteriorMaterials");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("portal.frameMaterials must contain at least one block material");
        }
        if (interiors.isEmpty()) {
            throw new IllegalArgumentException("portal.interiorMaterials must contain at least one block material");
        }
        if (interiors.contains(Material.NETHER_PORTAL)) {
            throw new IllegalArgumentException("portal.interiorMaterials cannot contain NETHER_PORTAL");
        }
        if (endInteriors.isEmpty()) {
            throw new IllegalArgumentException("portal.endInteriorMaterials must contain at least one block material");
        }
        if (endInteriors.contains(Material.END_PORTAL)) {
            throw new IllegalArgumentException("portal.endInteriorMaterials cannot contain END_PORTAL");
        }
        if (endInteriors.contains(Material.END_PORTAL_FRAME) || endInteriors.contains(Material.NETHER_PORTAL)) {
            throw new IllegalArgumentException(
                    "portal.endInteriorMaterials cannot contain END_PORTAL_FRAME or NETHER_PORTAL");
        }
        Set<Material> overlap = new LinkedHashSet<>(frames);
        overlap.retainAll(interiors);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Portal frame and interior materials overlap: " + overlap);
        }

        int minimum = inRange(config.portal.minimumInteriorBlocks, 1, HARD_MAXIMUM_INTERIOR,
                "portal.minimumInteriorBlocks");
        int maximum = inRange(config.portal.maximumInteriorBlocks, minimum, HARD_MAXIMUM_INTERIOR,
                "portal.maximumInteriorBlocks");
        int maximumWidth = inRange(config.portal.maximumWidth, 1, HARD_MAXIMUM_DIMENSION,
                "portal.maximumWidth");
        int maximumHeight = inRange(config.portal.maximumHeight, 1, HARD_MAXIMUM_DIMENSION,
                "portal.maximumHeight");
        int endMinimum = inRange(config.portal.endMinimumInteriorBlocks, 1, HARD_MAXIMUM_INTERIOR,
                "portal.endMinimumInteriorBlocks");
        int endMaximum = inRange(config.portal.endMaximumInteriorBlocks, endMinimum, HARD_MAXIMUM_INTERIOR,
                "portal.endMaximumInteriorBlocks");
        int endMaximumWidth = inRange(config.portal.endMaximumWidth, 1, HARD_MAXIMUM_DIMENSION,
                "portal.endMaximumWidth");
        int endMaximumLength = inRange(config.portal.endMaximumLength, 1, HARD_MAXIMUM_DIMENSION,
                "portal.endMaximumLength");
        long deduplicationMillis = inRange(config.portal.deduplicationMillis, 0L, 60_000L,
                "portal.deduplicationMillis");
        float volume = inRange(config.effects.creationSoundVolume, 0F, 4F,
                "effects.creationSoundVolume");
        float pitch = inRange(config.effects.creationSoundPitch, 0.5F, 2F,
                "effects.creationSoundPitch");
        float endVolume = inRange(config.effects.endCreationSoundVolume, 0F, 4F,
                "effects.endCreationSoundVolume");
        float endPitch = inRange(config.effects.endCreationSoundPitch, 0.5F, 2F,
                "effects.endCreationSoundPitch");
        long pollMillis = inRange(config.hotReload.pollIntervalMillis, 250L, 60_000L,
                "hotReload.pollIntervalMillis");
        long cooldownMillis = inRange(config.hotReload.cooldownMillis, 250L, 60_000L,
                "hotReload.cooldownMillis");
        long integrityInterval = inRange(config.integrity.checkIntervalTicks, 20L, 72_000L,
                "integrity.checkIntervalTicks");
        int integrityChecks = inRange(config.integrity.maximumChecksPerCycle, 1, 1024,
                "integrity.maximumChecksPerCycle");
        Set<PresentationChannel> commandOverlays = parseChannels(
                config.presentation.commandOverlays, "presentation.commandOverlays");
        if (commandOverlays.contains(PresentationChannel.CHAT)) {
            throw new IllegalArgumentException("presentation.commandOverlays cannot contain CHAT because command chat output is always enabled");
        }
        Set<PresentationChannel> portalNotices = parseChannels(
                config.presentation.portalNotices, "presentation.portalNotices");
        Set<PresentationChannel> netherCreationNotices = parseChannels(
                config.presentation.netherCreationNotices, "presentation.netherCreationNotices");
        Set<PresentationChannel> endCreationNotices = parseChannels(
                config.presentation.endCreationNotices, "presentation.endCreationNotices");
        long overlayDurationTicks = inRange(config.presentation.overlayDurationTicks, 10L, 600L,
                "presentation.overlayDurationTicks");
        int titleFadeInTicks = inRange(config.presentation.titleFadeInTicks, 0, 200,
                "presentation.titleFadeInTicks");
        int titleStayTicks = inRange(config.presentation.titleStayTicks, 1, 600,
                "presentation.titleStayTicks");
        int titleFadeOutTicks = inRange(config.presentation.titleFadeOutTicks, 0, 200,
                "presentation.titleFadeOutTicks");

        return new RuntimeConfig(
                config,
                config.general.enabled,
                language,
                config.general.requireCreatePermission,
                config.general.failureFeedback,
                config.general.updateNotifications,
                config.metrics.enabled,
                frames,
                interiors,
                parseIgnitionCauses(config.portal.ignitionCauses),
                normalizeWorlds(config.portal.allowedWorlds, "portal.allowedWorlds"),
                normalizeWorlds(config.portal.deniedWorlds, "portal.deniedWorlds"),
                deduplicationMillis,
                new PortalShapeScanner.ScanLimits(minimum, maximum, maximumWidth, maximumHeight),
                config.portal.endPortalCreation,
                endInteriors,
                new PortalShapeScanner.ScanLimits(endMinimum, endMaximum, endMaximumWidth, endMaximumLength),
                config.effects.creationSound,
                parseSound(config.effects.creationSoundType, "effects.creationSoundType"),
                volume,
                pitch,
                config.effects.endCreationSound,
                parseSound(config.effects.endCreationSoundType, "effects.endCreationSoundType"),
                endVolume,
                endPitch,
                config.hotReload.enabled,
                pollMillis,
                cooldownMillis,
                config.hotReload.notifyOperators,
                config.integrity.enabled,
                integrityInterval,
                integrityChecks,
                config.presentation.splashScreen,
                config.presentation.commandSounds,
                commandOverlays,
                portalNotices,
                netherCreationNotices,
                endCreationNotices,
                overlayDurationTicks,
                titleFadeInTicks,
                titleStayTicks,
                titleFadeOutTicks,
                config.debug.uploadEnabled
        );
    }

    public boolean allowsWorld(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        if (deniedWorlds.contains(name)) {
            return false;
        }
        return allowedWorlds.isEmpty() || allowedWorlds.contains(name);
    }

    public boolean allowsIgnition(String cause) {
        return cause != null && ignitionCauses.contains(cause.toUpperCase(Locale.ROOT));
    }

    private static void requireSections(ShapedPortalsConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        if (config.general == null || config.metrics == null || config.portal == null || config.effects == null
                || config.hotReload == null || config.integrity == null) {
            throw new IllegalArgumentException("Configuration sections cannot be null");
        }
        if (config.presentation == null || config.debug == null) {
            throw new IllegalArgumentException("Configuration sections cannot be null");
        }
    }

    private static String requireLocale(String locale) {
        if (locale == null || !LOCALE_PATTERN.matcher(locale).matches()) {
            throw new IllegalArgumentException("general.language must be a locale-safe name");
        }
        return locale;
    }

    private static Set<Material> parseMaterials(List<String> values, String path) {
        if (values == null) {
            throw new IllegalArgumentException(path + " cannot be null");
        }
        Set<Material> materials = new LinkedHashSet<>();
        for (String value : values) {
            Material material = value == null ? null : Material.matchMaterial(value.trim());
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException(path + " contains an unknown block material: " + value);
            }
            materials.add(material);
        }
        return materials;
    }

    private static Set<String> parseIgnitionCauses(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("portal.ignitionCauses must contain at least one cause");
        }
        Set<String> causes = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("portal.ignitionCauses cannot contain null");
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!normalized.equals("PLACED_FIRE")) {
                try {
                    BlockIgniteEvent.IgniteCause.valueOf(normalized);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown portal ignition cause: " + value, exception);
                }
            }
            causes.add(normalized);
        }
        return causes;
    }

    private static Set<String> normalizeWorlds(List<String> values, String path) {
        if (values == null) {
            throw new IllegalArgumentException(path + " cannot be null");
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(path + " cannot contain blank world names");
            }
            worlds.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return worlds;
    }

    private static Set<PresentationChannel> parseChannels(List<String> values, String path) {
        if (values == null) {
            throw new IllegalArgumentException(path + " cannot be null");
        }
        Set<PresentationChannel> channels = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(path + " cannot contain blank values");
            }
            try {
                channels.add(PresentationChannel.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + " contains an unknown channel: " + value, exception);
            }
        }
        return channels;
    }

    private static Sound parseSound(String value, String pathName) {
        if (value == null) {
            throw new IllegalArgumentException(pathName + " cannot be null");
        }
        String normalized = value.trim();
        String path = normalized.contains(":")
                ? normalized.substring(normalized.indexOf(':') + 1)
                : normalized;
        Sound constant = soundConstant(path.replace('.', '_').toUpperCase(Locale.ROOT));
        if (constant != null) {
            return constant;
        }
        if (Bukkit.getServer() == null) {
            throw new IllegalArgumentException("Unknown sound in " + pathName + ": " + value);
        }
        String keyText = normalized.contains(":")
                ? normalized.toLowerCase(Locale.ROOT)
                : "minecraft:" + path.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(keyText);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) {
            throw new IllegalArgumentException("Unknown sound in " + pathName + ": " + value);
        }
        return sound;
    }

    private static Sound soundConstant(String fieldName) {
        try {
            Object value = Sound.class.getField(fieldName).get(null);
            return value instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException | SecurityException exception) {
            return null;
        }
    }

    private static int inRange(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static long inRange(long value, long minimum, long maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static float inRange(float value, float minimum, float maximum, String path) {
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
