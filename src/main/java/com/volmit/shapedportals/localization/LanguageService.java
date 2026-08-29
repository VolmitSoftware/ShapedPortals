package com.volmit.shapedportals.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class LanguageService {
    private static final long MAXIMUM_LANGUAGE_BYTES = 2L * 1024L * 1024L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final PluralSelector ENGLISH_PLURALS = (locale, quantity) -> quantity.doubleValue() == 1D ? "one" : "other";
    private static final MessageCatalog CATALOG = ShapedMessages.catalog();
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");

    private final File languageDirectory;
    private final LocalizationManager manager;
    private final AtomicReference<File> activeFile;
    private final RemoteLanguageCatalog remoteCatalog;
    private final Throwable remoteCatalogFailure;
    private volatile List<String> availableLocales = List.of();
    private volatile BiConsumer<File, String> selfWriteListener;

    public LanguageService(File dataFolder) {
        languageDirectory = new File(dataFolder, "languages");
        validateCatalogTemplates();
        manager = new LocalizationManager(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS));
        activeFile = new AtomicReference<>(languageFile("en_US"));
        RemoteLanguageCatalog loadedCatalog = null;
        Throwable catalogFailure = null;
        try {
            loadedCatalog = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                    "ShapedPortals",
                    URI.create("https://raw.githubusercontent.com/VolmitSoftware/ShapedPortals/"),
                    "src/main/resources/languages",
                    ".toml",
                    "shapedportals-language-source.properties",
                    new File(dataFolder, ".language-cache").toPath(),
                    LanguageService.class.getClassLoader()
            ));
        } catch (Throwable failure) {
            catalogFailure = failure;
        }
        remoteCatalog = loadedCatalog;
        remoteCatalogFailure = catalogFailure;
    }

    public synchronized PreparedLanguage prepare(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        prepareLanguageDirectory();
        createEnglishLanguageIfMissing();
        File file = languageFile(requiredLocale);
        Path filePath = file.toPath().toAbsolutePath().normalize();
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(filePath))) {
            throw new IOException("Language path is not a regular file: " + file.getName());
        }
        if (!file.exists() && !hasRemoteCatalogLocale(requiredLocale)) {
            createCustomLanguageIfMissing(requiredLocale);
        }
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        if (file.isFile()) {
            overlays.add(createOverlay(requiredLocale, file.getPath(), loadEditableLanguage(file)));
        }
        boolean selectionReady = file.isFile();
        PreparedLanguage prepared = createPrepared(requiredLocale, file, overlays, selectionReady);
        refreshAvailableLocales();
        return prepared;
    }

    public synchronized PreparedLanguage englishFallback(String locale) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        prepareLanguageDirectory();
        createEnglishLanguageIfMissing();
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
        return mutateMessage(locale, key, value);
    }

    public void setSelfWriteListener(BiConsumer<File, String> listener) {
        selfWriteListener = listener;
    }

    public String render(TextKey key) {
        return render(key, MessageArgs.empty());
    }

    public String render(TextKey key, MessageArgs arguments) {
        return render(key, arguments, renderPrefix());
    }

    public String renderWithoutPrefix(TextKey key, MessageArgs arguments) {
        return render(key, arguments, "");
    }

    public String renderPrefixed(TextKey key, MessageArgs arguments) {
        return render(key, arguments);
    }

    public void send(CommandSender sender, TextKey key) {
        ComponentMessenger.sendMarkup(sender, render(key));
    }

    public void send(CommandSender sender, TextKey key, MessageArgs arguments) {
        ComponentMessenger.sendMarkup(sender, render(key, arguments));
    }

    public void sendPrefixed(CommandSender sender, TextKey key) {
        sendPrefixed(sender, key, MessageArgs.empty());
    }

    public void sendPrefixed(CommandSender sender, TextKey key, MessageArgs arguments) {
        ComponentMessenger.sendMarkup(sender, renderPrefixed(key, arguments));
    }

    public String legacy(TextKey key) {
        return ComponentText.markup(render(key)).legacy();
    }

    public String legacy(TextKey key, MessageArgs arguments) {
        return ComponentText.markup(render(key, arguments)).legacy();
    }

    public DirectorTextResolver directorResolver() {
        return this::renderWithoutPrefix;
    }

    public File activeFile() {
        return activeFile.get();
    }

    public File languageFile(String locale) {
        String requiredLocale = requireLocale(locale);
        return new File(languageDirectory, requiredLocale + ".toml");
    }

    public File languageDirectory() {
        return languageDirectory;
    }

    public boolean isLanguageFile(File candidate) {
        if (candidate == null || candidate.getParentFile() == null) {
            return false;
        }
        Path parent = candidate.getParentFile().toPath().toAbsolutePath().normalize();
        Path expected = languageDirectory.toPath().toAbsolutePath().normalize();
        if (!expected.equals(parent)) {
            return false;
        }
        String fileName = candidate.getName();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".toml")) {
            return false;
        }
        String locale = fileName.substring(0, fileName.length() - ".toml".length());
        return LOCALE_PATTERN.matcher(locale).matches();
    }

    public List<String> availableLocales() {
        return availableLocales;
    }

    public String localeDisplayName(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        return VolmitLocales.displayName(normalized).orElse("Custom language");
    }

    public Optional<String> availableLocale(String requested) {
        String normalized;
        try {
            normalized = requireLocale(requested);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return availableLocales.stream()
                .filter(locale -> locale.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Optional<Throwable> remoteCatalogFailure() {
        return Optional.ofNullable(remoteCatalogFailure);
    }

    public Optional<String> remoteCatalogRevision() {
        return remoteCatalog == null ? Optional.empty() : Optional.of(remoteCatalog.revision());
    }

    public boolean hasRemoteCatalogLocale(String locale) {
        String requiredLocale = canonicalLocale(locale);
        return remoteCatalog != null && remoteCatalog.availableLocales().contains(requiredLocale);
    }

    public RemoteLanguageCatalog.RequestState requestRemote(
            String locale,
            Consumer<RemoteLanguageCatalog.DownloadResult> completion
    ) {
        if (remoteCatalog == null) {
            return RemoteLanguageCatalog.RequestState.CLOSED;
        }
        String requiredLocale = canonicalLocale(locale);
        File target = languageFile(requiredLocale);
        return remoteCatalog.requestInstallIfMissing(
                requiredLocale,
                target.toPath(),
                this::validateDownloadedContent,
                result -> remoteInstallCompleted(target, result, completion)
        );
    }

    public void close() {
        selfWriteListener = null;
        if (remoteCatalog != null) {
            remoteCatalog.close();
        }
    }

    private void remoteInstallCompleted(
            File target,
            RemoteLanguageCatalog.DownloadResult result,
            Consumer<RemoteLanguageCatalog.DownloadResult> completion
    ) {
        RemoteLanguageCatalog.DownloadResult delivered = result;
        if (result.successful()) {
            try {
                String content = Files.readString(target.toPath(), StandardCharsets.UTF_8);
                BiConsumer<File, String> listener = selfWriteListener;
                if (listener != null) {
                    listener.accept(target, content);
                }
            } catch (IOException exception) {
                delivered = new RemoteLanguageCatalog.DownloadResult(
                        result.locale(), result.source(), result.file(), exception);
            }
        }
        completion.accept(delivered);
    }

    private PreparedLanguage mutateMessage(String locale, String key, String value) throws IOException {
        String requiredLocale = canonicalLocale(locale);
        MessageKey definition = CATALOG.require(key);
        if (!(definition instanceof TextKey)) {
            throw new IOException("Language editor does not support key shape: " + definition.id());
        }
        PreparedLanguage current = prepare(requiredLocale);
        if (!current.selectionReady()) {
            throw new IOException("Language file is not installed: " + requiredLocale);
        }
        File file = languageFile(requiredLocale);
        FileSource source = readFileSource(file);
        TomlLanguageEditor.EditResult edit = TomlLanguageEditor.upsertText(
                source.content(), definition.id(), value);
        String content = edit.content();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        Map<String, String> values = TomlLanguageParser.parseText(content, CATALOG.byId().keySet());
        ArrayList<LocaleOverlay> overlays = new ArrayList<>(1);
        overlays.add(createOverlay(requiredLocale, file.getPath(), values));
        PreparedLanguage prepared = createPrepared(
                requiredLocale, file, overlays, true);
        verifyUnchanged(file, source);
        AtomicFileIO.writeString(file.toPath(), content);
        refreshAvailableLocales();
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(file, content);
        }
        return prepared;
    }

    private FileSource readFileSource(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new FileSource(false, new byte[0], "");
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Language path is not a regular file: " + file.getName());
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            return new FileSource(true, bytes, decodeUtf8(bytes));
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8 in " + file.getName(), exception);
        }
    }

    private void verifyUnchanged(File file, FileSource expected) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        byte[] current = exists
                ? Files.readAllBytes(path)
                : new byte[0];
        if (exists != expected.existed() || !Arrays.equals(expected.bytes(), current)) {
            throw new IOException("Language file changed while the editor was saving; try again");
        }
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
                throw new IOException("Language file contains invalid message markup: " + entry.getKey(), exception);
            }
            overlay.text(entry.getKey(), entry.getValue());
        }
        return overlay.build();
    }

    private Map<String, String> loadEditableLanguage(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Language path is not a file: " + file);
        }
        if (file.length() > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAXIMUM_LANGUAGE_BYTES) {
                throw new IOException("Language file exceeds the 2 MiB safety limit");
            }
            String content = decodeUtf8(bytes);
            return TomlLanguageParser.parseText(content, CATALOG.byId().keySet());
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8 in " + file.getName(), exception);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + file.getName() + ": " + exception.getMessage(), exception);
        }
    }

    private String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void prepareLanguageDirectory() throws IOException {
        Path root = languageDirectory.toPath().toAbsolutePath().normalize();
        prepareRegularDirectory(root);
    }

    private void prepareRegularDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))) {
            throw new IOException("Language path is not a regular directory: " + directory);
        }
        Files.createDirectories(directory);
    }

    private void createEnglishLanguageIfMissing() throws IOException {
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
        AtomicFileIO.writeString(target.toPath(), LanguageReferenceRenderer.render(CATALOG, englishHeader(locale)));
    }

    private void validateDownloadedContent(String locale, String content) throws IOException {
        Map<String, String> values = parseStrictValues(content, locale);
        Set<String> expected = CATALOG.byId().keySet();
        if (!values.keySet().equals(expected)) {
            throw new IOException("Downloaded locale does not cover the complete ShapedPortals catalog: " + locale);
        }
        LocaleOverlay overlay = createOverlay(locale, "download:" + locale, values);
        try {
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(overlay), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Downloaded locale is invalid: " + locale, exception);
        }
    }

    private Map<String, String> parseStrictValues(String content, String locale) throws IOException {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LANGUAGE_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        try {
            return TomlLanguageParser.parseText(content);
        } catch (IOException exception) {
            throw new IOException("Invalid TOML in " + locale + ".toml: " + exception.getMessage(), exception);
        }
    }

    private void refreshAvailableLocales() throws IOException {
        Path root = languageDirectory.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            availableLocales = List.of();
            return;
        }
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(CATALOG.englishLocale());
        if (remoteCatalog != null) {
            choices.addAll(remoteCatalog.availableLocales());
        }
        try (Stream<Path> files = Files.list(root)) {
            for (Path path : files.toList()) {
                locale(path).ifPresent(choices::add);
            }
        }
        List<String> discovered = new ArrayList<>(choices);
        discovered.sort(String.CASE_INSENSITIVE_ORDER);
        availableLocales = List.copyOf(discovered);
    }

    private Optional<String> locale(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".toml")) {
            return Optional.empty();
        }
        String locale = fileName.substring(0, fileName.length() - ".toml".length());
        return LOCALE_PATTERN.matcher(locale).matches() ? Optional.of(locale) : Optional.empty();
    }

    private String requireLocale(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        if (!LOCALE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Language locale must be a safe name without a path or extension");
        }
        return normalized;
    }

    private String canonicalLocale(String locale) {
        String requiredLocale = requireLocale(locale);
        if (CATALOG.englishLocale().equalsIgnoreCase(requiredLocale)) {
            return CATALOG.englishLocale();
        }
        if (remoteCatalog != null) {
            for (String available : remoteCatalog.availableLocales()) {
                if (available.equalsIgnoreCase(requiredLocale)) {
                    return available;
                }
            }
        }
        return requiredLocale;
    }

    private static List<String> englishHeader(String locale) {
        return List.of(
                "ShapedPortals language: " + locale,
                "This is an editable language file. Manual and in-game edits are kept and hot reload automatically when enabled.",
                "The plugin creates or downloads this file only when it is missing and never automatically replaces local changes.",
                "Missing entries fall back to the built-in English catalog.",
                "Colors: &0-&f, &k-&r, &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, and [RRGGBB].",
                "Shipped defaults use classic ampersand codes; MiniMessage remains supported for custom formatting.",
                "Placeholders are message-specific. Keep only the tokens already present in each message and never rename them.",
                "Prefix: {prefix}=the global runtime.prefix value. Remove {prefix} from an individual message to hide it there.",
                "Commands: {argument}=unexpected argument; {command}=command path; {key}=parameter key; {parameter}=parameter name; {type}=parameter type; {usage}=command usage.",
                "Runtime: {attempts}=creation attempts; {created}=created count or creation time; {rejected}=rejected attempts; {enabled}=creation state; {hot_reload}=hot-reload state; {scheduler}=scheduler implementation.",
                "Portals: {portal}=portal identifier; {portals}=managed count; {id}=short ID; {uuid}=full UUID; {world}=world; {axis}=portal axis; {x}/{y}/{z}=anchor coordinates; {blocks}=portal cells; {cells}=all managed cells; {creator}=creator identity; {materials}=recorded frame materials.",
                "Lists and timing: {matches}=matching portals; {seconds}=confirmation window.",
                "Configuration: {category}=editor category; {setting}=setting name; {status}=selection marker; {value}=current, default, or raw value; {language}=active language; {locale}=locale identifier; {name}=full language name.",
                "Language editor: {variables}=tokens valid for the selected message; {old}=previous rendered value; {new}=installed rendered value.",
                "Other: {duration}=elapsed milliseconds; {path}=local report path; {url}=public report URL; {reason}=failure reason.",
                "Prefix & or [ with a backslash to display it literally."
        );
    }

    private void validateCatalogTemplates() {
        for (MessageKey key : CATALOG.keys()) {
            MessageValue value = key.englishValue();
            if (value instanceof TextValue text) {
                validateTemplate("catalog:" + key.id(), text.template(), sampleArguments(value.placeholders()));
            }
        }
    }

    private String render(TextKey key, MessageArgs arguments, String prefix) {
        MessageArgs resolvedArguments = argumentsWithPrefix(key, arguments, prefix);
        String template = manager.snapshot().resolve(key, resolvedArguments).template();
        return interpolate(template, resolvedArguments);
    }

    private String renderPrefix() {
        String template = manager.snapshot().resolve(ShapedMessages.PREFIX, MessageArgs.empty()).template();
        return interpolate(template, MessageArgs.empty());
    }

    private MessageArgs argumentsWithPrefix(TextKey key, MessageArgs arguments, String prefix) {
        MessageArgs resolved = arguments == null ? MessageArgs.empty() : arguments;
        if (!key.placeholders().contains("prefix")) {
            return resolved;
        }
        if (resolved.names().contains("prefix")) {
            throw new IllegalArgumentException("The prefix message argument is managed by ShapedPortals");
        }
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : resolved.arguments().values()) {
            builder.add(argument);
        }
        builder.trusted("prefix", prefix);
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
                arguments.trusted(placeholder, prefixText.template());
            } else {
                arguments.untrusted(placeholder, "[" + placeholder + "]");
            }
        }
        return interpolate(template, arguments.build());
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

    private record FileSource(boolean existed, byte[] bytes, String content) {
        private FileSource {
            bytes = Arrays.copyOf(bytes, bytes.length);
        }
    }

    public record PreparedLanguage(
            String locale,
            File file,
            LocalizationSnapshot snapshot,
            boolean selectionReady
    ) {
    }

}
