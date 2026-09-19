package com.volmit.shapedportals.presentation;

import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.hud.HudTitleClaim;
import art.arcane.volmlib.util.hud.HudTitleService;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.PresentationChannel;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.portal.PortalType;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PresentationService implements Listener {
    private static final String COMMAND_PURPOSE = "shapedportals:command";
    private static final String PORTAL_PURPOSE = "shapedportals:portal";
    private static final String HOTLOAD_PURPOSE = "shapedportals:hotload";

    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final LanguageService language;
    private final HudActionBar actionBar;
    private final HudTitleService titles;
    private final HudBossBarLane bossBars;
    private final Sound successSound;
    private final Sound failureSound;
    private final AtomicLong generations = new AtomicLong();
    private final ConcurrentHashMap<String, Long> bossBarGenerations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HudTitleClaim> titleClaims = new ConcurrentHashMap<>();

    public PresentationService(ShapedPortals plugin, ConfigService configService, LanguageService language) {
        this.plugin = plugin;
        this.configService = configService;
        this.language = language;
        actionBar = new HudActionBar(plugin);
        titles = new HudTitleService(plugin);
        bossBars = new HudBossBarLane();
        DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.SHAPEDPORTALS);
        successSound = sound(theme.getSuccessSound(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK);
        failureSound = sound(theme.getErrorSound(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE);
    }

    public void command(CommandSender sender, TextKey key, FeedbackTone tone) {
        command(sender, key, MessageArgs.empty(), tone);
    }

    public void command(CommandSender sender, TextKey key, MessageArgs arguments, FeedbackTone tone) {
        language.sendPrefixed(sender, key, arguments);
        if (!(sender instanceof Player player)) {
            return;
        }
        RuntimeConfig config = configService.runtime();
        display(player, COMMAND_PURPOSE, config.commandOverlays(), key, arguments, tone, config.commandSounds());
    }

    public void portal(Player player, TextKey key, MessageArgs arguments, FeedbackTone tone) {
        RuntimeConfig config = configService.runtime();
        display(player, PORTAL_PURPOSE, config.portalNotices(), key, arguments, tone, false);
    }

    public void portalCreated(Player player, PortalType type, MessageArgs arguments) {
        RuntimeConfig config = configService.runtime();
        Set<PresentationChannel> channels = type == PortalType.END
                ? config.endCreationNotices()
                : config.netherCreationNotices();
        TextKey message = type == PortalType.END
                ? ShapedMessages.PORTAL_END_CREATED
                : ShapedMessages.PORTAL_NETHER_CREATED;
        TextKey title = type == PortalType.END
                ? ShapedMessages.PORTAL_END_TITLE
                : ShapedMessages.PORTAL_NETHER_TITLE;
        display(player, PORTAL_PURPOSE, channels, message, arguments, FeedbackTone.SUCCESS, false, title);
    }

    public void hotReload(Player player, boolean success) {
        TextKey key = success ? ShapedMessages.HOT_RELOAD_SUCCESS : ShapedMessages.HOT_RELOAD_FAILED;
        FeedbackTone tone = success ? FeedbackTone.SUCCESS : FeedbackTone.FAILURE;
        display(player, HOTLOAD_PURPOSE, Set.of(PresentationChannel.ACTION_BAR), key, MessageArgs.empty(), tone, false);
    }

    public void shutdown() {
        actionBar.shutdown();
        titles.shutdown();
        bossBars.shutdown();
        bossBarGenerations.clear();
        titleClaims.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        actionBar.clearAll(player);
        titles.clear(player);
        bossBars.hideAll(player);
        removeGenerations(player.getUniqueId());
    }

    private void display(
            Player player,
            String purpose,
            Set<PresentationChannel> channels,
            TextKey key,
            MessageArgs arguments,
            FeedbackTone tone,
            boolean sound
    ) {
        display(player, purpose, channels, key, arguments, tone, sound, ShapedMessages.HUD_TITLE);
    }

    private void display(
            Player player,
            String purpose,
            Set<PresentationChannel> channels,
            TextKey key,
            MessageArgs arguments,
            FeedbackTone tone,
            boolean sound,
            TextKey title
    ) {
        ComponentText markup = language.renderWithoutPrefix(player, key, arguments);
        ComponentText chatMarkup = language.renderPrefixed(player, key, arguments);
        Set<PresentationChannel> selected = Set.copyOf(channels);
        Runnable delivery = () -> displayOwned(player, purpose, selected, markup, chatMarkup, tone, sound, title);
        Runnable retired = () -> retire(player.getUniqueId(), purpose);
        if (!FoliaScheduler.runEntity(plugin, player, delivery, 0L, retired)) {
            plugin.getLogger().warning("Could not schedule ShapedPortals feedback for " + player.getName());
        }
    }

    private void displayOwned(
            Player player,
            String purpose,
            Set<PresentationChannel> channels,
            ComponentText markup,
            ComponentText chatMarkup,
            FeedbackTone tone,
            boolean sound,
            TextKey title
    ) {
        RuntimeConfig config = configService.runtime();
        if (channels.contains(PresentationChannel.CHAT)) {
            ComponentMessenger.send(player, chatMarkup);
        }
        if (channels.contains(PresentationChannel.ACTION_BAR)) {
            actionBar.publish(player, new HudSegment(
                    purpose,
                    HudPriority.NOTICE,
                    config.overlayDurationTicks() * 50L,
                    List.of(HudSlot.RIGHT, HudSlot.LEFT),
                    markup.legacy()
            ));
        }
        if (channels.contains(PresentationChannel.TITLE)) {
            showTitle(player, purpose, markup, title, config);
        }
        if (channels.contains(PresentationChannel.BOSS_BAR)) {
            showBossBar(player, purpose, markup, tone, config.overlayDurationTicks());
        }
        if (sound) {
            playSound(player, tone);
        }
    }

    private void showTitle(Player player, String purpose, ComponentText markup, TextKey title, RuntimeConfig config) {
        String key = generationKey(player.getUniqueId(), purpose);
        HudTitleClaim previous = titleClaims.remove(key);
        if (previous != null) {
            previous.release();
        }
        long ttlMillis = Math.max(config.overlayDurationTicks(),
                (long) config.titleFadeInTicks() + config.titleStayTicks() + config.titleFadeOutTicks()) * 50L;
        HudTitleClaim claim = titles.open(player, purpose, HudPriority.NOTICE, ttlMillis);
        if (!claim.resolve()) {
            claim.release();
            return;
        }
        titleClaims.put(key, claim);
        ComponentMessenger.showTitle(
                player,
                language.render(player, title),
                markup,
                ticks(config.titleFadeInTicks()),
                ticks(config.titleStayTicks()),
                ticks(config.titleFadeOutTicks())
        );
        long releaseTicks = Math.max(config.overlayDurationTicks(),
                (long) config.titleFadeInTicks() + config.titleStayTicks() + config.titleFadeOutTicks());
        FoliaScheduler.runEntity(plugin, player,
                () -> releaseTitle(key, claim),
                releaseTicks,
                () -> retireTitle(key, claim));
    }

    private void showBossBar(Player player, String purpose, ComponentText markup, FeedbackTone tone, long durationTicks) {
        String key = generationKey(player.getUniqueId(), purpose);
        long generation = generations.incrementAndGet();
        bossBarGenerations.put(key, generation);
        bossBars.show(
                player,
                purpose,
                markup.legacy(),
                1D,
                barColor(tone),
                BarStyle.SOLID,
                durationTicks * 50L + 1000L
        );
        FoliaScheduler.runEntity(plugin, player,
                () -> hideBossBar(player, purpose, key, generation),
                durationTicks,
                () -> bossBarGenerations.remove(key, generation));
    }

    private void hideBossBar(Player player, String purpose, String key, long generation) {
        if (!bossBarGenerations.remove(key, generation)) {
            return;
        }
        bossBars.hide(player, purpose);
    }

    private void playSound(Player player, FeedbackTone tone) {
        if (tone == FeedbackTone.INFO) {
            return;
        }
        Sound selected = tone == FeedbackTone.SUCCESS ? successSound : failureSound;
        float pitch = tone == FeedbackTone.SUCCESS ? 1.35F : 0.75F;
        player.playSound(player.getLocation(), selected, SoundCategory.PLAYERS, 0.7F, pitch);
    }

    private void retire(UUID playerId, String purpose) {
        actionBar.retire(playerId, purpose);
        bossBarGenerations.remove(generationKey(playerId, purpose));
        bossBars.retire(playerId, purpose);
    }

    private void removeGenerations(UUID playerId) {
        String prefix = playerId + "|";
        bossBarGenerations.keySet().removeIf(key -> key.startsWith(prefix));
        titleClaims.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void releaseTitle(String key, HudTitleClaim claim) {
        titleClaims.remove(key, claim);
        claim.release();
    }

    private void retireTitle(String key, HudTitleClaim claim) {
        titleClaims.remove(key, claim);
        claim.retire();
    }

    private String generationKey(UUID playerId, String purpose) {
        return playerId + "|" + purpose;
    }

    private Duration ticks(long ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }

    private BarColor barColor(FeedbackTone tone) {
        return switch (tone) {
            case SUCCESS -> BarColor.GREEN;
            case FAILURE -> BarColor.RED;
            case INFO -> BarColor.PURPLE;
        };
    }

    private Sound sound(String key, Sound fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        Sound sound = namespacedKey == null ? null : Registry.SOUNDS.get(namespacedKey);
        return sound == null ? fallback : sound;
    }
}
