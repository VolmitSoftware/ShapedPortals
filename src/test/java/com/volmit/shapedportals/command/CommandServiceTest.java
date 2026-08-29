package com.volmit.shapedportals.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CommandServiceTest {
    @Test
    void normalizesNaturalPortalAndPageArgumentsForDirector() {
        assertThat(CommandService.normalizeOptionalArguments(List.of("teleport", "portal-id")))
                .containsExactly("teleport", "portal=portal-id");
        assertThat(CommandService.normalizeOptionalArguments(List.of("tp", "list")))
                .containsExactly("tp", "portal=list");
        assertThat(CommandService.normalizeOptionalArguments(List.of("portals", "2")))
                .containsExactly("portals", "page=2");
        assertThat(CommandService.normalizeOptionalArguments(List.of("language", "fr_FR")))
                .containsExactly("language", "locale=fr_FR");
        assertThat(CommandService.normalizeOptionalArguments(List.of("language", "menu")))
                .containsExactly("language", "locale=menu");
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
    void routesBareAndPagedLanguageRequestsToThePicker() {
        assertThat(CommandService.languageMenuPage(List.of("language"))).hasValue(1);
        assertThat(CommandService.languageMenuPage(List.of("language", "page=2"))).hasValue(2);
        assertThat(CommandService.languageMenuPage(List.of("language", "fr_FR"))).isEmpty();
        assertThat(CommandService.languageMenuPage(List.of("language", "page=invalid"))).isEmpty();
    }
}
