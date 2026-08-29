package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.List;

public final class ShapedPortalsConfig {
    public General general = new General();
    public Metrics metrics = new Metrics();
    public Portal portal = new Portal();
    public Effects effects = new Effects();
    public HotReload hotReload = new HotReload();
    public Integrity integrity = new Integrity();
    public Presentation presentation = new Presentation();
    public Debug debug = new Debug();

    public ShapedPortalsConfig copy() {
        ShapedPortalsConfig copy = new ShapedPortalsConfig();
        copy.general = general.copy();
        copy.metrics = metrics.copy();
        copy.portal = portal.copy();
        copy.effects = effects.copy();
        copy.hotReload = hotReload.copy();
        copy.integrity = integrity.copy();
        copy.presentation = presentation.copy();
        copy.debug = debug.copy();
        return copy;
    }

    public static final class General {
        @ConfigDoc(value = "Master switch for shaped portal creation.", impact = "Existing managed portals continue to be maintained while creation is disabled.")
        public boolean enabled = true;
        @ConfigDoc(value = "Active locale stored as the directly editable languages/<locale>.toml file.", impact = "Repository locales are verified and downloaded only when missing; local changes are never automatically replaced, and missing entries use code-owned English.")
        public String language = "en_US";
        @ConfigDoc(value = "Require shapedportals.create from player igniters.", impact = "Non-player ignition follows the configured ignition causes.")
        public boolean requireCreatePermission = true;
        @ConfigDoc(value = "Tell player igniters why a shaped portal was not created.", impact = "Duplicate and vanilla-handled attempts remain quiet.")
        public boolean failureFeedback = true;

        private General copy() {
            General copy = new General();
            copy.enabled = enabled;
            copy.language = language;
            copy.requireCreatePermission = requireCreatePermission;
            copy.failureFeedback = failureFeedback;
            return copy;
        }
    }

    public static final class Metrics {
        @ConfigDoc(value = "Submit anonymous ShapedPortals usage statistics to bStats.", impact = "The global plugins/bStats/config.yml opt-out remains authoritative; changes apply immediately without a restart.")
        public boolean enabled = true;

        private Metrics copy() {
            Metrics copy = new Metrics();
            copy.enabled = enabled;
            return copy;
        }
    }

    public static final class Portal {
        @ConfigDoc(value = "Minimum connected interior cells in a shaped portal.", impact = "A value of two rejects ambiguous one-cell cavities.")
        public int minimumInteriorBlocks = 2;
        @ConfigDoc(value = "Maximum connected interior cells in a shaped portal.", impact = "The hard safety ceiling is 4096 cells.")
        public int maximumInteriorBlocks = 256;
        @ConfigDoc(value = "Maximum interior width in blocks.", impact = "Bounds long open scans and cross-chunk work.")
        public int maximumWidth = 64;
        @ConfigDoc(value = "Maximum interior height in blocks.", impact = "Bounds vertical scans within world height.")
        public int maximumHeight = 64;
        @ConfigDoc(value = "Blocks accepted when creating a new closed portal frame.", impact = "Existing portals keep the frame-material snapshot recorded when they were created.")
        public List<String> frameMaterials = new ArrayList<>(List.of("OBSIDIAN", "CRYING_OBSIDIAN"));
        @ConfigDoc(value = "Blocks that may be replaced inside a newly created portal.", impact = "NETHER_PORTAL cannot be added because vanilla creation takes priority.")
        public List<String> interiorMaterials = new ArrayList<>(List.of("AIR", "CAVE_AIR", "VOID_AIR", "FIRE", "SOUL_FIRE"));
        @ConfigDoc(value = "BlockIgniteEvent causes allowed to start a shaped portal, plus PLACED_FIRE for direct fire placement.", impact = "SPREAD and LAVA are excluded by default to prevent accidental portals.")
        public List<String> ignitionCauses = new ArrayList<>(List.of("FLINT_AND_STEEL", "FIREBALL", "PLACED_FIRE"));
        @ConfigDoc(value = "Optional world allow-list by world name.", impact = "An empty list allows every world not denied below.")
        public List<String> allowedWorlds = new ArrayList<>();
        @ConfigDoc(value = "World deny-list by world name.", impact = "Denied worlds always win over the allow-list.")
        public List<String> deniedWorlds = new ArrayList<>();
        @ConfigDoc(value = "Cooldown for duplicate ignition events at the same block.", impact = "Prevents BlockIgniteEvent and BlockPlaceEvent from double-processing one action.")
        public long deduplicationMillis = 1500L;

        private Portal copy() {
            Portal copy = new Portal();
            copy.minimumInteriorBlocks = minimumInteriorBlocks;
            copy.maximumInteriorBlocks = maximumInteriorBlocks;
            copy.maximumWidth = maximumWidth;
            copy.maximumHeight = maximumHeight;
            copy.frameMaterials = new ArrayList<>(frameMaterials);
            copy.interiorMaterials = new ArrayList<>(interiorMaterials);
            copy.ignitionCauses = new ArrayList<>(ignitionCauses);
            copy.allowedWorlds = new ArrayList<>(allowedWorlds);
            copy.deniedWorlds = new ArrayList<>(deniedWorlds);
            copy.deduplicationMillis = deduplicationMillis;
            return copy;
        }
    }

    public static final class Effects {
        @ConfigDoc(value = "Play a sound after a shaped portal is successfully committed.", impact = "Cancelled or failed attempts stay silent.")
        public boolean creationSound = true;
        @ConfigDoc(value = "Bukkit sound enum used for successful creation.", impact = "The value must exist on the minimum supported API.")
        public String creationSoundType = "minecraft:block.end_portal.spawn";
        @ConfigDoc(value = "Creation sound volume.", impact = "Accepted range is 0.0 through 4.0.")
        public float creationSoundVolume = 0.6F;
        @ConfigDoc(value = "Creation sound pitch.", impact = "Accepted range is 0.5 through 2.0.")
        public float creationSoundPitch = 0.67F;

        private Effects copy() {
            Effects copy = new Effects();
            copy.creationSound = creationSound;
            copy.creationSoundType = creationSoundType;
            copy.creationSoundVolume = creationSoundVolume;
            copy.creationSoundPitch = creationSoundPitch;
            return copy;
        }
    }

    public static final class HotReload {
        @ConfigDoc(value = "Apply stable config and language file changes automatically.", impact = "Invalid edits keep the last known good runtime snapshot.")
        public boolean enabled = true;
        @ConfigDoc(value = "Filesystem watcher poll interval in milliseconds.", impact = "Accepted range is 250 through 60000 milliseconds.")
        public long pollIntervalMillis = 1000L;
        @ConfigDoc(value = "Quiet period used to coalesce editor save bursts.", impact = "Accepted range is 250 through 60000 milliseconds.")
        public long cooldownMillis = 1500L;
        @ConfigDoc(value = "Notify online operators when automatic file reload succeeds or fails.", impact = "Uses a cooperative action-bar notice and never sends chat spam.")
        public boolean notifyOperators = true;

        private HotReload copy() {
            HotReload copy = new HotReload();
            copy.enabled = enabled;
            copy.pollIntervalMillis = pollIntervalMillis;
            copy.cooldownMillis = cooldownMillis;
            copy.notifyOperators = notifyOperators;
            return copy;
        }
    }

    public static final class Integrity {
        @ConfigDoc(value = "Maintain registered shaped portals after vanilla physics and external block edits.", impact = "Disabling this makes non-rectangular native portal blocks best-effort only.")
        public boolean enabled = true;
        @ConfigDoc(value = "Ticks between bounded managed-portal integrity sweeps.", impact = "Accepted range is 20 through 72000 ticks.")
        public long checkIntervalTicks = 200L;
        @ConfigDoc(value = "Maximum registered portals queued for validation per sweep.", impact = "Accepted range is 1 through 1024 portals.")
        public int maximumChecksPerCycle = 32;

        private Integrity copy() {
            Integrity copy = new Integrity();
            copy.enabled = enabled;
            copy.checkIntervalTicks = checkIntervalTicks;
            copy.maximumChecksPerCycle = maximumChecksPerCycle;
            return copy;
        }
    }

    public static final class Presentation {
        @ConfigDoc(value = "Print the ShapedPortals startup splash after services are ready.", impact = "The splash is console-only and is not replayed by reloads.")
        public boolean splashScreen = true;
        @ConfigDoc(value = "Play short success and failure sounds for player command feedback.", impact = "Console command output is unaffected.")
        public boolean commandSounds = true;
        @ConfigDoc(value = "Optional transient overlays added to normal command chat output.", impact = "Accepted values are ACTION_BAR, TITLE, and BOSS_BAR. Chat output is always retained.")
        public List<String> commandOverlays = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc(value = "Channels used for portal creation and rejection notices.", impact = "Accepted values are CHAT, ACTION_BAR, TITLE, and BOSS_BAR. An empty list disables portal notices.")
        public List<String> portalNotices = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc(value = "Lifetime of transient action-bar, title-claim, and boss-bar feedback.", impact = "Accepted range is 10 through 600 ticks.")
        public long overlayDurationTicks = 50L;
        @ConfigDoc(value = "Title fade-in duration in ticks.", impact = "Accepted range is 0 through 200 ticks.")
        public int titleFadeInTicks = 5;
        @ConfigDoc(value = "Title fully-visible duration in ticks.", impact = "Accepted range is 1 through 600 ticks.")
        public int titleStayTicks = 30;
        @ConfigDoc(value = "Title fade-out duration in ticks.", impact = "Accepted range is 0 through 200 ticks.")
        public int titleFadeOutTicks = 10;

        private Presentation copy() {
            Presentation copy = new Presentation();
            copy.splashScreen = splashScreen;
            copy.commandSounds = commandSounds;
            copy.commandOverlays = new ArrayList<>(commandOverlays);
            copy.portalNotices = new ArrayList<>(portalNotices);
            copy.overlayDurationTicks = overlayDurationTicks;
            copy.titleFadeInTicks = titleFadeInTicks;
            copy.titleStayTicks = titleStayTicks;
            copy.titleFadeOutTicks = titleFadeOutTicks;
            return copy;
        }
    }

    public static final class Debug {
        @ConfigDoc(value = "Upload /shapedportals debug reports to the public mclo.gs service after saving them locally.", impact = "Enabled by default. Disabling this keeps reports local; ShapedPortals does not retain the service deletion credential for public uploads.")
        public boolean uploadEnabled = true;

        private Debug copy() {
            Debug copy = new Debug();
            copy.uploadEnabled = uploadEnabled;
            return copy;
        }
    }
}
