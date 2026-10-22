package com.volmit.shapedportals.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CommandServiceTest {
    @Test
    void normalizesNaturalPortalAndPageArgumentsForDirector() {
        assertThat(CommandService.normalizeOptionalArguments(List.of("teleport", "portal-id")))
                .containsExactly("teleport", "portal=portal-id");
        assertThat(CommandService.normalizeOptionalArguments(List.of("tp", "list")))
                .containsExactly("tp", "portal=list");
        assertThat(CommandService.normalizeOptionalArguments(List.of("portals", "2")))
                .containsExactly("portals", "page=2");
        assertThat(CommandService.normalizeOptionalArguments(List.of("language", "self")))
                .containsExactly("language", "self");
    }

    @Test
    void preservesOmittedKeyedAndUnrelatedArguments() {
        assertThat(CommandService.normalizeOptionalArguments(List.of("teleport")))
                .containsExactly("teleport");
        assertThat(CommandService.normalizeOptionalArguments(List.of("teleport", "portal=id")))
                .containsExactly("teleport", "portal=id");
        assertThat(CommandService.normalizeOptionalArguments(List.of("portals", "page=2")))
                .containsExactly("portals", "page=2");
        assertThat(CommandService.normalizeOptionalArguments(List.of("status", "extra")))
                .containsExactly("status", "extra");
    }

    @Test
    void rootSuggestionsFollowEachSubcommandPermission() {
        CommandSender player = mock(CommandSender.class);
        when(player.hasPermission("shapedportals.command")).thenReturn(true);
        when(player.hasPermission("volmit.language.self")).thenReturn(true);
        when(player.hasPermission("shapedportals.language.self")).thenReturn(true);

        assertThat(CommandService.rootSuggestions(player, "")).containsExactly("status", "language");

        CommandSender lister = mock(CommandSender.class);
        when(lister.hasPermission("shapedportals.portals")).thenReturn(true);

        assertThat(CommandService.rootSuggestions(lister, "")).containsExactly("portals", "teleport");
        assertThat(CommandService.rootSuggestions(lister, "te")).containsExactly("teleport");
    }
}
