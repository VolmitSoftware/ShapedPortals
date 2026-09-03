package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageServiceTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final Logger TEST_LOGGER = Logger.getLogger(LanguageServiceTest.class.getName());

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOneEditableEnglishLanguageFile() throws IOException {
        LanguageService service = service();
        LanguageService.PreparedLanguage prepared = service.prepare("en_US");
        service.install(prepared);
        Path file = service.languageFile("en_US").toPath();
        String toml = Files.readString(file);

        assertThat(prepared.file().toPath()).isEqualTo(file);
        assertThat(file).isRegularFile();
        assertThat(service.remoteCatalogReference()).contains("main");
        assertThat(service.hasRemoteCatalogLocale("fr_FR")).isTrue();
        assertThat(service.hasRemoteCatalogLocale("en_US")).isFalse();
        assertThat(toml)
                .contains("an editable language file")
                .contains("never automatically replaces local changes")
                .contains("Colors: &0-&f")
                .contains("[runtime]")
                .contains("[command.feedback.reload]")
                .contains("[portal.navigation.error]")
                .contains("[hud]")
                .contains("[gui.title]")
                .contains("[director.runtime]")
                .contains("{prefix}=the global runtime.prefix value")
                .contains("&cYou do not have permission")
                .contains("&d&lShapedPortals")
                .doesNotContain("<red>")
                .doesNotContain("<gradient:");
        assertThat(headerPlaceholders(toml)).containsExactlyInAnyOrderElementsOf(catalogPlaceholders());
    }

    @Test
    void downloadedLanguageIgnoresKeysAddedAfterTheCurrentJar() throws IOException {
        LanguageService service = service();
        service.prepare("en_US");
        String latest = Files.readString(service.languageFile("en_US").toPath())
                + "\n[future]\nmessage = \"Newer catalog value\"\n";

        service.validateDownloadedContent("es_ES", latest);
    }

    @Test
    void downloadedLanguageCanUseEnglishForKeysAddedAfterItsTranslation() throws IOException {
        LanguageService service = service();

        service.validateDownloadedContent("es_ES", "[runtime]\nprefix = \"&6Portales &8> \"\n");
    }

    @Test
    void preservesAnExistingLanguageFileAcrossPrepareAndRestart() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("en_US").toPath();
        Files.createDirectories(file.getParent());
        String content = "[runtime]\nprefix = \"&6Local &8> \"\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        service.install(service.prepare("en_US"));
        LanguageService restarted = service();
        restarted.install(restarted.prepare("en_US"));

        assertThat(service.render(ShapedMessages.PREFIX)).contains("Local");
        assertThat(restarted.render(ShapedMessages.PREFIX)).contains("Local");
        assertThat(Files.readString(file)).isEqualTo(content);
    }

    @Test
    void acceptsAnExistingPortalListEntryWithoutTheOptionalPortalType() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("en_US").toPath();
        Files.createDirectories(file.getParent());
        String content = """
                [portal.navigation.list]
                entry = "&d{id}&r &8›&r &f{world}&r\\n&8  ├&r &7Location:&r &f{x}, {y}, {z}&r &8•&r &7Axis:&r &f{axis}&r &8•&r &7Cells:&r &f{blocks}&r\\n&8  └&r &7Creator:&r &f{creator}&r &8•&r &7Created:&r &f{created}&r"
                """;
        Files.writeString(file, content, StandardCharsets.UTF_8);

        service.install(service.prepare("en_US"));
        String rendered = service.render(ShapedMessages.PORTAL_LIST_ENTRY, MessageArgs.builder()
                .untrusted("id", "portal")
                .untrusted("world", "world")
                .untrusted("type", "NETHER")
                .untrusted("x", "1")
                .untrusted("y", "64")
                .untrusted("z", "2")
                .untrusted("axis", "X")
                .untrusted("blocks", "6")
                .untrusted("creator", "operator")
                .untrusted("created", "2026-09-03 10:44:19")
                .build());

        assertThat(rendered).contains("portal", "world", "operator").doesNotContain("NETHER");
        assertThat(Files.readString(file)).isEqualTo(content);
    }

    @Test
    void loadsTheDirectLocaleOverCodeOwnedEnglish() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("fr_FR").toPath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[runtime]\nprefix = \"&6Portails &8> \"\n", StandardCharsets.UTF_8);

        LanguageService.PreparedLanguage prepared = service.prepare("fr_FR");
        service.install(prepared);

        assertThat(prepared.selectionReady()).isTrue();
        assertThat(service.render(ShapedMessages.PREFIX)).contains("Portails");
        assertThat(service.render(ShapedMessages.NO_PERMISSION))
                .startsWith(service.render(ShapedMessages.PREFIX))
                .contains("You do not have permission");
    }

    @Test
    void ignoresUnknownEntriesWithoutChangingTheirBytes() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("en_US").toPath();
        Files.createDirectories(file.getParent());
        String content = """
                [portal.navigation.list]
                previous = "&7Retired"
                retired_number = 12

                [runtime]
                prefix = "&6Current &8> "
                """;
        Files.writeString(file, content, StandardCharsets.UTF_8);

        service.install(service.prepare("en_US"));

        assertThat(service.render(ShapedMessages.PREFIX)).contains("Current");
        assertThat(Files.readString(file)).isEqualTo(content);
    }

    @Test
    void invalidSelectedFileCanFallBackWithoutBeingReplaced() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("fr_FR").toPath();
        Files.createDirectories(file.getParent());
        byte[] invalid = "[runtime]\nprefix = 42\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);

        assertThatThrownBy(() -> service.prepare("fr_FR")).isInstanceOf(IOException.class);
        LanguageService.PreparedLanguage fallback = service.englishFallback("fr_FR");
        service.install(fallback);

        assertThat(service.render(ShapedMessages.PREFIX)).isEqualTo(ShapedMessages.PREFIX.english());
        assertThat(service.activeFile()).isEqualTo(service.languageFile("fr_FR"));
        assertThat(Files.readAllBytes(file)).containsExactly(invalid);
    }

    @Test
    void rendersLegacyRgbAndMiniMessageWhileKeepingArgumentsLiteral() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("en_US").toPath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[portal.notice]\nfailed = \"&cFailure &#12ABef\\\\&eLiteral {reason}\"\n",
                StandardCharsets.UTF_8);
        service.install(service.prepare("en_US"));

        String rendered = service.render(ShapedMessages.PORTAL_FAILED, MessageArgs.builder()
                .untrusted("reason", "&4[12ABef]<red>unsafe</red>")
                .build());
        ComponentText message = ComponentText.markup(rendered);

        assertThat(message.plain()).isEqualTo("Failure &eLiteral &4[12ABef]<red>unsafe</red>");
        assertThat(message.legacy()).contains("\u00a7cFailure");
        assertThat(message.legacy()).contains("\u00a7x\u00a71\u00a72\u00a7a\u00a7b\u00a7e\u00a7f");
        assertThat(message.legacy()).doesNotContain("\u00a7eLiteral");
    }

    @Test
    void discoversRemoteAndSafeDirectCustomLocales() throws IOException {
        LanguageService service = service();
        service.prepare("en_US");
        Files.writeString(service.languageDirectory().toPath().resolve("pirate.toml"), "");
        Files.writeString(service.languageDirectory().toPath().resolve("notes.txt"), "ignored");
        Files.writeString(service.languageDirectory().toPath().resolve("bad.locale.toml"), "ignored");
        service.prepare("en_US");

        List<String> expectedLocales = Stream.concat(VolmitLocales.all().stream(), Stream.of("pirate"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        assertThat(service.availableLocales()).containsExactlyElementsOf(expectedLocales);
        assertThat(service.availableLocale("FR_fr")).contains("fr_FR");
        assertThat(service.availableLocale("PIRATE")).contains("pirate");
        assertThat(service.availableLocale("../en_US")).isEmpty();
        assertThat(service.isLanguageFile(service.languageFile("en_US"))).isTrue();
        assertThat(service.isLanguageFile(temporaryDirectory.resolve("en_US.toml").toFile())).isFalse();
        assertThatThrownBy(() -> service.languageFile("../outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe name");
    }

    @Test
    void remoteLocaleIsNotReadyUntilItsDirectFileExists() throws IOException {
        LanguageService service = service();

        assertThat(service.prepare("en_US").selectionReady()).isTrue();
        assertThat(service.prepare("fr_FR").selectionReady()).isFalse();
        assertThat(service.languageFile("fr_FR")).doesNotExist();
        Files.writeString(service.languageFile("fr_FR").toPath(),
                "[runtime]\nprefix = \"&6Local &8> \"\n", StandardCharsets.UTF_8);

        assertThat(service.prepare("fr_FR").selectionReady()).isTrue();
    }

    @Test
    void completedDownloadStillActivatesWhenTheHotReloadObserverFails() throws IOException {
        Logger logger = Logger.getLogger(LanguageServiceTest.class.getName() + ".download");
        logger.setUseParentHandlers(false);
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        LanguageService service = new LanguageService(temporaryDirectory.toFile(), logger);
        Path file = service.languageFile("fr_FR").toPath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[runtime]\nprefix = \"&6Portails &8> \"\n", StandardCharsets.UTF_8);
        service.setSelfWriteListener((written, content) -> {
            throw new IllegalStateException("observer failed");
        });
        RemoteLanguageCatalog.DownloadResult download = new RemoteLanguageCatalog.DownloadResult(
                "fr_FR",
                URI.create("https://raw.githubusercontent.com/VolmitSoftware/ShapedPortals/main/"
                        + "src/main/resources/languages/fr_FR.toml"),
                file,
                null
        );
        AtomicReference<RemoteLanguageCatalog.DownloadResult> completed = new AtomicReference<>();

        service.remoteInstallCompleted(file.toFile(), download, completed::set);

        assertThat(completed.get()).isEqualTo(download);
        assertThat(handler.record()).isNotNull();
        assertThat(handler.record().getMessage()).contains("continuing with activation");
    }

    @Test
    void rejectsInvalidMarkupAndPreservesTheFile() throws IOException {
        LanguageService service = service();
        Path file = service.languageFile("en_US").toPath();
        Files.createDirectories(file.getParent());
        String content = "[command.feedback.reload]\nsuccess = \"<green><bold>Broken {duration} {locale}</green>\"\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.prepare("en_US"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("invalid message markup");
        assertThat(Files.readString(file)).isEqualTo(content);
    }

    @Test
    void editorUpdatesTheDirectFileAndPreservesHeaderAndUnknownValues() throws IOException {
        LanguageService service = service();
        service.prepare("en_US");
        Path file = service.languageFile("en_US").toPath();
        String original = Files.readString(file) + "\n[unknown]\nenabled = true\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        AtomicReference<String> selfWrite = new AtomicReference<>();
        service.setSelfWriteListener((written, content) -> selfWrite.set(content));

        LanguageService.PreparedLanguage updated = service.updateMessage(
                "en_US", ShapedMessages.NO_PERMISSION.id(), "&cEdited permission message&r");
        String editedFile = Files.readString(file);
        LanguageService.EditableMessage edited = service.editableMessages("en_US").stream()
                .filter(message -> message.id().equals(ShapedMessages.NO_PERMISSION.id()))
                .findFirst()
                .orElseThrow();

        assertThat(updated.selectionReady()).isTrue();
        assertThat(editedFile)
                .startsWith("# ShapedPortals language: en_US")
                .contains("[runtime.permission]\ndenied = \"&cEdited permission message&r\"")
                .contains("[unknown]\nenabled = true")
                .doesNotContain("  denied =");
        assertThat(edited.effectiveValue()).isEqualTo("&cEdited permission message&r");
        assertThat(edited.previewValue()).isEqualTo("&cEdited permission message&r");
        assertThat(edited.placeholders()).isEmpty();
        assertThat(selfWrite.get()).isEqualTo(editedFile);

        String safe = editedFile;
        assertThatThrownBy(() -> service.updateMessage(
                "en_US", ShapedMessages.NO_PERMISSION.id(), "<red>Unclosed"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("invalid message markup");
        assertThat(Files.readString(file)).isEqualTo(safe);
    }

    @Test
    void supportsPersistentPlayerLanguagesAndASeparateServerDefault() throws Exception {
        LanguageService service = service();
        LanguageService.PreparedLanguage english = service.prepare("en_US");
        Path pirate = service.languageFile("pirate").toPath();
        Files.createDirectories(pirate.getParent());
        String pirateContent = "[runtime]\nprefix = \"&6Pirate &8> \"\n";
        Files.writeString(pirate, pirateContent, StandardCharsets.UTF_8);
        service.prepare("pirate");
        service.install(english);
        AtomicReference<String> defaultLocale = new AtomicReference<>("en_US");
        PluginLanguageService selections = service.initializeSelections(defaultLocale::get, (locale, snapshot) -> {
            defaultLocale.set(locale);
            service.install(new LanguageService.PreparedLanguage(
                    locale,
                    service.languageFile(locale),
                    snapshot,
                    true
            ));
        });
        UUID playerId = UUID.randomUUID();

        try {
            selections.selectPlayer(playerId, "pirate").get(5L, TimeUnit.SECONDS);

            assertThat(service.render(ShapedMessages.PREFIX)).doesNotContain("Pirate");
            assertThat(LanguageAudience.call(playerId, () -> service.render(ShapedMessages.PREFIX)))
                    .contains("Pirate");
            assertThat(LanguageAudience.call(playerId, () -> service.render(ShapedMessages.NO_PERMISSION)))
                    .contains("Pirate", "You do not have permission");
            assertThat(Files.readString(temporaryDirectory.resolve("language-preferences.properties")))
                    .contains(playerId + "=pirate");
            assertThat(Files.readString(pirate)).isEqualTo(pirateContent);

            selections.selectDefault("pirate").get(5L, TimeUnit.SECONDS);

            assertThat(defaultLocale.get()).isEqualTo("pirate");
            assertThat(service.render(ShapedMessages.PREFIX)).contains("Pirate");

            selections.clearPlayer(playerId).get(5L, TimeUnit.SECONDS);
            assertThat(selections.playerLocale(playerId)).isEmpty();
        } finally {
            service.close();
        }
    }

    private LanguageService service() {
        return new LanguageService(temporaryDirectory.toFile(), TEST_LOGGER);
    }

    private Set<String> catalogPlaceholders() {
        return ShapedMessages.catalog().keys().stream()
                .flatMap(key -> key.placeholders().stream())
                .collect(Collectors.toSet());
    }

    private Set<String> headerPlaceholders(String content) {
        String header = content.substring(0, content.indexOf("\n[runtime]"));
        Matcher matcher = PLACEHOLDER.matcher(header);
        HashSet<String> placeholders = new HashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return Set.copyOf(placeholders);
    }

    private static final class CapturingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord record) {
            this.record = record;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private LogRecord record() {
            return record;
        }
    }
}
