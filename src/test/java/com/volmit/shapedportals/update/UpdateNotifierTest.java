package com.volmit.shapedportals.update;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.update.GitHubReleaseChecker;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateNotifierTest {
    private final ShapedPortals plugin = mock(ShapedPortals.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final LanguageService language = mock(LanguageService.class);
    private final GitHubReleaseChecker checker = mock(GitHubReleaseChecker.class);
    private final Player player = mock(Player.class);
    private final Player.Spigot spigot = mock(Player.Spigot.class);
    private final ShapedPortalsConfig config = new ShapedPortalsConfig();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final CompletableFuture<Optional<GitHubReleaseChecker.Release>> response = new CompletableFuture<>();
    private UpdateNotifier notifier;

    @BeforeEach
    void setUp() {
        when(plugin.getConfigService()).thenReturn(configService);
        when(plugin.getLanguageService()).thenReturn(language);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(
                "ShapedPortals", "2.0.0-26.2", ShapedPortals.class.getName()));
        when(plugin.isEnabled()).thenReturn(true);
        when(configService.runtime()).thenAnswer(invocation -> RuntimeConfig.from(config));
        when(player.isOnline()).thenReturn(true);
        when(player.spigot()).thenReturn(spigot);
        when(language.renderPrefixed(eq(player), eq(ShapedMessages.UPDATE_AVAILABLE), any(MessageArgs.class)))
                .thenReturn(ComponentText.markup("&eShapedPortals 2.0.1 is available"));
        when(checker.check()).thenReturn(response);
        notifier = new UpdateNotifier(plugin, checker);
        notifier.reconfigure();
    }

    @Test
    void ordinaryPlayersDoNotRequestOrReceiveANotice() {
        notifier.onJoin(new PlayerJoinEvent(player, (String) null));

        verify(checker, never()).check();
        verifyNoInteractions(language);
    }

    @Test
    void operatorsReceiveOneDeferredNoticeEvenWithoutPermission() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            verifyNoInteractions(language);
            assertThat(scheduled).hasSize(1);
            scheduled.get(0).run();
        }

        verify(language).renderPrefixed(eq(player), eq(ShapedMessages.UPDATE_AVAILABLE), any(MessageArgs.class));
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(player).sendRichMessage(messages.capture());
        Component component = MiniMessage.miniMessage().deserialize(messages.getValue());
        assertThat(ComponentText.markup(messages.getValue()).plain()).isEqualTo("ShapedPortals 2.0.1 is available");
        assertThat(component.clickEvent()).isEqualTo(ClickEvent.openUrl(
                "https://github.com/VolmitSoftware/ShapedPortals/releases/tag/2.0.1"));
    }

    @Test
    void permittedNonOperatorsReceiveANotice() {
        when(player.hasPermission("shapedportals.update")).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            scheduled.get(0).run();
        }

        verify(language).renderPrefixed(eq(player), eq(ShapedMessages.UPDATE_AVAILABLE), any(MessageArgs.class));
    }

    @Test
    void noNewerReleaseDoesNotScheduleANotice() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            response.complete(Optional.empty());
            assertThat(scheduled).isEmpty();
        }
    }

    @Test
    void disablingAndReenablingInvalidatesAnInFlightJoin() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            config.general.updateNotifications = false;
            notifier.reconfigure();
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            config.general.updateNotifications = true;
            notifier.reconfigure();
            completeUpdate();
            assertThat(scheduled).isEmpty();
        }

        verify(checker).setEnabled(false);
        verify(checker, times(2)).setEnabled(true);
        verify(checker).check();
        verifyNoInteractions(language);
    }

    @Test
    void permissionIsRecheckedOnTheOwningThread() {
        when(player.hasPermission("shapedportals.update")).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            when(player.hasPermission("shapedportals.update")).thenReturn(false);
            scheduled.get(0).run();
        }

        verifyNoInteractions(language);
    }

    @Test
    void disconnectedPlayersDoNotReceiveQueuedNotices() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            when(player.isOnline()).thenReturn(false);
            scheduled.get(0).run();
        }

        verifyNoInteractions(language);
    }

    @Test
    void configOptOutStopsQueuedNoticesBeforeReconfiguration() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            config.general.updateNotifications = false;
            scheduled.get(0).run();
        }

        verifyNoInteractions(language);
    }

    @Test
    void closingStopsQueuedNoticesAndChecker() {
        when(player.isOp()).thenReturn(true);
        try (MockedStatic<FoliaScheduler> scheduler = captureScheduler()) {
            notifier.onJoin(new PlayerJoinEvent(player, (String) null));
            completeUpdate();
            notifier.close();
            scheduled.get(0).run();
        }

        verify(checker).close();
        verifyNoInteractions(language);
    }

    private MockedStatic<FoliaScheduler> captureScheduler() {
        MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(20L)))
                .thenAnswer(invocation -> {
                    scheduled.add(invocation.getArgument(2, Runnable.class));
                    return true;
                });
        return scheduler;
    }

    private void completeUpdate() {
        response.complete(Optional.of(new GitHubReleaseChecker.Release(
                "2.0.1", "https://github.com/VolmitSoftware/ShapedPortals/releases/tag/2.0.1")));
    }
}
