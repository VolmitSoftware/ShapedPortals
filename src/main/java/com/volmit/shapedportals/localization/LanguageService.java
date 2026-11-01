package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LanguageService {
    public static final String MANIFEST_RESOURCE = "shapedportals-language-source.properties";
    private static final long MAXIMUM_LANGUAGE_BYTES = LanguageDirectory.MAXIMUM_BYTES;
    private static final int MAXIMUM_BASE_DEPTH = 4;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final PluralSelector ENGLISH_PLURALS = (locale, quantity) -> quantity.doubleValue() == 1D ? "one" : "other";
    private static final MessageCatalog CATALOG = ShapedMessages.catalog();

    private final LanguageDirectory directory;
    private final File languageDirectory;
    private final Logger logger;
    private final LocalizationManager manager;
    private final AtomicReference<File> activeFile;
    private final LanguageSourceManifest manifest;
    private final LanguageUpstreamSync sync;
    private final Throwable sourceFailure;
    private final Path preferenceFile;
    private volatile List<String> availableLocales = List.of();
    private volatile Map<String, LanguageMeta> metas = Map.of();
    private volatile BiConsumer<File, String> selfWriteListener;
    private volatile PluginLanguageService selections;

    public LanguageService(File dataFolder, Logger logger) {
        this(dataFolder, logger, GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT);
    }

    LanguageService(File dataFolder, Logger logger, URI apiRoot, URI rawRoot) {
        languageDirectory = new File(dataFolder, "languages");
        directory = new LanguageDirectory(languageDirectory);
        preferenceFile = directory.root().resolve("language-preferences.properties");
        this.logger = Objects.requireNonNull(logger, "logger");
        validateCatalogTemplates();
        manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS));
        activeFile = new AtomicReference<>(languageFile(CATALOG.englishLocale()));
        LanguageSourceManifest loadedManifest = null;
        LanguageUpstreamSync loadedSync = null;
        Throwable failure = null;
        try {
            loadedManifest = LanguageSourceManifest.load(LanguageService.class.getClassLoader(), MANIFEST_RESOURCE);
            GitHubLanguageSource source = new GitHubLanguageSource(loadedManifest, "ShapedPortals", apiRoot, rawRoot);
            LanguageUpstreamStore store = new LanguageUpstreamStore(directory, loadedManifest);
            loadedSync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                    "ShapedPortals",
                    CATALOG,
                    directory,
                    loadedManifest,
                    source,
                    store,
                    () -> LanguageReferenceRenderer.render(CATALOG, englishHeader(CATALOG.englishLocale())),
                    this::validateContent,
                    this::commitUpstream,
                    this::noteUpstreamWrite,
                    this::applyUpstream,
                    logger
            ));
        } catch (RuntimeException exception) {
            failure = exception;
        }
        manifest = loadedManifest;
        sync = loadedSync;
        sourceFailure = failure;
    }

    public synchronized PreparedLanguage prepare(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        directory.prepare();
        ensureEnglish();
        File file = languageFile(requiredLocale);
        Path filePath = file.toPath().toAbsolutePath().normalize();
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(filePath))) {
            throw new IOException("Language path is not a regular file: " + file.getName());
        }
        if (!file.exists() && !isRepositoryLocale(requiredLocale)) {
            createCustomLanguageIfMissing(requiredLocale);
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        if (file.isFile()) {
            overlays.add(createOverlay(requiredLocale, file.getPath(), loadEditableLanguage(file)));
            overlays.addAll(baseOverlays(requiredLocale));
        }
        boolean selectionReady = file.isFile();
        PreparedLanguage prepared = createPrepared(requiredLocale, file, overlays, selectionReady);
        refreshAvailableLocales();
        return prepared;
    }

    public synchronized PreparedLanguage englishFallback(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        directory.prepare();
        ensureEnglish();
        refreshAvailableLocales();
        return new PreparedLanguage(
                requiredLocale,
                languageFile(requiredLocale),
                LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)),
                requiredLocale.equalsIgnoreCase(CATALOG.englishLocale())
        );
    }

    public void install(PreparedLanguage prepared) {
        manager.install(prepared.snapshot());
        activeFile.set(prepared.file());
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(prepared.locale(), selectionSnapshot(prepared.locale(), prepared.snapshot()));
        }
    }

    public synchronized boolean reloadSnapshot(File file, String content) {
        if (!isLanguageFile(file)) {
            return false;
        }
        String locale = canonicalLocale(file.getName().substring(0, file.getName().length() - ".toml".length()));
        try {
            PreparedLanguage prepared;
            if (content == null) {
                if (!Files.notExists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Language file could not be read: " + file);
                }
                prepared = new PreparedLanguage(locale, file,
                        LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)), false);
            } else {
                Map<String, String> values = parseStrictValues(content, locale);
                ArrayList<LocaleOverlay> overlays = new ArrayList<>();
                overlays.add(createOverlay(locale, file.getPath(), values));
                overlays.addAll(baseOverlays(locale, content));
                prepared = createPrepared(locale, file, overlays, true);
            }
            refreshAvailableLocales();
            if (file.getAbsoluteFile().equals(activeFile.get().getAbsoluteFile())) {
                install(prepared);
            } else {
                PluginLanguageService activeSelections = selections;
                if (activeSelections != null) {
                    activeSelections.cache(locale, selectionSnapshot(locale, prepared.snapshot()));
                }
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.WARNING, "Could not reload ShapedPortals language " + file
                    + "; keeping the last valid messages", exception);
            return false;
        }
    }

    public synchronized PluginLanguageService initializeSelections(
            Supplier<String> defaultLocale,
            PluginLanguageService.DefaultSelection defaultSelection
    ) {
        if (selections != null) {
            return selections;
        }
        PluginLanguageService created = new PluginLanguageService(new PluginLanguageService.Options(
                preferenceFile,
                this::availableLocales,
                defaultLocale,
                manager::snapshot,
                this::loadSelectionSnapshot,
                defaultSelection,
                logger
        ));
        selections = created;
        return created;
    }

    public PluginLanguageService selections() {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            throw new IllegalStateException("Language selections are not initialized");
        }
        return activeSelections;
    }

    public PluginLanguageEditor.Options editorOptions() {
        return new PluginLanguageEditor.Options(this::loadSelectionSnapshot, this::saveEditorMessage);
    }

    public BukkitLanguageSwitcher.LanguageUpdateAction updateAction() {
        return () -> {
            if (sync == null) {
                return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
                        sourceDescription(), System.currentTimeMillis()));
            }
            return sync.checkAsync(true).thenApply(this::logUpstreamReport);
        };
    }

    public BukkitLanguageSwitcher.LocaleDisplayNames displayNames() {
        return locale -> metas.getOrDefault(locale, LanguageMeta.empty()).name().orElse(null);
    }

    public CompletableFuture<LanguageUpstreamSync.SyncReport> startUpstreamCheck() {
        if (sync == null) {
            return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
                    sourceDescription(), System.currentTimeMillis()));
        }
        return sync.checkAsync(false).thenApply(this::logUpstreamReport);
    }

    public CompletableFuture<String> requestInstall(String locale) {
        String requiredLocale = canonicalLocale(locale);
        if (languageFile(requiredLocale).isFile()) {
            try {
                return CompletableFuture.completedFuture(directory.read(requiredLocale));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        if (sync == null || !isRepositoryLocale(requiredLocale)) {
            return CompletableFuture.failedFuture(new IOException("Language " + requiredLocale
                    + " is not available from " + sourceDescription()));
        }
        return sync.installAsync(requiredLocale);
    }

    public Optional<Throwable> sourceFailure() {
        return Optional.ofNullable(sourceFailure);
    }

    public String sourceDescription() {
        if (manifest == null) {
            return "unavailable";
        }
        return manifest.repository() + "@" + manifest.branch();
    }

    public Optional<LanguageUpstreamSync.Status> upstreamStatus() {
        return sync == null ? Optional.empty() : Optional.of(sync.status());
    }

    public boolean isRepositoryLocale(String locale) {
        return manifest != null && manifest.contains(locale);
    }

    public synchronized List<EditableMessage> editableMessages(String locale) throws IOException {
        PreparedLanguage prepared = prepare(locale);
        if (!prepared.selectionReady()) {
            throw new IOException("Language file is not installed: " + prepared.locale());
        }
        ArrayList<MessageKey> definitions = new ArrayList<>(CATALOG.keys());
        definitions.sort(Comparator.comparing(MessageKey::id, String.CASE_INSENSITIVE_ORDER));
        ArrayList<EditableMessage> messages = new ArrayList<>(definitions.size());
        for (MessageKey definition : definitions) {
            if (!(definition instanceof TextKey textKey)) {
                throw new IOException("Language editor does not support key shape: " + definition.id());
            }
            MessageValue effectiveValue = prepared.snapshot().value(textKey);
            if (!(effectiveValue instanceof TextValue textValue)) {
                throw new IOException("Language editor resolved a non-text value: " + definition.id());
            }
            messages.add(new EditableMessage(
                    definition.id(),
                    textValue.template(),
                    previewValue(prepared.snapshot(), definition, textValue.template()),
                    textValue.placeholders()
            ));
        }
        return List.copyOf(messages);
    }

    public synchronized PreparedLanguage updateMessage(String locale, String key, String value) throws IOException {
        PreparedLanguage current = prepare(locale);
        return mutateMessage(new PluginLanguageEditor.Edit(locale, key,
                current.snapshot().value(CATALOG.require(key)), new TextValue(value)));
    }

    public void setSelfWriteListener(BiConsumer<File, String> listener) {
        selfWriteListener = listener;
    }

    public ComponentText render(TextKey key) {
        return render(key, MessageArgs.empty());
    }

    public ComponentText render(TextKey key, MessageArgs arguments) {
        LocalizationSnapshot snapshot = selectedSnapshot(null);
        return render(snapshot, key, arguments, renderPrefix(snapshot));
    }

    public ComponentText render(CommandSender sender, TextKey key) {
        return render(sender, key, MessageArgs.empty());
    }

    public ComponentText render(CommandSender sender, TextKey key, MessageArgs arguments) {
        LocalizationSnapshot snapshot = selectedSnapshot(sender);
        return render(snapshot, key, arguments, renderPrefix(snapshot));
    }

    public ComponentText renderWithoutPrefix(TextKey key, MessageArgs arguments) {
        return renderWithoutPrefix(selectedSnapshot(null), key, arguments);
    }

    public ComponentText renderWithoutPrefix(CommandSender sender, TextKey key, MessageArgs arguments) {
        return renderWithoutPrefix(selectedSnapshot(sender), key, arguments);
    }

    public ComponentText renderPrefixed(TextKey key, MessageArgs arguments) {
        return render(key, arguments);
    }

    public ComponentText renderPrefixed(CommandSender sender, TextKey key, MessageArgs arguments) {
        return render(sender, key, arguments);
    }

    public void send(CommandSender sender, TextKey key) {
        ComponentMessenger.send(sender, render(sender, key));
    }

    public void send(CommandSender sender, TextKey key, MessageArgs arguments) {
        ComponentMessenger.send(sender, render(sender, key, arguments));
    }

    public void sendPrefixed(CommandSender sender, TextKey key) {
        sendPrefixed(sender, key, MessageArgs.empty());
    }

    public void sendPrefixed(CommandSender sender, TextKey key, MessageArgs arguments) {
        ComponentMessenger.send(sender, renderPrefixed(sender, key, arguments));
    }

    public String legacy(TextKey key) {
        return render(key).legacy();
    }

    public String legacy(TextKey key, MessageArgs arguments) {
        return render(key, arguments).legacy();
    }

    public String legacy(CommandSender sender, TextKey key) {
        return render(sender, key).legacy();
    }

    public String legacy(CommandSender sender, TextKey key, MessageArgs arguments) {
        return render(sender, key, arguments).legacy();
    }

    public DirectorTextResolver directorResolver() {
        return (key, arguments) -> render(key, arguments).miniMessage();
    }

    public File activeFile() {
        return activeFile.get();
    }

    public File languageFile(String locale) {
        return directory.languageFile(locale).toFile();
    }

    public File languageDirectory() {
        return languageDirectory;
    }

    public List<File> files() {
        try {
            ArrayList<File> installed = new ArrayList<>();
            for (String locale : directory.scan()) {
                installed.add(directory.languageFile(locale).toFile());
            }
            return List.copyOf(installed);
        } catch (IOException exception) {
            return List.of();
        }
    }

    public boolean isLanguageFile(File candidate) {
        return directory.isLanguageFile(candidate);
    }

    public List<String> availableLocales() {
        return availableLocales;
    }

    public String localeDisplayName(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        LanguageMeta meta = metas.getOrDefault(normalized, LanguageMeta.empty());
        return meta.name().or(() -> VolmitLocales.displayName(normalized)).orElse("Custom language");
    }

    public LanguageMeta meta(String locale) {
        return metas.getOrDefault(locale == null ? "" : locale.trim(), LanguageMeta.empty());
    }

    public Optional<String> availableLocale(String requested) {
        String normalized;
        try {
            normalized = LanguageDirectory.requireLocale(requested);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return availableLocales.stream()
                .filter(locale -> locale.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public void close() {
        selfWriteListener = null;
        PluginLanguageService activeSelections = selections;
        selections = null;
        if (activeSelections != null) {
            activeSelections.close();
        }
        if (sync != null) {
            sync.close();
        }
    }

    private <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            return action.apply();
        }
        return activeSelections.commitUpdate(action::apply);
    }

    private void noteUpstreamWrite(Path file, String content) {
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(file.toFile(), content);
        }
    }

    private void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result) throws IOException {
        File file = languageFile(locale);
        Map<String, String> values = parseStrictValues(content, locale);
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        overlays.add(createOverlay(locale, file.getPath(), values));
        overlays.addAll(baseOverlays(locale, content));
        PreparedLanguage prepared = createPrepared(locale, file, overlays, true);
        refreshAvailableLocales();
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(locale, selectionSnapshot(locale, prepared.snapshot()));
        }
        if (file.getAbsoluteFile().equals(activeFile.get().getAbsoluteFile())) {
            manager.install(prepared.snapshot());
        }
    }

    private LanguageUpstreamSync.SyncReport logUpstreamReport(LanguageUpstreamSync.SyncReport report) {
        String revision = report.revision().map(sha -> sha.substring(0, 7)).orElse("unknown");
        for (String locale : report.changedLocales()) {
            LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
            String source = locale.equals(CATALOG.englishLocale()) ? "the built-in catalog" : sourceDescription() + " (" + revision + ")";
            logger.info("Updated ShapedPortals language " + locale + " from " + source + ": " + result.summary() + ".");
        }
        if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
                && report.revision().isPresent()) {
            logger.info("ShapedPortals languages match " + sourceDescription() + " (" + revision + ").");
        }
        return report;
    }

    private PreparedLanguage mutateMessage(PluginLanguageEditor.Edit edit) throws IOException {
        String requiredLocale = canonicalLocale(edit.locale());
        String key = edit.key();
        MessageKey definition = CATALOG.require(key);
        if (!(definition instanceof TextKey) || !(edit.value() instanceof TextValue)) {
            throw new IOException("Language editor does not support key shape: " + definition.id());
        }
        String value = ((TextValue) edit.value()).template();
        try {
            validateTemplate("language:" + key, value, sampleArguments(definition.placeholders()));
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                    List.of(LocaleOverlay.builder("editor", requiredLocale).text(key, value).build()), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Language file contains invalid message markup: " + key, exception);
        }
        PreparedLanguage current = prepare(requiredLocale);
        if (!current.selectionReady()) {
            throw new IOException("Language file is not installed: " + requiredLocale);
        }
        File file = languageFile(requiredLocale);
        EditedLanguage edited = LanguageFileEditor.update(file.toPath(), source -> {
            Map<String, String> originalValues = TomlLanguageParser.parseValidText(source, CATALOG);
            ArrayList<LocaleOverlay> originalOverlays = new ArrayList<>();
            originalOverlays.add(createOverlay(requiredLocale, file.getPath(), originalValues));
            originalOverlays.addAll(baseOverlays(requiredLocale, source));
            PreparedLanguage latest = createPrepared(requiredLocale, file, originalOverlays, true);
            if (!selectionSnapshot(requiredLocale, latest.snapshot()).value(definition).equals(edit.expected())) {
                throw new IOException("Language message changed while the editor was open; reopen it and try again");
            }
            String content = TomlLanguageEditor.upsertText(source, definition.id(), value).content();
            Map<String, String> values = TomlLanguageParser.parseValidText(content, CATALOG);
            ArrayList<LocaleOverlay> overlays = new ArrayList<>();
            overlays.add(createOverlay(requiredLocale, file.getPath(), values));
            overlays.addAll(baseOverlays(requiredLocale, content));
            PreparedLanguage prepared = createPrepared(requiredLocale, file, overlays, true);
            return new LanguageFileEditor.Prepared<>(content, new EditedLanguage(prepared, content));
        });
        PreparedLanguage prepared = edited.prepared();
        refreshAvailableLocales();
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(file, edited.content());
        }
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(requiredLocale, prepared.snapshot());
            if (sameLocale(requiredLocale, activeSelections.defaultLocale())) {
                manager.install(prepared.snapshot());
                activeFile.set(prepared.file());
            }
        }
        return prepared;
    }

    private synchronized LocalizationSnapshot loadSelectionSnapshot(String locale) throws Exception {
        String requiredLocale = canonicalLocale(locale);
        directory.prepare();
        ensureEnglish();
        File target = languageFile(requiredLocale);
        if (!target.exists() && isRepositoryLocale(requiredLocale)) {
            if (sync == null) {
                throw new IOException("Language " + requiredLocale + " is not installed and the language source is "
                        + sourceDescription());
            }
            logger.info("Installing ShapedPortals language " + requiredLocale + " from " + sourceDescription() + "...");
            sync.install(requiredLocale);
            logger.info("Installed ShapedPortals language " + requiredLocale + " to "
                    + target.toPath().toAbsolutePath().normalize() + ".");
        }
        PreparedLanguage prepared = prepare(requiredLocale);
        if (!prepared.selectionReady()) {
            throw new IOException("Language file is not installed: " + requiredLocale);
        }
        return selectionSnapshot(requiredLocale, prepared.snapshot());
    }

    private LocalizationSnapshot selectionSnapshot(String locale, LocalizationSnapshot prepared) {
        if (sameLocale(locale, CATALOG.englishLocale())) {
            return prepared;
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>(prepared.overlays());
        LocaleOverlay.Builder englishFallback = LocaleOverlay.builder("code-owned-English:" + locale, locale);
        for (MessageKey key : CATALOG.keys()) {
            englishFallback.put(key.id(), key.englishValue());
        }
        overlays.add(englishFallback.build());
        return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, overlays, ENGLISH_PLURALS));
    }

    private synchronized LocalizationSnapshot saveEditorMessage(PluginLanguageEditor.Edit edit) throws Exception {
        LocalizationSnapshot current = loadSelectionSnapshot(edit.locale());
        MessageKey definition = CATALOG.require(edit.key());
        if (!current.value(definition).equals(edit.expected())) {
            throw new IOException("Language message changed while it was being edited: " + edit.key());
        }
        if (!(edit.value() instanceof TextValue textValue)) {
            throw new IllegalArgumentException("Unsupported language message shape: " + edit.key());
        }
        return mutateMessage(edit).snapshot();
    }

    private PreparedLanguage createPrepared(
            String locale,
            File file,
            List<LocaleOverlay> overlays,
            boolean selectionReady
    ) throws IOException {
        try {
            LocalizationSnapshot snapshot = LocalizationSnapshot.create(
                    new LocalizationCandidate(CATALOG, overlays, ENGLISH_PLURALS)
            );
            return new PreparedLanguage(locale, file, snapshot, selectionReady);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid language selection " + locale, exception);
        }
    }

    private LocaleOverlay createOverlay(String locale, String source, Map<String, String> values) throws IOException {
        LocaleOverlay.Builder overlay = LocaleOverlay.builder(source, locale);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            MessageKey definition = CATALOG.key(entry.getKey());
            if (definition == null) {
                continue;
            }
            if (!(definition instanceof TextKey)) {
                throw new IOException("Language key is not text: " + entry.getKey());
            }
            try {
                validateTemplate("language:" + entry.getKey(), entry.getValue(),
                        sampleArguments(definition.placeholders()));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Using English for invalid language message " + source + ":" + entry.getKey(), exception);
                continue;
            }
            overlay.text(entry.getKey(), entry.getValue());
        }
        return overlay.build();
    }

    private List<LocaleOverlay> baseOverlays(String locale) throws IOException {
        File file = languageFile(locale);
        if (!file.isFile()) {
            return List.of();
        }
        return baseOverlays(locale, LanguageDirectory.readContent(file.toPath()));
    }

    private List<LocaleOverlay> baseOverlays(String locale, String content) throws IOException {
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        visited.add(normalizedLocale(locale));
        LanguageMeta meta = metaFromContent(locale, content);
        int depth = 0;
        while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
            String base = canonicalLocale(meta.base().get());
            if (!visited.add(normalizedLocale(base)) || sameLocale(base, CATALOG.englishLocale())) {
                break;
            }
            File baseFile = languageFile(base);
            if (!baseFile.isFile()) {
                break;
            }
            String baseContent = LanguageDirectory.readContent(baseFile.toPath());
            overlays.add(createOverlay(base, baseFile.getPath(), TomlLanguageParser.parseValidText(baseContent, CATALOG)));
            meta = metaFromContent(base, baseContent);
            depth++;
        }
        return overlays;
    }

    private LanguageMeta metaFromContent(String locale, String content) {
        try {
            return LanguageMeta.parse(content).withoutSelfBase(locale);
        } catch (IOException exception) {
            return LanguageMeta.empty();
        }
    }

    private Map<String, String> loadEditableLanguage(File file) throws IOException {
        try {
            String content = LanguageDirectory.readContent(file.toPath());
            return TomlLanguageParser.parseValidText(content, CATALOG);
        } catch (IOException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UTF-8")) {
                throw new IOException("Invalid UTF-8 in " + file.getName(), exception);
            }
            throw new IOException("Invalid TOML in " + file.getName() + ": " + exception.getMessage(), exception);
        }
    }

    private void ensureEnglish() throws IOException {
        if (sync != null) {
            try {
                sync.mergeEnglish().ifPresent(result -> logger.info("Updated ShapedPortals language "
                        + CATALOG.englishLocale() + " from the built-in catalog: " + result.summary() + "."));
                return;
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException("Could not refresh the English language file", exception);
            }
        }
        File english = languageFile(CATALOG.englishLocale());
        if (english.exists()) {
            return;
        }
        AtomicFileIO.writeString(english.toPath(), LanguageReferenceRenderer.render(
                CATALOG, englishHeader(CATALOG.englishLocale())));
    }

    private void createCustomLanguageIfMissing(String locale) throws IOException {
        File target = languageFile(locale);
        if (target.exists()) {
            return;
        }
        String content = LanguageReferenceRenderer.render(CATALOG, englishHeader(locale),
                LanguageMeta.of(locale, CATALOG.englishLocale()));
        AtomicFileIO.writeString(target.toPath(), content);
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(target, content);
        }
    }

    void validateContent(String locale, String content) throws IOException {
        Map<String, String> values = parseStrictValues(content, locale);
        LocaleOverlay overlay = createOverlay(locale, "upstream:" + locale, values);
        try {
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(overlay), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Language content is invalid: " + locale, exception);
        }
    }

    private Map<String, String> parseStrictValues(String content, String locale) throws IOException {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            return TomlLanguageParser.parseValidText(content, CATALOG);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + locale + ".toml: " + exception.getMessage(), exception);
        }
    }

    private void refreshAvailableLocales() throws IOException {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(CATALOG.englishLocale());
        if (manifest != null) {
            choices.addAll(manifest.locales());
        }
        List<String> installed = directory.scan();
        choices.addAll(installed);
        LinkedHashMap<String, LanguageMeta> discoveredMetas = new LinkedHashMap<>();
        for (String locale : installed) {
            try {
                LanguageMeta meta = directory.meta(locale);
                if (!meta.isEmpty()) {
                    discoveredMetas.put(locale, meta);
                }
            } catch (IOException exception) {
                logger.log(Level.FINE, "Could not read language metadata for " + locale, exception);
            }
        }
        List<String> discovered = new ArrayList<>(choices);
        discovered.sort(String.CASE_INSENSITIVE_ORDER);
        metas = Map.copyOf(discoveredMetas);
        availableLocales = List.copyOf(discovered);
    }

    private String canonicalLocale(String locale) {
        String requiredLocale = LanguageDirectory.requireLocale(locale);
        if (CATALOG.englishLocale().equalsIgnoreCase(requiredLocale)) {
            return CATALOG.englishLocale();
        }
        if (manifest != null && manifest.contains(requiredLocale)) {
            return manifest.canonical(requiredLocale);
        }
        return requiredLocale;
    }

    private static String normalizedLocale(String locale) {
        return locale.replace('-', '_').toLowerCase(java.util.Locale.ROOT);
    }

    private static List<String> englishHeader(String locale) {
        return LanguageFileHeader.render(new LanguageFileHeader.Options(
                "ShapedPortals", locale,
                List.of("runtime.prefix supplies the styled plugin name for {prefix}. Chat templates add the gray › separator.",
                        "Remove the leading {prefix}&r &7› &7 from a message to hide its name and separator."),
                List.of("Colors and styles: &0-&f, &k-&r.", "RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].", "MiniMessage supports custom formatting. Put a backslash before & or [ to display it literally."),
                Map.ofEntries(
                        Map.entry("added", "Messages added by a language update"),
                        Map.entry("after", "Value after the change"),
                        Map.entry("argument", "Unexpected command argument"),
                        Map.entry("attempts", "Portal creation attempts"),
                        Map.entry("axis", "Portal axis"),
                        Map.entry("before", "Value before the change"),
                        Map.entry("blocks", "Portal interior cells"),
                        Map.entry("category", "Editor category"),
                        Map.entry("cells", "All managed cells"),
                        Map.entry("command", "Command path"),
                        Map.entry("count", "Number of messages"),
                        Map.entry("created", "Created count or creation time"),
                        Map.entry("creator", "Creator identity"),
                        Map.entry("enabled", "Portal creation state"),
                        Map.entry("group", "Message category name"),
                        Map.entry("hot_reload", "Hot-reload state"),
                        Map.entry("id", "Short portal ID"),
                        Map.entry("kept", "Local edits kept by a language update"),
                        Map.entry("key", "Parameter key"),
                        Map.entry("language", "Active language"),
                        Map.entry("line", "Message line number"),
                        Map.entry("locale", "Locale identifier"),
                        Map.entry("matches", "Matching portal count"),
                        Map.entry("materials", "Recorded frame materials"),
                        Map.entry("maximum", "Maximum accepted value"),
                        Map.entry("name", "Full language name"),
                        Map.entry("new", "Installed rendered value"),
                        Map.entry("old", "Previous rendered value"),
                        Map.entry("parameter", "Parameter name"),
                        Map.entry("path", "Local report path"),
                        Map.entry("permission", "Required permission"),
                        Map.entry("personal", "Personal locale when it differs from the server default"),
                        Map.entry("portal", "Portal identifier"),
                        Map.entry("portals", "Managed portal count"),
                        Map.entry("prefix", "Global runtime.prefix value; optional per message"),
                        Map.entry("reason", "Failure reason"),
                        Map.entry("rejected", "Rejected creation attempts"),
                        Map.entry("scheduler", "Scheduler implementation"),
                        Map.entry("seconds", "Confirmation window"),
                        Map.entry("section", "Language editor section"),
                        Map.entry("setting", "Setting name"),
                        Map.entry("status", "Selection marker"),
                        Map.entry("target", "Language selection target"),
                        Map.entry("type", "Parameter type or portal type"),
                        Map.entry("updated", "Messages changed by a language update"),
                        Map.entry("url", "Public report URL"),
                        Map.entry("uuid", "Full portal UUID"),
                        Map.entry("value", "Current, default, or raw value"),
                        Map.entry("variables", "Placeholders valid for the selected message"),
                        Map.entry("version", "Plugin version"),
                        Map.entry("world", "World name"),
                        Map.entry("x", "Anchor coordinates"),
                        Map.entry("y", "Anchor coordinates"),
                        Map.entry("z", "Anchor coordinates")
                )));
    }

    private void validateCatalogTemplates() {
        for (MessageKey key : CATALOG.keys()) {
            MessageValue value = key.englishValue();
            if (value instanceof TextValue text) {
                validateTemplate("catalog:" + key.id(), text.template(), sampleArguments(value.placeholders()));
            }
        }
    }

    private LocalizationSnapshot selectedSnapshot(CommandSender sender) {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            return manager.snapshot();
        }
        return sender instanceof Player player
                ? activeSelections.snapshot(player.getUniqueId())
                : activeSelections.snapshot();
    }

    private ComponentText render(LocalizationSnapshot snapshot, TextKey key, MessageArgs arguments, String prefix) {
        TextKey definition = (TextKey) CATALOG.require(key.id());
        MessageArgs resolvedArguments = argumentsWithPrefix(definition, arguments, prefix);
        String template = snapshot.resolve(definition, resolvedArguments).template();
        return renderTemplate(template, resolvedArguments);
    }

    private ComponentText renderWithoutPrefix(LocalizationSnapshot snapshot, TextKey key, MessageArgs arguments) {
        TextKey definition = (TextKey) CATALOG.require(key.id());
        MessageArgs resolvedArguments = argumentsWithPrefix(definition, arguments, renderPrefix(snapshot));
        String template = snapshot.resolve(definition, resolvedArguments).template();
        if (template.startsWith(ShapedMessages.CHAT_PREFIX)) {
            template = template.substring(ShapedMessages.CHAT_PREFIX.length());
        }
        return renderTemplate(template, resolvedArguments);
    }

    private ComponentText renderTemplate(String template, MessageArgs arguments) {
        MessageArgs.Builder replacements = MessageArgs.builder();
        TagResolver.Builder tags = TagResolver.builder();
        int index = 0;
        for (MessageArgument argument : arguments.arguments().values()) {
            String tag = "shaped_argument_" + index++;
            replacements.trusted(argument.name(), "<" + tag + ">");
            Component value;
            if (argument.kind() == MessageArgumentKind.UNTRUSTED) {
                String literal = String.valueOf(argument.value());
                if (literal.indexOf('§') >= 0) {
                    literal = ComponentText.section(literal).plain();
                }
                value = Component.text(literal);
            } else if (argument.value() instanceof ComponentText component) {
                value = MINI_MESSAGE.deserialize(component.miniMessage());
            } else {
                value = MINI_MESSAGE.deserialize(ComponentText.normalizeMarkup(String.valueOf(argument.value())));
            }
            tags.resolver(Placeholder.component(tag, value));
        }
        String rendered = interpolate(ComponentText.normalizeMarkup(template), replacements.build());
        return ComponentText.component(MINI_MESSAGE.deserialize(rendered, tags.build()));
    }

    private String renderPrefix(LocalizationSnapshot snapshot) {
        String template = snapshot.resolve(ShapedMessages.PREFIX, MessageArgs.empty()).template();
        return interpolate(template, MessageArgs.empty());
    }

    private boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private MessageArgs argumentsWithPrefix(TextKey key, MessageArgs arguments, String prefix) {
        MessageArgs resolved = arguments == null ? MessageArgs.empty() : arguments;
        if (resolved.names().contains("prefix")) {
            throw new IllegalArgumentException("The prefix message argument is managed by ShapedPortals");
        }
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : resolved.arguments().values()) {
            if (key.id().equals("language.selection.preparing") && argument.name().equals("target")
                    && String.valueOf(argument.value()).equalsIgnoreCase("ShapedPortals")) {
                builder.trusted("target", prefix);
            } else if (!argument.name().equals("plugin") || key.placeholders().contains("plugin")) {
                builder.add(argument);
            }
        }
        if (key.placeholders().contains("prefix")) {
            if (resolved.names().contains("plugin")
                    && !String.valueOf(resolved.require("plugin").value()).equalsIgnoreCase("ShapedPortals")) {
                builder.trusted("prefix", ComponentText.literal(String.valueOf(resolved.require("plugin").value())));
            } else if (key.equals(ShapedMessages.VERSION)) {
                builder.untrusted("prefix", ComponentText.markup(prefix).plain());
            } else {
                builder.trusted("prefix", prefix);
            }
        }
        return builder.build();
    }

    private void validateTemplate(String path, String template, MessageArgs arguments) {
        validatePlaceholderPlacement(path, template);
        try {
            STRICT_MINI_MESSAGE.deserialize(interpolate(template, arguments));
            String normalized = ComponentText.normalizeMarkup(template);
            MINI_MESSAGE.deserialize(interpolate(normalized, arguments));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(path + ": invalid message markup", exception);
        }
    }

    private void validatePlaceholderPlacement(String path, String template) {
        boolean insideTag = false;
        for (int index = 0; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '\\') {
                index++;
                continue;
            }
            if (current == '<') {
                insideTag = true;
                continue;
            }
            if (current == '>') {
                insideTag = false;
                continue;
            }
            if (insideTag && current == '{'
                    && (index + 1 >= template.length() || template.charAt(index + 1) != '{')) {
                throw new IllegalArgumentException(path + ": message placeholders cannot be used inside MiniMessage tags");
            }
        }
    }

    private MessageArgs sampleArguments(Set<String> placeholders) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (String placeholder : placeholders) {
            builder.untrusted(placeholder, "value");
        }
        return builder.build();
    }

    private String interpolate(String template, MessageArgs arguments) {
        StringBuilder output = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                output.append('{');
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                output.append('}');
                index += 2;
                continue;
            }
            if (current != '{') {
                output.append(current);
                index++;
                continue;
            }
            int end = template.indexOf('}', index + 1);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed message placeholder");
            }
            String name = template.substring(index + 1, end);
            MessageArgument argument = arguments.require(name);
            String replacement = String.valueOf(argument.value());
            if (argument.kind() == MessageArgumentKind.UNTRUSTED) {
                replacement = escapeUntrusted(replacement);
            }
            output.append(replacement);
            index = end + 1;
        }
        return output.toString();
    }

    private String previewValue(LocalizationSnapshot snapshot, MessageKey definition, String template) {
        MessageArgs.Builder arguments = MessageArgs.builder();
        for (String placeholder : definition.placeholders()) {
            if (placeholder.equals("prefix")) {
                MessageValue prefixValue = snapshot.value(ShapedMessages.PREFIX);
                if (!(prefixValue instanceof TextValue prefixText)) {
                    throw new IllegalStateException("Language prefix is not text");
                }
                if (definition.equals(ShapedMessages.VERSION)) {
                    arguments.untrusted(placeholder, ComponentText.markup(prefixText.template()).plain());
                } else {
                    arguments.trusted(placeholder, prefixText.template());
                }
            } else {
                arguments.untrusted(placeholder, "[" + placeholder + "]");
            }
        }
        return renderTemplate(template, arguments.build()).miniMessage();
    }

    private String escapeUntrusted(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' || current == '[') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return MINI_MESSAGE.escapeTags(escaped.toString());
    }

    public record EditableMessage(
            String id,
            String effectiveValue,
            String previewValue,
            Set<String> placeholders
    ) {
        public EditableMessage {
            placeholders = Set.copyOf(placeholders);
        }
    }

    private record EditedLanguage(PreparedLanguage prepared, String content) {
    }

    public record PreparedLanguage(
            String locale,
            File file,
            LocalizationSnapshot snapshot,
            boolean selectionReady
    ) {
    }

}
