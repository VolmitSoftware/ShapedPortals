package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import com.volmit.shapedportals.presentation.ChatMenuStyle;

import java.util.List;

public final class ShapedMessages {
    public static final String CHAT_PREFIX = "{prefix}&r &7› &7";
    public static final TextKey PREFIX = TextKey.of("runtime.prefix", "<bold><gradient:"
            + ChatMenuStyle.theme().primaryLeft() + ":" + ChatMenuStyle.theme().primaryRight()
            + ">ShapedPortals</gradient></bold>");
    public static final TextKey VERSION = TextKey.ofOptional("command.feedback.version", "<gradient:"
            + ChatMenuStyle.theme().primaryLeft() + ":" + ChatMenuStyle.theme().primaryRight()
            + ">{prefix} v{version}</gradient>", "prefix");
    public static final TextKey NO_PERMISSION = prefixed("runtime.permission.denied", "&cYou do not have permission to do that.&r");
    public static final TextKey PLAYER_ONLY = prefixed("runtime.player_only", "&cThis command can only be used by a player.&r");
    public static final TextKey HOT_RELOAD_SUCCESS = prefixed("runtime.hot_reload.success", "&aApplied&7 configuration and language file changes.&r");
    public static final TextKey HOT_RELOAD_FAILED = prefixed("runtime.hot_reload.failed", "&cRejected file changes; the last known good settings remain active.&r");
    public static final TextKey UPDATE_AVAILABLE = prefixed("runtime.update.available", "&7Version &f{new}&7 is &eavailable&7 (installed: &f{old}&7).&r\n&7Release: &b{url}&r");

    public static final TextKey COMMAND_CONFIG_OPENED = prefixed("command.feedback.config.opened", "&aOpened&7 the complete in-game configuration editor.&r");
    public static final TextKey COMMAND_FAILED = prefixed("command.feedback.failed", "&cThe command could not be completed. See the console for details.&r");
    public static final TextKey CONFIG_SAVED = prefixed("command.feedback.config.saved", "&f{setting}&r &achanged&7 from &f{old}&r &7to &f{new}&r&7.&r");
    public static final TextKey CONFIG_SAVE_FAILED = prefixed("command.feedback.config.save_failed", "&cCould not apply &f{setting}&r&c: {reason}&r");
    public static final TextKey DEBUG_STARTED = prefixed("command.feedback.debug.started", "&7Capturing server and {prefix} diagnostics…&r");
    public static final TextKey DEBUG_BUSY = prefixed("command.feedback.debug.busy", "&eA {prefix} diagnostic report is already being created.&r");
    public static final TextKey DEBUG_SAVED = prefixed("command.feedback.debug.saved", "&aSaved&7 the diagnostic report to &f{path}&r&7.&r");
    public static final TextKey DEBUG_UPLOADED = prefixed("command.feedback.debug.uploaded", "&aUploaded&7 the diagnostic report:&r &b&n{url}&r");
    public static final TextKey DEBUG_LINK_HOVER = TextKey.of("command.feedback.debug.link_hover", "&7Open the public mclo.gs report.&r");
    public static final TextKey DEBUG_UPLOAD_FAILED = prefixed("command.feedback.debug.upload_failed", "&eThe local report was saved, but the mclo.gs upload failed. See the console for details.&r");
    public static final TextKey DEBUG_FAILED = prefixed("command.feedback.debug.failed", "&cThe diagnostic report could not be created. See the console for details.&r");

    public static final TextKey STATUS_HEADER = prefixed("command.status.header", "&7Runtime&r");
    public static final TextKey STATUS_CONFIG = prefixed("command.status.config", "&7Creation:&r {enabled} &8|&r &7Language:&r &f{language}&r &8|&r &7Hot reload:&r {hot_reload}");
    public static final TextKey STATUS_PORTALS = prefixed("command.status.portals", "&7Managed portals:&r &f{portals}&r &8|&r &7Interior cells:&r &f{cells}&r");
    public static final TextKey STATUS_ATTEMPTS = prefixed("command.status.attempts", "&7Attempts:&r &f{attempts}&r &8|&r &7Created:&r &a{created}&r &8|&r &7Rejected:&r &c{rejected}&r");
    public static final TextKey STATUS_COMPATIBILITY = prefixed("command.status.compatibility", "&7Artifact:&r &fJava 17 / Bukkit 1.20.1+&r &8|&r &7Scheduler:&r &f{scheduler}&r");

    public static final TextKey PORTAL_NETHER_CREATED = prefixed("portal.notice.nether_created", "&aCreated&7 a shaped Nether portal with &f{blocks}&7 interior blocks.&r");
    public static final TextKey PORTAL_END_CREATED = prefixed("portal.notice.end_created", "&aCreated&7 a shaped End portal with &f{blocks}&7 interior blocks.&r");
    public static final TextKey PORTAL_NETHER_TITLE = TextKey.of("portal.title.nether_created", "&5&lNether Portal Created&r");
    public static final TextKey PORTAL_END_TITLE = TextKey.of("portal.title.end_created", "&5&lEnd Portal Created&r");
    public static final TextKey PORTAL_FAILED = prefixed("portal.notice.failed", "&cThat frame cannot become a shaped portal: &f{reason}&r&c.&r");

    public static final TextKey PORTAL_LIST_EMPTY = TextKey.of("portal.navigation.list.empty", "&eNo managed shaped portals are registered.&r");
    public static final TextKey PORTAL_LIST_ENTRY = TextKey.ofOptional("portal.navigation.list.entry", "&d{id}&r &8›&r &f{world}&r &8•&r &7Type:&r &f{type}&r\n&8  ├&r &7Location:&r &f{x}, {y}, {z}&r &8•&r &7Axis:&r &f{axis}&r &8•&r &7Cells:&r &f{blocks}&r\n&8  └&r &7Creator:&r &f{creator}&r &8•&r &7Created:&r &f{created}&r", "type");
    public static final TextKey PORTAL_LIST_HOVER = TextKey.of("portal.navigation.list.hover", "&7UUID:&r &f{uuid}&r\n&aClick to teleport to this portal.&r");
    public static final TextKey PORTAL_LIST_FRAME_POLICY_NOTE = TextKey.of("portal.navigation.list.frame_policy_note", "&8  ⚠&r &eExisting frame uses &f{materials}&r&e, which cannot create new portals.&r");
    public static final TextKey PORTAL_NOT_FOUND = prefixed("portal.navigation.error.not_found", "&cNo managed portal matches &f{portal}&r&c.&r");
    public static final TextKey PORTAL_AMBIGUOUS = prefixed("portal.navigation.error.ambiguous", "&cThe prefix &f{portal}&r&c matches {matches} portals. Enter more of the UUID.&r");
    public static final TextKey PORTAL_REMOVED = prefixed("portal.navigation.error.removed", "&cThat portal is no longer registered.&r");
    public static final TextKey PORTAL_WORLD_UNAVAILABLE = prefixed("portal.navigation.error.world_unavailable", "&cThe portal world &f{world}&r&c is currently unavailable.&r");
    public static final TextKey PORTAL_DESTINATION_UNAVAILABLE = prefixed("portal.navigation.error.destination_unavailable", "&cThe portal destination is temporarily unavailable.&r");
    public static final TextKey PORTAL_INACTIVE = prefixed("portal.navigation.error.inactive", "&cThat portal is no longer active. Its integrity check has been queued.&r");
    public static final TextKey PORTAL_NO_SAFE_LANDING = prefixed("portal.navigation.error.no_safe_landing", "&cNo safe standing space exists beside that portal.&r");
    public static final TextKey PORTAL_UNSAFE_CONFIRMATION = prefixed("portal.navigation.confirm.unsafe", "&cNo safe standing space exists beside &f{portal}&r&c. &eClick the same portal again within &f{seconds}&r&e seconds to teleport into it anyway.&r");
    public static final TextKey PORTAL_TELEPORT_PREPARING = prefixed("portal.navigation.teleport.preparing", "&7Preparing destination for &f{portal}&r&7…&r");
    public static final TextKey PORTAL_TELEPORT_SUCCESS = prefixed("portal.navigation.teleport.success", "&aTeleported&7 beside &f{portal}&r&7 in &f{world}&r&7.&r");
    public static final TextKey PORTAL_TELEPORT_FAILED = prefixed("portal.navigation.teleport.failed", "&cThe portal teleport did not complete. It may have been cancelled by another plugin.&r");

    public static final TextKey HUD_TITLE = TextKey.ofOptional("hud.title", "{prefix}&r", "prefix");
    public static final TextKey HUD_SUCCESS = TextKey.of("hud.success", "&aSuccess&r");
    public static final TextKey HUD_FAILURE = TextKey.of("hud.failure", "&cAction failed&r");
    public static final TextKey HUD_INFO = TextKey.ofOptional("hud.info", "{prefix}&r", "prefix");

    public static final TextKey GUI_ROOT_TITLE = TextKey.ofOptional("gui.title.root", "{prefix}&r &7› &7Configuration&r", "prefix");
    public static final TextKey GUI_CATEGORY_TITLE = TextKey.ofOptional("gui.title.category", "{prefix}&r &7› &7{category}", "prefix");
    public static final TextKey GUI_CATEGORY_GENERAL = TextKey.of("gui.category.general", "&dGeneral&r");
    public static final TextKey GUI_CATEGORY_PORTAL = TextKey.of("gui.category.portal", "&dPortal Rules&r");
    public static final TextKey GUI_CATEGORY_EFFECTS = TextKey.of("gui.category.effects", "&dEffects&r");
    public static final TextKey GUI_CATEGORY_HOT_RELOAD = TextKey.of("gui.category.hot_reload", "&dHot Reload&r");
    public static final TextKey GUI_CATEGORY_INTEGRITY = TextKey.of("gui.category.integrity", "&dIntegrity&r");
    public static final TextKey GUI_CATEGORY_PRESENTATION = TextKey.of("gui.category.presentation", "&dPresentation&r");
    public static final TextKey GUI_CATEGORY_DEBUG = TextKey.of("gui.category.debug", "&dDiagnostics&r");
    public static final TextKey GUI_CATEGORY_LANGUAGES = TextKey.of("gui.category.languages", "&dLanguages&r");
    public static final TextKey GUI_BACK = TextKey.of("gui.navigation.back", "&eBack&r");
    public static final TextKey GUI_CLOSE = TextKey.of("gui.navigation.close", "&cClose&r");
    public static final TextKey GUI_STATE = TextKey.of("gui.lore.state", "&7Current:&r {value}");
    public static final TextKey GUI_TOGGLE = TextKey.of("gui.lore.toggle", "&8Click to toggle.&r");
    public static final TextKey GUI_NUMBER = TextKey.of("gui.lore.number", "&8Left +1, right -1, shift ×10, drop to type.&r");
    public static final TextKey GUI_TEXT = TextKey.of("gui.lore.text", "&8Click to type a new value in chat.&r");
    public static final TextKey GUI_LANGUAGE_SELECT = TextKey.of("gui.lore.language", "&8Click to choose an available language or type a locale.&r");
    public static final TextKey GUI_CATEGORY_OPEN = TextKey.of("gui.lore.category", "&8Click to edit every setting in this category.&r");
    public static final TextKey GUI_PROMPT = prefixed("gui.prompt.request", "&eType a new value for &f{setting}&r&e in chat.&r");
    public static final TextKey GUI_PROMPT_LIST = prefixed("gui.prompt.list", "&7Separate list entries with commas. Type &fnone&r&7 for an empty list.&r");
    public static final TextKey GUI_PROMPT_CANCEL = prefixed("gui.prompt.cancel", "&7Type &fcancel&r&7 to stop. This prompt expires in 60 seconds.&r");
    public static final TextKey GUI_PROMPT_CANCELLED = prefixed("gui.prompt.cancelled", "&eConfiguration edit cancelled.&r");
    public static final TextKey GUI_PROMPT_TIMEOUT = prefixed("gui.prompt.timeout", "&eConfiguration edit expired without changing anything.&r");
    public static final TextKey GUI_LANGUAGE_TYPE = TextKey.of("gui.language.type", "&7Type a locale name in chat to create or select it.&r");
    public static final TextKey GUI_LANGUAGE_OPTION = TextKey.of("gui.language.option", "{status} &f{locale}&r &8›&r &7{name}&r");
    public static final TextKey GUI_LANGUAGE_HOVER = TextKey.of("gui.language.hover", "&7Click to select &f{locale}&r&7.&r");
    public static final TextKey GUI_LANGUAGE_EMPTY = TextKey.of("gui.language.empty", "&eNo available languages were found; type a locale name to create one.&r");
    public static final TextKey GUI_LANGUAGE_CURRENT = TextKey.of("gui.language.current", "&7Current Value:&r {value}");
    public static final TextKey GUI_LANGUAGE_VARIABLES = TextKey.of("gui.language.variables", "&7Current variables / placeholders:&r &f{variables}&r");
    public static final TextKey GUI_LANGUAGE_CHANGED = TextKey.of("gui.language.changed", "&f{old}&r &7changed to&r &f{new}&r");

    public static final TextKey SETTING_GENERAL_ENABLED = setting("general.enabled", "Portal creation");
    public static final TextKey SETTING_GENERAL_LANGUAGE = setting("general.language", "Active language locale");
    public static final TextKey SETTING_GENERAL_REQUIRE_PERMISSION = setting("general.require_create_permission", "Require creation permission");
    public static final TextKey SETTING_GENERAL_FAILURE_FEEDBACK = setting("general.failure_feedback", "Portal failure feedback");
    public static final TextKey SETTING_GENERAL_UPDATE_NOTIFICATIONS = setting("general.update_notifications", "GitHub update notifications");
    public static final TextKey SETTING_METRICS_ENABLED = setting("metrics.enabled", "Anonymous bStats metrics");
    public static final TextKey SETTING_PORTAL_MINIMUM = setting("portal.minimum_interior_blocks", "Nether minimum interior blocks");
    public static final TextKey SETTING_PORTAL_MAXIMUM = setting("portal.maximum_interior_blocks", "Nether maximum interior blocks");
    public static final TextKey SETTING_PORTAL_WIDTH = setting("portal.maximum_width", "Nether maximum width");
    public static final TextKey SETTING_PORTAL_HEIGHT = setting("portal.maximum_height", "Nether maximum height");
    public static final TextKey SETTING_PORTAL_FRAMES = setting("portal.frame_materials", "Nether frame materials");
    public static final TextKey SETTING_PORTAL_INTERIORS = setting("portal.interior_materials", "Nether interior materials");
    public static final TextKey SETTING_PORTAL_CAUSES = setting("portal.ignition_causes", "Ignition causes");
    public static final TextKey SETTING_PORTAL_ALLOWED_WORLDS = setting("portal.allowed_worlds", "Allowed worlds");
    public static final TextKey SETTING_PORTAL_DENIED_WORLDS = setting("portal.denied_worlds", "Denied worlds");
    public static final TextKey SETTING_PORTAL_DEDUPLICATION = setting("portal.deduplication_millis", "Deduplication milliseconds");
    public static final TextKey SETTING_PORTAL_END_ENABLED = setting("portal.end_portal_creation", "Shaped End portal creation");
    public static final TextKey SETTING_PORTAL_END_MINIMUM = setting("portal.end_minimum_interior_blocks", "End minimum interior blocks");
    public static final TextKey SETTING_PORTAL_END_MAXIMUM = setting("portal.end_maximum_interior_blocks", "End maximum interior blocks");
    public static final TextKey SETTING_PORTAL_END_WIDTH = setting("portal.end_maximum_width", "End maximum width");
    public static final TextKey SETTING_PORTAL_END_LENGTH = setting("portal.end_maximum_length", "End maximum length");
    public static final TextKey SETTING_PORTAL_END_INTERIORS = setting("portal.end_interior_materials", "End interior materials");
    public static final TextKey SETTING_EFFECTS_SOUND = setting("effects.creation_sound", "Nether creation sound");
    public static final TextKey SETTING_EFFECTS_SOUND_TYPE = setting("effects.creation_sound_type", "Nether creation sound type");
    public static final TextKey SETTING_EFFECTS_VOLUME = setting("effects.creation_sound_volume", "Nether creation sound volume");
    public static final TextKey SETTING_EFFECTS_PITCH = setting("effects.creation_sound_pitch", "Nether creation sound pitch");
    public static final TextKey SETTING_EFFECTS_END_SOUND = setting("effects.end_creation_sound", "End creation sound");
    public static final TextKey SETTING_EFFECTS_END_SOUND_TYPE = setting("effects.end_creation_sound_type", "End creation sound type");
    public static final TextKey SETTING_EFFECTS_END_VOLUME = setting("effects.end_creation_sound_volume", "End creation sound volume");
    public static final TextKey SETTING_EFFECTS_END_PITCH = setting("effects.end_creation_sound_pitch", "End creation sound pitch");
    public static final TextKey SETTING_HOT_RELOAD_ENABLED = setting("hot_reload.enabled", "Hot reload");
    public static final TextKey SETTING_HOT_RELOAD_POLL = setting("hot_reload.poll_interval_millis", "Poll interval milliseconds");
    public static final TextKey SETTING_HOT_RELOAD_COOLDOWN = setting("hot_reload.cooldown_millis", "Save cooldown milliseconds");
    public static final TextKey SETTING_HOT_RELOAD_NOTIFY = setting("hot_reload.notify_operators", "Notify operators");
    public static final TextKey SETTING_INTEGRITY_ENABLED = setting("integrity.enabled", "Integrity service");
    public static final TextKey SETTING_INTEGRITY_INTERVAL = setting("integrity.check_interval_ticks", "Check interval ticks");
    public static final TextKey SETTING_INTEGRITY_MAXIMUM = setting("integrity.maximum_checks_per_cycle", "Maximum checks per cycle");
    public static final TextKey SETTING_PRESENTATION_SPLASH = setting("presentation.splash_screen", "Startup splash screen");
    public static final TextKey SETTING_PRESENTATION_SOUNDS = setting("presentation.command_sounds", "Command sounds");
    public static final TextKey SETTING_PRESENTATION_COMMAND = setting("presentation.command_overlays", "Command overlays");
    public static final TextKey SETTING_PRESENTATION_PORTAL = setting("presentation.portal_notices", "Portal rejection notice channels");
    public static final TextKey SETTING_PRESENTATION_NETHER_CREATION = setting("presentation.nether_creation_notices", "Nether creation notice channels");
    public static final TextKey SETTING_PRESENTATION_END_CREATION = setting("presentation.end_creation_notices", "End creation notice channels");
    public static final TextKey SETTING_PRESENTATION_DURATION = setting("presentation.overlay_duration_ticks", "Overlay duration ticks");
    public static final TextKey SETTING_PRESENTATION_FADE_IN = setting("presentation.title_fade_in_ticks", "Title fade-in ticks");
    public static final TextKey SETTING_PRESENTATION_STAY = setting("presentation.title_stay_ticks", "Title stay ticks");
    public static final TextKey SETTING_PRESENTATION_FADE_OUT = setting("presentation.title_fade_out_ticks", "Title fade-out ticks");
    public static final TextKey SETTING_DEBUG_UPLOAD = setting("debug.upload_enabled", "Upload debug reports to mclo.gs");

    public static final TextKey COMMAND_ROOT = TextKey.ofOptional("command.description.root", "{prefix} help and administration", "prefix");
    public static final TextKey COMMAND_CONFIG = TextKey.of("command.description.config", "Open the complete in-game configuration editor");
    public static final TextKey COMMAND_LANGUAGE = TextKey.ofOptional("command.description.language", "Select an available {prefix} language", "prefix");
    public static final TextKey COMMAND_DEBUG = TextKey.ofOptional("command.description.debug", "{prefix} diagnostic tools", "prefix");
    public static final TextKey COMMAND_VERSION = TextKey.ofOptional("command.description.version", "Show the installed {prefix} version", "prefix");
    public static final TextKey COMMAND_DEBUG_DUMP = TextKey.ofOptional("command.description.debug_dump", "Create a comprehensive {prefix} diagnostic report", "prefix");
    public static final TextKey COMMAND_PORTALS = TextKey.of("command.description.portals", "List every managed portal and its teleport shortcut");
    public static final TextKey COMMAND_TELEPORT = TextKey.of("command.description.teleport", "Teleport safely beside a managed portal or list portals when omitted");
    public static final TextKey COMMAND_STATUS = TextKey.of("command.description.status", "Show managed portal and attempt statistics");
    public static final TextKey PARAMETER_LOCALE = TextKey.of("command.parameter.locale", "Available language locale");
    public static final TextKey PARAMETER_PAGE = TextKey.of("command.parameter.page", "One-based portal list page");
    public static final TextKey PARAMETER_PORTAL = TextKey.of("command.parameter.portal", "Portal UUID, unique prefix, or list");
    public static final TextKey PARAMETER_UPLOAD = TextKey.of("command.parameter.upload", "Upload the report when public uploads are enabled");

    private ShapedMessages() {
    }

    public static MessageCatalog catalog() {
        MessageCatalog.Builder builder = MessageCatalog.builder("en_US");
        builder.addAll(productKeys());
        builder.addAll(DirectorMessages.keys());
        for (MessageKey key : BukkitLanguageMessages.keys()) {
            builder.add(shared((TextKey) key));
        }
        for (MessageKey key : BukkitDebugMessages.keys()) {
            builder.add(shared((TextKey) key));
        }
        return builder.build();
    }

    private static TextKey setting(String id, String english) {
        return TextKey.of("gui.setting." + id, "&d" + english + "&r");
    }

    private static TextKey prefixed(String id, String english) {
        return TextKey.ofOptional(id, CHAT_PREFIX + english, "prefix");
    }

    private static TextKey shared(TextKey key) {
        String english = key.english().replace("{plugin}", "{prefix}");
        boolean feedback = key.id().startsWith("language.error.")
                || key.id().startsWith("language.usage.")
                || key.id().startsWith("language.selection.")
                || key.id().startsWith("language.editor.prompt.")
                || key.id().startsWith("language.editor.input.")
                || key.id().startsWith("language.editor.error.")
                || key.id().startsWith("language.editor.saved.")
                || key.id().equals("language.editor.loading")
                || key.id().startsWith("debug.") && !key.id().startsWith("debug.action.");
        if (feedback) {
            if (english.startsWith("{prefix}: ")) {
                english = english.substring("{prefix}: ".length());
            }
            String color = key.id().contains(".error.") || key.id().endsWith("failed")
                    || key.id().startsWith("language.editor.input.") ? "&c" : "&7";
            if (key.id().equals("language.selection.english-fallback") || key.id().endsWith("expired")) {
                color = "&e";
            }
            return prefixed(key.id(), color + english);
        }
        return english.contains("{prefix}")
                ? TextKey.ofOptional(key.id(), english, "prefix")
                : TextKey.of(key.id(), english);
    }

    private static List<TextKey> productKeys() {
        return List.of(
                PREFIX, VERSION, NO_PERMISSION, PLAYER_ONLY, HOT_RELOAD_SUCCESS, HOT_RELOAD_FAILED, UPDATE_AVAILABLE,
                COMMAND_CONFIG_OPENED, COMMAND_FAILED,
                CONFIG_SAVED, CONFIG_SAVE_FAILED, DEBUG_STARTED, DEBUG_BUSY, DEBUG_SAVED, DEBUG_UPLOADED,
                DEBUG_LINK_HOVER, DEBUG_UPLOAD_FAILED, DEBUG_FAILED, STATUS_HEADER, STATUS_CONFIG, STATUS_PORTALS,
                STATUS_ATTEMPTS, STATUS_COMPATIBILITY, PORTAL_NETHER_CREATED, PORTAL_END_CREATED,
                PORTAL_NETHER_TITLE, PORTAL_END_TITLE, PORTAL_FAILED,
                PORTAL_LIST_EMPTY, PORTAL_LIST_ENTRY, PORTAL_LIST_HOVER, PORTAL_LIST_FRAME_POLICY_NOTE, PORTAL_NOT_FOUND,
                PORTAL_AMBIGUOUS, PORTAL_REMOVED, PORTAL_WORLD_UNAVAILABLE, PORTAL_DESTINATION_UNAVAILABLE,
                PORTAL_INACTIVE, PORTAL_NO_SAFE_LANDING, PORTAL_UNSAFE_CONFIRMATION, PORTAL_TELEPORT_PREPARING,
                PORTAL_TELEPORT_SUCCESS, PORTAL_TELEPORT_FAILED,
                HUD_TITLE, HUD_SUCCESS, HUD_FAILURE, HUD_INFO,
                GUI_ROOT_TITLE, GUI_CATEGORY_TITLE, GUI_CATEGORY_GENERAL, GUI_CATEGORY_PORTAL,
                GUI_CATEGORY_EFFECTS, GUI_CATEGORY_HOT_RELOAD, GUI_CATEGORY_INTEGRITY,
                GUI_CATEGORY_PRESENTATION, GUI_CATEGORY_DEBUG, GUI_CATEGORY_LANGUAGES,
                GUI_BACK, GUI_CLOSE, GUI_STATE, GUI_TOGGLE,
                GUI_NUMBER, GUI_TEXT, GUI_LANGUAGE_SELECT, GUI_CATEGORY_OPEN, GUI_PROMPT, GUI_PROMPT_LIST,
                GUI_PROMPT_CANCEL, GUI_PROMPT_CANCELLED, GUI_PROMPT_TIMEOUT,
                GUI_LANGUAGE_TYPE, GUI_LANGUAGE_OPTION, GUI_LANGUAGE_HOVER, GUI_LANGUAGE_EMPTY,
                GUI_LANGUAGE_CURRENT, GUI_LANGUAGE_VARIABLES, GUI_LANGUAGE_CHANGED,
                SETTING_GENERAL_ENABLED, SETTING_GENERAL_LANGUAGE, SETTING_GENERAL_REQUIRE_PERMISSION,
                SETTING_GENERAL_FAILURE_FEEDBACK, SETTING_GENERAL_UPDATE_NOTIFICATIONS,
                SETTING_METRICS_ENABLED, SETTING_PORTAL_MINIMUM, SETTING_PORTAL_MAXIMUM,
                SETTING_PORTAL_WIDTH, SETTING_PORTAL_HEIGHT, SETTING_PORTAL_FRAMES,
                SETTING_PORTAL_INTERIORS, SETTING_PORTAL_CAUSES, SETTING_PORTAL_ALLOWED_WORLDS,
                SETTING_PORTAL_DENIED_WORLDS, SETTING_PORTAL_DEDUPLICATION, SETTING_PORTAL_END_ENABLED,
                SETTING_PORTAL_END_MINIMUM, SETTING_PORTAL_END_MAXIMUM, SETTING_PORTAL_END_WIDTH,
                SETTING_PORTAL_END_LENGTH, SETTING_PORTAL_END_INTERIORS, SETTING_EFFECTS_SOUND,
                SETTING_EFFECTS_SOUND_TYPE, SETTING_EFFECTS_VOLUME, SETTING_EFFECTS_PITCH,
                SETTING_EFFECTS_END_SOUND, SETTING_EFFECTS_END_SOUND_TYPE, SETTING_EFFECTS_END_VOLUME,
                SETTING_EFFECTS_END_PITCH,
                SETTING_HOT_RELOAD_ENABLED, SETTING_HOT_RELOAD_POLL, SETTING_HOT_RELOAD_COOLDOWN,
                SETTING_HOT_RELOAD_NOTIFY, SETTING_INTEGRITY_ENABLED, SETTING_INTEGRITY_INTERVAL,
                SETTING_INTEGRITY_MAXIMUM, SETTING_PRESENTATION_SPLASH, SETTING_PRESENTATION_SOUNDS,
                SETTING_PRESENTATION_COMMAND, SETTING_PRESENTATION_PORTAL, SETTING_PRESENTATION_NETHER_CREATION,
                SETTING_PRESENTATION_END_CREATION, SETTING_PRESENTATION_DURATION,
                SETTING_PRESENTATION_FADE_IN, SETTING_PRESENTATION_STAY, SETTING_PRESENTATION_FADE_OUT,
                SETTING_DEBUG_UPLOAD, COMMAND_ROOT, COMMAND_CONFIG, COMMAND_LANGUAGE, COMMAND_DEBUG, COMMAND_DEBUG_DUMP,
                COMMAND_PORTALS, COMMAND_TELEPORT, COMMAND_STATUS, COMMAND_VERSION,
                PARAMETER_LOCALE, PARAMETER_PAGE, PARAMETER_PORTAL, PARAMETER_UPLOAD
        );
    }
}
