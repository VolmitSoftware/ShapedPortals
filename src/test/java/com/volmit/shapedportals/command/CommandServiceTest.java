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
    void routesOnlyLanguageMenuPagesThroughTheShapedPortalsSelector() {
        assertThat(CommandService.languageMenuRequest(new String[0], true, true))
                .isEqualTo(new CommandService.LanguageMenuRequest(true, 1));
        assertThat(CommandService.languageMenuRequest(new String[]{"server"}, true, true))
                .isEqualTo(new CommandService.LanguageMenuRequest(false, 1));
        assertThat(CommandService.languageMenuRequest(new String[]{"self", "page=3"}, true, true))
                .isEqualTo(new CommandService.LanguageMenuRequest(true, 3));
        assertThat(CommandService.languageMenuRequest(new String[]{"self", "fr_FR"}, true, true)).isNull();
        assertThat(CommandService.languageMenuRequest(new String[]{"server", "edit"}, true, true)).isNull();
        assertThat(CommandService.languageMenuRequest(new String[]{"self"}, false, true)).isNull();
    }
}
