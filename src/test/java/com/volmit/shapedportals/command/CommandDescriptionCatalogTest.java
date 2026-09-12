package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.localization.LanguageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDescriptionCatalogTest {
    @TempDir
    Path directory;

    @Test
    void directorDescriptionsMatchCatalogDefinitions() {
        MessageCatalog catalog = ShapedMessages.catalog();
        assertCommandType(ShapedPortalsCommands.class, catalog);
        assertCommandType(ShapedPortalsDebugCommands.class, catalog);
    }

    @Test
    void debugIsAGroupWithDumpAndVersionCommands() {
        assertThat(ShapedPortalsDebugCommands.class.getAnnotation(Director.class).name()).isEqualTo("debug");
        assertThat(Arrays.stream(ShapedPortalsDebugCommands.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Director.class))
                .filter(Objects::nonNull)
                .map(Director::name))
                .containsExactlyInAnyOrder("dump", "version");
        assertThat(Arrays.stream(ShapedPortalsCommands.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Director.class))
                .filter(Objects::nonNull)
                .map(Director::name))
                .doesNotContain("debug");
    }

    @Test
    void bareDebugCommandResolvesItsOwnHelpPage() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(
                DirectorEngineFactory.create(new ShapedPortalsCommands(null)),
                List.of("debug")
        ).orElseThrow();

        assertThat(page.node().getDescriptor().getName()).isEqualTo("debug");
        assertThat(page.entries())
                .extracting(entry -> entry.getDescriptor().getName())
                .containsExactlyInAnyOrder("dump", "version");
    }

    @Test
    void rootLanguageEntryRunsTheLanguageMenuImmediately() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(
                DirectorEngineFactory.create(new ShapedPortalsCommands(null)),
                List.of()
        ).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(
                page, DirectorMiniMenu.Theme.reactBlue(),
                new LanguageService(directory.toFile(), Logger.getAnonymousLogger()).directorResolver()));

        assertThat(rendered).contains("<click:run_command:/shapedportals language>");
        assertThat(rendered).doesNotContain("<click:suggest_command:/shapedportals language ");
    }

    private void assertCommandType(Class<?> commandType, MessageCatalog catalog) {
        assertDescription(commandType.getAnnotation(Director.class), catalog);
        for (Method method : commandType.getDeclaredMethods()) {
            Director director = method.getAnnotation(Director.class);
            if (director != null) {
                assertDescription(director, catalog);
                for (Parameter parameter : method.getParameters()) {
                    Param param = parameter.getAnnotation(Param.class);
                    if (param != null && !param.descriptionKey().isBlank()) {
                        assertDescription(param.description(), param.descriptionKey(), catalog);
                    }
                }
            }
        }
    }

    @Test
    void omitsManualReloadAndHidesTheRootVersionShortcut() {
        assertThat(Arrays.stream(ShapedPortalsCommands.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Director.class))
                .filter(Objects::nonNull)
                .filter(command -> !command.hidden())
                .map(Director::name))
                .doesNotContain("reload", "version");
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(
                DirectorEngineFactory.create(new ShapedPortalsCommands(null)), List.of()).orElseThrow();
        assertThat(page.entries()).extracting(entry -> entry.getDescriptor().getName()).doesNotContain("version");
    }

    private void assertDescription(Director director, MessageCatalog catalog) {
        assertDescription(director.description(), director.descriptionKey(), catalog);
    }

    private void assertDescription(String description, String descriptionKey, MessageCatalog catalog) {
        MessageKey key = catalog.require(descriptionKey);

        assertThat(key).isInstanceOf(TextKey.class);
        assertThat(((TextKey) key).english()).isEqualTo(description);
    }
}
