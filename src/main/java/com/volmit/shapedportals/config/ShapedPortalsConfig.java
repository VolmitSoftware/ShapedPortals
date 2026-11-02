package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.List;

@ConfigDescription("Edits apply automatically while hot reload is on.")
public final class ShapedPortalsConfig {
    @ConfigDoc("Portal creation switch, language, permission, and notifications.")
    public General general = new General();
    @ConfigDoc("Anonymous usage statistics.")
    public Metrics metrics = new Metrics();
    @ConfigDoc("Nether and End portal creation rules.")
    public Portal portal = new Portal();
    @ConfigDoc("Sounds played when a portal is created.")
    public Effects effects = new Effects();
    @ConfigDoc("Automatic reloading of edited config and language files.")
    public HotReload hotReload = new HotReload();
    @ConfigDoc("Repair checks for existing portals.")
    public Integrity integrity = new Integrity();
    @ConfigDoc("Startup banner, command feedback, and player notices.")
    public Presentation presentation = new Presentation();
    @ConfigDoc("Diagnostic reports.")
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
        @ConfigDoc(value = "Allow new shaped portals to be created.", impact = "Existing portals keep working and are still maintained when off.")
        public boolean enabled = true;
        @ConfigDoc(value = "Server default language: en_US, a repository locale, or the name of any languages/<id>.toml file.", impact = "Repository languages download when selected and update at startup without losing your edits; missing messages use English.")
        public String language = "en_US";
        @ConfigDoc(value = "Require shapedportals.create to create Nether or End portals.", impact = "Portals lit without a player are controlled by portal.ignitionCauses instead.")
        public boolean requireCreatePermission = true;
        @ConfigDoc(value = "Tell players why a shaped portal was not created.", impact = "Unrelated fires, incomplete End frames, and vanilla portals stay quiet.")
        public boolean failureFeedback = true;
        @ConfigDoc(value = "Tell operators and players with shapedportals.update about new GitHub releases when they join.", impact = "Checks at startup and on /volmit plugins updates, and never downloads anything; off stops the checks.")
        public boolean updateNotifications = true;

        private General copy() {
            General copy = new General();
            copy.enabled = enabled;
            copy.language = language;
            copy.requireCreatePermission = requireCreatePermission;
            copy.failureFeedback = failureFeedback;
            copy.updateNotifications = updateNotifications;
            return copy;
        }
    }

    public static final class Metrics {
        @ConfigDoc(value = "Send anonymous usage statistics to bStats.", impact = "Applies immediately; the global opt-out in plugins/bStats/config.yml still wins.")
        public boolean enabled = true;

        private Metrics copy() {
            Metrics copy = new Metrics();
            copy.enabled = enabled;
            return copy;
        }
    }

    public static final class Portal {
        @ConfigDoc(value = "Smallest Nether portal interior, in blocks (1 to 4096).", impact = "A value of 2 rejects single-block openings, whose axis is ambiguous.")
        public int minimumInteriorBlocks = 2;
        @ConfigDoc("Largest Nether portal interior, in blocks, from the minimum up to 4096.")
        public int maximumInteriorBlocks = 256;
        @ConfigDoc(value = "Widest Nether portal interior, in blocks (1 to 512).", impact = "Also limits how far the frame scan reaches.")
        public int maximumWidth = 64;
        @ConfigDoc(value = "Tallest Nether portal interior, in blocks (1 to 512).", impact = "Also limits how far the frame scan reaches.")
        public int maximumHeight = 64;
        @ConfigDoc(value = "Blocks that can form a new Nether portal frame.", impact = "Changes do not affect existing portals.")
        public List<String> frameMaterials = new ArrayList<>(List.of("OBSIDIAN", "CRYING_OBSIDIAN"));
        @ConfigDoc(value = "Blocks a Nether portal can fill when created or repaired.", impact = "Cannot include NETHER_PORTAL or any frame material.")
        public List<String> interiorMaterials = new ArrayList<>(List.of("AIR", "CAVE_AIR", "VOID_AIR", "FIRE", "SOUL_FIRE"));
        @ConfigDoc(value = "What can light a Nether portal: any BlockIgniteEvent cause, PLACED_FIRE for placed fire, or DISPENSER for dispensers.", impact = "Portals lit by a dispenser skip the shapedportals.create check.")
        public List<String> ignitionCauses = new ArrayList<>(List.of("FLINT_AND_STEEL", "FIREBALL", "PLACED_FIRE"));
        @ConfigDoc(value = "Worlds where portals can be created, ignoring case.", impact = "An empty list allows every world not in deniedWorlds.")
        public List<String> allowedWorlds = new ArrayList<>();
        @ConfigDoc(value = "Worlds where portals cannot be created, ignoring case.", impact = "Takes priority over allowedWorlds.")
        public List<String> deniedWorlds = new ArrayList<>();
        @ConfigDoc(value = "How long to ignore repeat ignitions at the same block, in milliseconds (0 to 60000).", impact = "Stops one action that fires two events from being handled twice.")
        public long deduplicationMillis = 1500L;
        @ConfigDoc(value = "Create shaped End portals when the last eye completes a frame.", impact = "Vanilla 3x3 End portals are left to the server.")
        public boolean endPortalCreation = true;
        @ConfigDoc("Smallest End portal interior, in blocks (1 to 4096).")
        public int endMinimumInteriorBlocks = 1;
        @ConfigDoc("Largest End portal interior, in blocks, from the minimum up to 4096.")
        public int endMaximumInteriorBlocks = 256;
        @ConfigDoc("Longest End portal interior along X, in blocks (1 to 512).")
        public int endMaximumWidth = 64;
        @ConfigDoc("Longest End portal interior along Z, in blocks (1 to 512).")
        public int endMaximumLength = 64;
        @ConfigDoc(value = "Blocks an End portal can fill when created or repaired.", impact = "Cannot include END_PORTAL, END_PORTAL_FRAME, or NETHER_PORTAL.")
        public List<String> endInteriorMaterials = new ArrayList<>(List.of("AIR", "CAVE_AIR", "VOID_AIR"));

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
            copy.endPortalCreation = endPortalCreation;
            copy.endMinimumInteriorBlocks = endMinimumInteriorBlocks;
            copy.endMaximumInteriorBlocks = endMaximumInteriorBlocks;
            copy.endMaximumWidth = endMaximumWidth;
            copy.endMaximumLength = endMaximumLength;
            copy.endInteriorMaterials = new ArrayList<>(endInteriorMaterials);
            return copy;
        }
    }

    public static final class Effects {
        @ConfigDoc("Play a sound when a shaped Nether portal is created.")
        public boolean creationSound = true;
        @ConfigDoc("Sound for a new Nether portal, as a namespaced key or Bukkit sound name.")
        public String creationSoundType = "minecraft:block.portal.trigger";
        @ConfigDoc("Nether portal sound volume (0.0 to 4.0).")
        public float creationSoundVolume = 0.6F;
        @ConfigDoc("Nether portal sound pitch (0.5 to 2.0).")
        public float creationSoundPitch = 0.67F;
        @ConfigDoc("Play a sound when a shaped End portal is created.")
        public boolean endCreationSound = true;
        @ConfigDoc("Sound for a new End portal, as a namespaced key or Bukkit sound name.")
        public String endCreationSoundType = "minecraft:block.end_portal.spawn";
        @ConfigDoc("End portal sound volume (0.0 to 4.0).")
        public float endCreationSoundVolume = 0.8F;
        @ConfigDoc("End portal sound pitch (0.5 to 2.0).")
        public float endCreationSoundPitch = 1.0F;

        private Effects copy() {
            Effects copy = new Effects();
            copy.creationSound = creationSound;
            copy.creationSoundType = creationSoundType;
            copy.creationSoundVolume = creationSoundVolume;
            copy.creationSoundPitch = creationSoundPitch;
            copy.endCreationSound = endCreationSound;
            copy.endCreationSoundType = endCreationSoundType;
            copy.endCreationSoundVolume = endCreationSoundVolume;
            copy.endCreationSoundPitch = endCreationSoundPitch;
            return copy;
        }
    }

    public static final class HotReload {
        @ConfigDoc(value = "Reload config and language files automatically when they change.", impact = "Invalid edits are rejected and the last working settings stay active.")
        public boolean enabled = true;
        @ConfigDoc("How often to check for file changes, in milliseconds (250 to 60000).")
        public long pollIntervalMillis = 1000L;
        @ConfigDoc("How long a changed file must stay unchanged before it loads, in milliseconds (250 to 60000).")
        public long cooldownMillis = 1500L;
        @ConfigDoc(value = "Tell online players with shapedportals.config when a reload succeeds or fails.", impact = "Shown on the action bar, not in chat.")
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
        @ConfigDoc(value = "Keep existing portals intact after physics updates and outside block changes.", impact = "When off, non-rectangular portals may break.")
        public boolean enabled = true;
        @ConfigDoc("Ticks between portal checks (20 to 72000).")
        public long checkIntervalTicks = 200L;
        @ConfigDoc("Most portals checked each cycle (1 to 1024).")
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
        @ConfigDoc("Print the startup banner in the console.")
        public boolean splashScreen = true;
        @ConfigDoc("Play success and failure sounds for player commands.")
        public boolean commandSounds = true;
        @ConfigDoc(value = "Extra places to show command results: ACTION_BAR, TITLE, BOSS_BAR.", impact = "Results always appear in chat too.")
        public List<String> commandOverlays = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc(value = "Where to show why a portal was not created: CHAT, ACTION_BAR, TITLE, BOSS_BAR.", impact = "An empty list hides these notices.")
        public List<String> portalNotices = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc(value = "Where to confirm a new Nether portal: CHAT, ACTION_BAR, TITLE, BOSS_BAR.", impact = "An empty list hides the message.")
        public List<String> netherCreationNotices = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc(value = "Where to confirm a new End portal: CHAT, ACTION_BAR, TITLE, BOSS_BAR.", impact = "An empty list hides the message.")
        public List<String> endCreationNotices = new ArrayList<>(List.of("ACTION_BAR"));
        @ConfigDoc("How long action bar and boss bar messages stay up, in ticks (10 to 600).")
        public long overlayDurationTicks = 50L;
        @ConfigDoc("Title fade-in time in ticks (0 to 200).")
        public int titleFadeInTicks = 5;
        @ConfigDoc("How long titles stay fully visible, in ticks (1 to 600).")
        public int titleStayTicks = 30;
        @ConfigDoc("Title fade-out time in ticks (0 to 200).")
        public int titleFadeOutTicks = 10;

        private Presentation copy() {
            Presentation copy = new Presentation();
            copy.splashScreen = splashScreen;
            copy.commandSounds = commandSounds;
            copy.commandOverlays = new ArrayList<>(commandOverlays);
            copy.portalNotices = new ArrayList<>(portalNotices);
            copy.netherCreationNotices = new ArrayList<>(netherCreationNotices);
            copy.endCreationNotices = new ArrayList<>(endCreationNotices);
            copy.overlayDurationTicks = overlayDurationTicks;
            copy.titleFadeInTicks = titleFadeInTicks;
            copy.titleStayTicks = titleStayTicks;
            copy.titleFadeOutTicks = titleFadeOutTicks;
            return copy;
        }
    }

    public static final class Debug {
        @ConfigDoc(value = "Upload /shapedportals debug dump reports to the public mclo.gs service.", impact = "Reports are always saved locally; ShapedPortals does not keep the key needed to delete an upload.")
        public boolean uploadEnabled = true;

        private Debug copy() {
            Debug copy = new Debug();
            copy.uploadEnabled = uploadEnabled;
            return copy;
        }
    }
}
