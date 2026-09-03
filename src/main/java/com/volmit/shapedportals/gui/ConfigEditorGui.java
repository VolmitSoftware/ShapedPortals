package com.volmit.shapedportals.gui;

import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import com.volmit.shapedportals.presentation.FeedbackTone;
import com.volmit.shapedportals.presentation.PresentationService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class ConfigEditorGui implements Listener {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int RELOAD_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int PREVIOUS_PAGE_SLOT = 47;
    private static final int NEXT_PAGE_SLOT = 51;
    private static final int[] CATEGORY_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int LANGUAGE_PAGE_SIZE = 12;
    private static final int LANGUAGE_EDITOR_PAGE_SIZE = 45;
    private static final int LANGUAGE_PREVIEW_WIDTH = 44;
    private static final int LANGUAGE_PREVIEW_LINES = 6;
    private static final long PROMPT_TICKS = 20L * 60L;
    private static final int MAXIMUM_INPUT_LENGTH = 512;

    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final LanguageService language;
    private final PresentationService presentation;
    private final List<Setting> settings;
    private final Map<UUID, PromptSession> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, LanguageMessagePrompt> languageMessagePrompts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> languageSelections = new ConcurrentHashMap<>();
    private final AtomicLong promptIds = new AtomicLong();
    private final ExecutorService writer;

    public ConfigEditorGui(
            ShapedPortals plugin,
            ConfigService configService,
            LanguageService language,
            PresentationService presentation
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.language = language;
        this.presentation = presentation;
        settings = createSettings();
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ShapedPortals-Config-Editor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void open(Player player) {
        FoliaScheduler.runEntity(plugin, player, () -> openRootOwned(player));
    }

    public void openLanguagePicker(Player player, boolean personal, int requestedPage, boolean preserveEditorPrompt) {
        FoliaScheduler.runEntity(plugin, player, () -> {
            PromptSession prompt = !personal && preserveEditorPrompt
                    ? prompts.get(player.getUniqueId())
                    : prompts.remove(player.getUniqueId());
            showLanguagePickerOwned(player, personal, requestedPage, prompt);
        });
    }

    public void finishLanguageSelection(Player player) {
        prompts.remove(player.getUniqueId());
    }

    public void openLanguageEditor(Player player, String requestedLocale) {
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (requestedLocale == null) {
                openLanguageLocalesOwned(player, 1);
                return;
            }
            String locale = language.availableLocale(requestedLocale).orElse(null);
            if (locale == null) {
                saveFailedOwned(player, Category.LANGUAGES, requestedLocale,
                        "the language file is not available", ResultDestination.NONE);
                return;
            }
            loadLanguageMessages(player, locale, 1);
        });
    }

    public void shutdown() {
        prompts.clear();
        languageMessagePrompts.clear();
        languageSelections.clear();
        writer.shutdownNow();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= SIZE) {
            return;
        }
        if (!player.hasPermission("shapedportals.config")) {
            player.closeInventory();
            presentation.command(player, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }

        LanguageAudience.run(player.getUniqueId(), () -> handleClick(player, holder, event));
    }

    private void handleClick(Player player, EditorHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (holder.category() == Category.LANGUAGES) {
            handleLanguageEditorClick(player, holder, event);
            return;
        }
        if (slot == RELOAD_SLOT) {
            reload(player, holder.category());
            return;
        }
        if (holder.category() == null) {
            Category category = categoryAt(slot);
            if (category != null) {
                if (category == Category.LANGUAGES) {
                    openLanguageLocalesOwned(player, 1);
                } else {
                    openCategoryOwned(player, category);
                }
            }
            return;
        }
        if (slot == BACK_SLOT) {
            openRootOwned(player);
            return;
        }

        List<Setting> categorySettings = settings(holder.category());
        if (slot >= categorySettings.size()) {
            return;
        }
        Setting setting = categorySettings.get(slot);
        handleSettingClick(player, holder.category(), setting, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        LanguageMessagePrompt languagePrompt = languageMessagePrompts.remove(player.getUniqueId());
        if (languagePrompt != null) {
            event.setCancelled(true);
            String input = event.getMessage();
            FoliaScheduler.runEntity(plugin, player, () -> processLanguageMessagePrompt(player, languagePrompt, input),
                    0L, () -> languageMessagePrompts.remove(player.getUniqueId(), languagePrompt));
            return;
        }
        PromptSession prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage();
        FoliaScheduler.runEntity(plugin, player, () -> processPrompt(player, prompt, input), 0L,
                () -> prompts.remove(player.getUniqueId(), prompt));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        prompts.remove(playerId);
        languageMessagePrompts.remove(playerId);
        languageSelections.remove(playerId);
    }

    private void openRootOwned(Player player) {
        prompts.remove(player.getUniqueId());
        languageMessagePrompts.remove(player.getUniqueId());
        EditorHolder holder = EditorHolder.root();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, language.legacy(ShapedMessages.GUI_ROOT_TITLE));
        holder.setInventory(inventory);
        fill(inventory);
        Category[] categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            Category category = categories[index];
            inventory.setItem(CATEGORY_SLOTS[index], item(
                    category.material(),
                    language.legacy(category.displayName()),
                    List.of(language.legacy(ShapedMessages.GUI_CATEGORY_OPEN))
            ));
        }
        navigation(inventory, false);
        player.openInventory(inventory);
    }

    private void openCategoryOwned(Player player, Category category) {
        EditorHolder holder = EditorHolder.category(category);
        MessageArgs titleArguments = MessageArgs.builder()
                .trusted("category", language.render(category.displayName()))
                .build();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                language.legacy(ShapedMessages.GUI_CATEGORY_TITLE, titleArguments)
        );
        holder.setInventory(inventory);
        fill(inventory);
        ShapedPortalsConfig config = configService.editableCopy();
        List<Setting> categorySettings = settings(category);
        for (int slot = 0; slot < categorySettings.size(); slot++) {
            inventory.setItem(slot, settingItem(categorySettings.get(slot), config));
        }
        navigation(inventory, true);
        player.openInventory(inventory);
    }

    private void navigation(Inventory inventory, boolean back) {
        if (back) {
            inventory.setItem(BACK_SLOT, item(Material.ARROW, language.legacy(ShapedMessages.GUI_BACK), List.of()));
        }
        inventory.setItem(RELOAD_SLOT, item(Material.CLOCK, language.legacy(ShapedMessages.GUI_RELOAD), List.of()));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, language.legacy(ShapedMessages.GUI_CLOSE), List.of()));
    }

    private void openLanguageLocalesOwned(Player player, int requestedPage) {
        prompts.remove(player.getUniqueId());
        languageMessagePrompts.remove(player.getUniqueId());
        List<String> locales = language.availableLocales();
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(
                locales.size(), requestedPage, LANGUAGE_EDITOR_PAGE_SIZE);
        EditorHolder holder = EditorHolder.languageLocales(page.page(), locales);
        MessageArgs titleArguments = MessageArgs.builder()
                .trusted("category", language.render(ShapedMessages.GUI_CATEGORY_LANGUAGES))
                .build();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                language.legacy(ShapedMessages.GUI_CATEGORY_TITLE, titleArguments)
        );
        holder.setInventory(inventory);
        fill(inventory);
        String current = configService.runtime().language();
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            String locale = locales.get(index);
            boolean active = locale.equalsIgnoreCase(current);
            MessageArgs arguments = MessageArgs.builder()
                    .trusted("status", active ? "&a✔&r" : "&8•&r")
                    .untrusted("locale", locale)
                    .untrusted("name", language.localeDisplayName(locale))
                    .build();
            inventory.setItem(index - page.startIndex(), item(
                    active ? Material.WRITABLE_BOOK : Material.BOOK,
                    language.legacy(ShapedMessages.GUI_LANGUAGE_OPTION, arguments),
                    List.of(language.legacy(ShapedMessages.GUI_CATEGORY_OPEN))
            ));
        }
        navigation(inventory, true);
        pagination(inventory, page);
        player.openInventory(inventory);
    }

    private void loadLanguageMessages(Player player, String locale, int requestedPage) {
        player.closeInventory();
        try {
            writer.execute(() -> {
                try {
                    LanguageService.PreparedLanguage prepared = language.prepare(locale);
                    if (!prepared.selectionReady()) {
                        requestLanguageEditorDownload(player, locale, requestedPage);
                        return;
                    }
                    List<LanguageService.EditableMessage> messages = language.editableMessages(locale);
                    scheduleResult(player, () -> openLanguageMessagesOwned(player, locale, requestedPage, messages));
                } catch (IOException | RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to load ShapedPortals language editor for " + locale, exception);
                    scheduleResult(player, () -> languageEditorDownloadFailed(
                            player, locale, reason(exception)));
                }
            });
        } catch (RejectedExecutionException exception) {
            saveFailedOwned(player, Category.LANGUAGES, locale,
                    "the editor is shutting down", ResultDestination.NONE);
        }
    }

    private void requestLanguageEditorDownload(Player player, String locale, int page) {
        RemoteLanguageCatalog.RequestState state = language.requestRemote(
                locale,
                result -> languageEditorDownloadCompleted(player, locale, page, result)
        );
        if (state == RemoteLanguageCatalog.RequestState.SCHEDULED
                || state == RemoteLanguageCatalog.RequestState.IN_FLIGHT) {
            return;
        }
        if (state == RemoteLanguageCatalog.RequestState.CURRENT) {
            scheduleResult(player, () -> loadLanguageMessages(player, locale, page));
            return;
        }
        scheduleResult(player, () -> languageEditorDownloadFailed(
                player, locale, languageRequestFailure(state)));
    }

    private void languageEditorDownloadCompleted(
            Player player,
            String locale,
            int page,
            RemoteLanguageCatalog.DownloadResult result
    ) {
        if (result.successful()) {
            scheduleResult(player, () -> loadLanguageMessages(player, locale, page));
            return;
        }
        Throwable failure = result.failure();
        String detail = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "the language download or verification failed"
                : failure.getMessage();
        plugin.getLogger().log(Level.WARNING,
                "Unable to open the ShapedPortals language editor for " + locale, failure);
        scheduleResult(player, () -> languageEditorDownloadFailed(player, locale, detail));
    }

    private void languageEditorDownloadFailed(Player player, String locale, String reason) {
        saveFailedOwned(player, Category.LANGUAGES, locale, reason, ResultDestination.NONE);
        openLanguageLocalesOwned(player, 1);
    }

    private void openLanguageMessagesOwned(
            Player player,
            String locale,
            int requestedPage,
            List<LanguageService.EditableMessage> messages
    ) {
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(
                messages.size(), requestedPage, LANGUAGE_EDITOR_PAGE_SIZE);
        EditorHolder holder = EditorHolder.languageMessages(locale, page.page(), messages);
        MessageArgs titleArguments = MessageArgs.builder().untrusted("category", locale).build();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                language.legacy(ShapedMessages.GUI_CATEGORY_TITLE, titleArguments)
        );
        holder.setInventory(inventory);
        fill(inventory);
        for (int index = page.startIndex(); index < page.endIndex(); index++) {
            LanguageService.EditableMessage message = messages.get(index);
            inventory.setItem(index - page.startIndex(), languageMessageItem(message));
        }
        navigation(inventory, true);
        pagination(inventory, page);
        player.openInventory(inventory);
    }

    private void pagination(Inventory inventory, DirectorMiniMenu.ContentPage page) {
        if (page.hasPrevious()) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, item(
                    Material.ARROW,
                    language.legacy(DirectorHelpMessages.PREVIOUS_PAGE),
                    List.of(language.legacy(DirectorHelpMessages.PAGE) + " " + (page.page() - 1))
            ));
        }
        if (page.hasNext()) {
            inventory.setItem(NEXT_PAGE_SLOT, item(
                    Material.ARROW,
                    language.legacy(DirectorHelpMessages.NEXT_PAGE),
                    List.of(language.legacy(DirectorHelpMessages.PAGE) + " " + (page.page() + 1))
            ));
        }
    }

    private void handleLanguageEditorClick(Player player, EditorHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            if (holder.locale() == null) {
                openRootOwned(player);
            } else {
                openLanguageLocalesOwned(player, 1);
            }
            return;
        }
        if (slot == RELOAD_SLOT) {
            reloadLanguageEditor(player, holder);
            return;
        }
        if (slot == PREVIOUS_PAGE_SLOT) {
            openLanguagePage(player, holder, holder.page() - 1);
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            openLanguagePage(player, holder, holder.page() + 1);
            return;
        }
        if (slot >= LANGUAGE_EDITOR_PAGE_SIZE) {
            return;
        }
        int index = ((holder.page() - 1) * LANGUAGE_EDITOR_PAGE_SIZE) + slot;
        if (holder.locale() == null) {
            if (index < holder.locales().size()) {
                loadLanguageMessages(player, holder.locales().get(index), 1);
            }
            return;
        }
        if (index >= holder.messages().size()) {
            return;
        }
        if (!event.isLeftClick()) {
            return;
        }
        LanguageService.EditableMessage message = holder.messages().get(index);
        beginLanguageMessagePrompt(player, holder.locale(), message, holder.page());
    }

    private void openLanguagePage(Player player, EditorHolder holder, int page) {
        if (holder.locale() == null) {
            openLanguageLocalesOwned(player, page);
        } else {
            openLanguageMessagesOwned(player, holder.locale(), page, holder.messages());
        }
    }

    private void reloadLanguageEditor(Player player, EditorHolder holder) {
        try {
            writer.execute(() -> {
                long startedNanos = System.nanoTime();
                boolean success = plugin.reloadAll(true);
                long duration = (System.nanoTime() - startedNanos) / 1_000_000L;
                scheduleResult(player, () -> {
                    if (success) {
                        presentation.command(player, ShapedMessages.RELOAD_SUCCESS, MessageArgs.builder()
                                .trusted("duration", duration)
                                .untrusted("locale", configService.runtime().language())
                                .build(), FeedbackTone.SUCCESS);
                    } else {
                        presentation.command(player, ShapedMessages.RELOAD_FAILED, MessageArgs.builder()
                                .trusted("duration", duration)
                                .build(), FeedbackTone.FAILURE);
                    }
                    if (holder.locale() == null) {
                        openLanguageLocalesOwned(player, holder.page());
                    } else {
                        loadLanguageMessages(player, holder.locale(), holder.page());
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            presentation.command(player, ShapedMessages.COMMAND_FAILED, FeedbackTone.FAILURE);
        }
    }

    private void handleSettingClick(Player player, Category category, Setting setting, InventoryClickEvent event) {
        if (setting.kind() == SettingKind.LOCALE) {
            beginLanguagePicker(player, category, setting);
            return;
        }
        if (setting.kind() == SettingKind.BOOLEAN) {
            saveMutation(player, category, setting, candidate -> {
                boolean current = Boolean.parseBoolean(setting.reader().apply(candidate));
                setting.writer().write(candidate, Boolean.toString(!current));
            }, false, ResultDestination.CATEGORY, null);
            return;
        }
        if (setting.kind().numeric() && !isPromptClick(event.getClick())) {
            double direction = event.isRightClick() ? -1D : 1D;
            double multiplier = event.isShiftClick() ? 10D : 1D;
            double adjustment = direction * multiplier;
            saveMutation(player, category, setting, candidate -> {
                String current = setting.reader().apply(candidate);
                setting.writer().write(candidate, adjust(setting, current, adjustment));
            }, false, ResultDestination.CATEGORY, null);
            return;
        }
        beginPrompt(player, category, setting);
    }

    private void beginPrompt(Player player, Category category, Setting setting) {
        long id = promptIds.incrementAndGet();
        PromptSession prompt = new PromptSession(id, category, setting);
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        MessageArgs arguments = MessageArgs.builder().untrusted("setting", plainName(setting)).build();
        language.sendPrefixed(player, ShapedMessages.GUI_PROMPT, arguments);
        if (setting.kind() == SettingKind.LIST) {
            language.sendPrefixed(player, ShapedMessages.GUI_PROMPT_LIST);
        }
        language.sendPrefixed(player, ShapedMessages.GUI_PROMPT_CANCEL);
        boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                () -> expirePrompt(player, prompt), PROMPT_TICKS,
                () -> prompts.remove(player.getUniqueId(), prompt));
        if (!scheduled) {
            prompts.remove(player.getUniqueId(), prompt);
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.FAILURE);
        }
    }

    private void beginLanguagePicker(Player player, Category category, Setting setting) {
        long id = promptIds.incrementAndGet();
        PromptSession prompt = new PromptSession(id, category, setting);
        prompts.put(player.getUniqueId(), prompt);
        showLanguagePickerOwned(player, false, 1, prompt);
        boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                () -> expirePrompt(player, prompt), PROMPT_TICKS,
                () -> prompts.remove(player.getUniqueId(), prompt));
        if (!scheduled) {
            prompts.remove(player.getUniqueId(), prompt);
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.FAILURE);
        }
    }

    private void showLanguagePickerOwned(
            Player player,
            boolean personal,
            int requestedPage,
            PromptSession prompt
    ) {
        player.closeInventory();
        DirectorMiniMenu.Theme theme = ChatMenuStyle.theme();
        List<String> locales = language.availableLocales();
        DirectorMiniMenu.ContentPage page = DirectorMiniMenu.paginate(
                locales.size(), requestedPage, LANGUAGE_PAGE_SIZE);
        String scope = personal ? "self" : "server";
        String baseCommand = "/shapedportals language " + scope;
        ArrayList<String> lines = new ArrayList<>();
        lines.add(DirectorMiniMenu.banner(baseCommand, theme));
        if (languageScopeLinksVisible(page.page())) {
            if (canSelectPersonalLanguage(player)) {
                lines.add(languageScopeOption(
                        "Your language",
                        "Change only the messages you see.",
                        "/shapedportals language self"
                ));
            }
            if (canSelectServerLanguage(player)) {
                lines.add(languageScopeOption(
                        "Server default",
                        "Change the language used without a personal choice.",
                        "/shapedportals language server"
                ));
            }
        }
        if (prompt != null) {
            lines.add(ComponentText.markup(language.render(player, ShapedMessages.GUI_LANGUAGE_TYPE)).miniMessage());
        }
        if (locales.isEmpty()) {
            lines.add(ComponentText.markup(language.render(player, ShapedMessages.GUI_LANGUAGE_EMPTY)).miniMessage());
        } else {
            String current = personal
                    ? language.selections().effectiveLocale(player.getUniqueId())
                    : configService.runtime().language();
            for (String locale : locales.subList(page.startIndex(), page.endIndex())) {
                lines.add(languageOption(player, locale, locale.equalsIgnoreCase(current), scope));
            }
        }
        if (languageScopeLinksVisible(page.page()) && personal
                && language.selections().playerLocale(player.getUniqueId()).isPresent()) {
            lines.add(languageScopeOption(
                    "Use server default",
                    "Remove your personal language choice.",
                    "/shapedportals language self reset"
            ));
        }
        if (prompt != null) {
            lines.add(ComponentText.markup(language.renderWithoutPrefix(
                    player,
                    ShapedMessages.GUI_PROMPT_CANCEL,
                    MessageArgs.empty()
            )).miniMessage());
        }
        lines.add(DirectorMiniMenu.paginationBar(page, baseCommand, theme,
                language.directorResolver()));
        DirectorMiniMenu.deliver(player, lines);
    }

    private String languageOption(Player player, String locale, boolean selected, String scope) {
        ComponentText option = languageOptionText(selected, locale, language.localeDisplayName(locale));
        ComponentText hover = ComponentText.markup(language.render(player,
                ShapedMessages.GUI_LANGUAGE_HOVER,
                MessageArgs.builder().untrusted("locale", locale).build()));
        return ChatMenuStyle.entry(option)
                .clickRunCommand(languageCommand(scope, locale))
                .hover(hover)
                .miniMessage();
    }

    private String languageScopeOption(String label, String description, String command) {
        ComponentText option = ComponentText.markup("&f" + label + "&r &8:&r &7" + description + "&r");
        return ChatMenuStyle.entry(option)
                .clickRunCommand(command)
                .hover(ComponentText.markup("&7" + description + "&r"))
                .miniMessage();
    }

    private boolean canSelectPersonalLanguage(Player player) {
        return player.hasPermission("volmit.language.self")
                && player.hasPermission("shapedportals.language.self");
    }

    private boolean canSelectServerLanguage(Player player) {
        return player.hasPermission("volmit.language.admin")
                || player.hasPermission("shapedportals.config");
    }

    private void processPrompt(Player player, PromptSession prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.INFO);
            openCategoryOwned(player, prompt.category());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            saveFailedOwned(player, prompt.category(), prompt.setting(),
                    "the value is longer than 512 characters", ResultDestination.CATEGORY);
            return;
        }
        save(player, prompt.category(), prompt.setting(), input);
    }

    private void beginLanguageMessagePrompt(
            Player player,
            String locale,
            LanguageService.EditableMessage message,
            int page
    ) {
        prompts.remove(player.getUniqueId());
        long id = promptIds.incrementAndGet();
        LanguageMessagePrompt prompt = new LanguageMessagePrompt(
                id, locale, message.id(), message.previewValue(), message.placeholders(), page);
        languageMessagePrompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        showLanguageMessagePrompt(player, prompt);
        boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                () -> expireLanguageMessagePrompt(player, prompt), PROMPT_TICKS,
                () -> languageMessagePrompts.remove(player.getUniqueId(), prompt));
        if (!scheduled) {
            languageMessagePrompts.remove(player.getUniqueId(), prompt);
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.FAILURE);
        }
    }

    private void showLanguageMessagePrompt(Player player, LanguageMessagePrompt prompt) {
        ArrayList<String> entries = new ArrayList<>();
        entries.add(ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.GUI_PROMPT,
                MessageArgs.builder().untrusted("setting", prompt.key()).build()
        ))).miniMessage());
        entries.add(ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.GUI_LANGUAGE_CURRENT,
                MessageArgs.builder().trusted("value", prompt.previewValue()).build()
        ))).miniMessage());
        entries.add(ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.GUI_LANGUAGE_VARIABLES,
                MessageArgs.builder().untrusted("variables", languageVariables(prompt.placeholders())).build()
        ))).miniMessage());
        entries.add(ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.GUI_PROMPT_CANCEL,
                MessageArgs.empty()
        ))).miniMessage());
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals config language " + prompt.locale(),
                "/shapedportals config",
                entries,
                "",
                1,
                entries.size()
        );
        DirectorMiniMenu.deliverContent(player, menu, ChatMenuStyle.theme(), language.directorResolver());
    }

    private void processLanguageMessagePrompt(Player player, LanguageMessagePrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.INFO);
            loadLanguageMessages(player, prompt.locale(), prompt.page());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            saveFailedOwned(player, Category.LANGUAGES, prompt.key(),
                    "the value is longer than 512 characters", ResultDestination.NONE);
            loadLanguageMessages(player, prompt.locale(), prompt.page());
            return;
        }
        saveLanguageMessage(player, prompt, decodeLanguageInput(input));
    }

    private void expireLanguageMessagePrompt(Player player, LanguageMessagePrompt prompt) {
        if (!languageMessagePrompts.remove(player.getUniqueId(), prompt)) {
            return;
        }
        presentation.command(player, ShapedMessages.GUI_PROMPT_TIMEOUT, FeedbackTone.INFO);
        loadLanguageMessages(player, prompt.locale(), prompt.page());
    }

    private void saveLanguageMessage(Player player, LanguageMessagePrompt prompt, String value) {
        try {
            writer.execute(() -> {
                try {
                    LanguageService.PreparedLanguage prepared = language.updateMessage(
                            prompt.locale(), prompt.key(), value);
                    LanguageService.EditableMessage updatedMessage = language.editableMessages(prompt.locale()).stream()
                            .filter(message -> message.id().equals(prompt.key()))
                            .findFirst()
                            .orElseThrow(() -> new IOException(
                                    "Updated language key was not found: " + prompt.key()));
                    installEditedLanguage(prepared);
                    scheduleResult(player, () -> {
                        showLanguageSaveResult(player, prompt, updatedMessage.previewValue());
                        loadLanguageMessages(player, prompt.locale(), prompt.page());
                    });
                } catch (IOException | RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to save ShapedPortals language key " + prompt.key()
                                    + " for " + prompt.locale(), exception);
                    scheduleLanguageFailure(player, prompt.locale(), prompt.page(), prompt.key(), exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            saveFailedOwned(player, Category.LANGUAGES, prompt.key(),
                    "the editor is shutting down", ResultDestination.NONE);
        }
    }

    private void installEditedLanguage(LanguageService.PreparedLanguage prepared) {
        if (prepared.locale().equalsIgnoreCase(configService.runtime().language())) {
            plugin.installPreparedLanguage(prepared);
        }
    }

    private void scheduleLanguageFailure(
            Player player,
            String locale,
            int page,
            String key,
            Exception exception
    ) {
        scheduleResult(player, () -> {
            saveFailedOwned(player, Category.LANGUAGES, key, reason(exception), ResultDestination.NONE);
            loadLanguageMessages(player, locale, page);
        });
    }

    private void expirePrompt(Player player, PromptSession prompt) {
        if (!prompts.remove(player.getUniqueId(), prompt)) {
            return;
        }
        presentation.command(player, ShapedMessages.GUI_PROMPT_TIMEOUT, FeedbackTone.INFO);
        openCategoryOwned(player, prompt.category());
    }

    private void save(Player player, Category category, Setting setting, String input) {
        boolean languageChange = setting.path().equals("general.language");
        saveMutation(player, category, setting, config -> setting.writer().write(config, input), languageChange,
                ResultDestination.CATEGORY, languageChange ? input : null);
    }

    private void saveMutation(
            Player player,
            Category category,
            Setting setting,
            Consumer<ShapedPortalsConfig> mutation,
            boolean languageChange,
            ResultDestination destination,
            String attemptedLanguage
    ) {
        long languageSelection = languageChange ? promptIds.incrementAndGet() : 0L;
        if (languageChange) {
            languageSelections.put(player.getUniqueId(), languageSelection);
        }
        SaveOperation operation = new SaveOperation(
                player, category, setting, mutation, languageChange, destination, languageSelection,
                attemptedLanguage);
        submitSave(operation);
    }

    private void submitSave(SaveOperation operation) {
        try {
            writer.execute(() -> saveOffThread(operation));
        } catch (RejectedExecutionException exception) {
            scheduleFailure(operation, "the editor is shutting down");
        }
    }

    private void saveOffThread(SaveOperation operation) {
        if (!isCurrentSelection(operation)) {
            return;
        }
        try {
            LanguageService.PreparedLanguage preparedLanguage = null;
            if (operation.languageChange()) {
                ShapedPortalsConfig candidate = configService.editableCopy();
                operation.mutation().accept(candidate);
                preparedLanguage = plugin.getLanguageService().prepare(candidate.general.language);
                if (!preparedLanguage.selectionReady()) {
                    requestLanguageDownload(operation, preparedLanguage.locale());
                    return;
                }
            }
            applyPreparedSave(operation, preparedLanguage);
        } catch (IOException | RuntimeException exception) {
            saveFailed(operation, exception);
        }
    }

    private boolean applyPreparedSave(
            SaveOperation operation,
            LanguageService.PreparedLanguage preparedLanguage
    ) throws IOException {
        if (!isCurrentSelection(operation)) {
            return false;
        }
        plugin.applyConfigurationEdit(operation.mutation(), preparedLanguage);
        clearSelection(operation);
        scheduleResult(operation.player(), () -> {
            MessageArgs arguments = MessageArgs.builder()
                    .untrusted("setting", plainName(operation.setting()))
                    .build();
            showConfigResult(operation.player(), ShapedMessages.CONFIG_SAVED, arguments);
            openDestination(operation);
        });
        return true;
    }

    private void saveFailed(SaveOperation operation, Exception exception) {
        plugin.getLogger().log(Level.SEVERE,
                "Failed to save ShapedPortals setting " + operation.setting().path(), exception);
        scheduleFailure(operation, reason(exception));
    }

    private void requestLanguageDownload(SaveOperation operation, String locale) {
        RemoteLanguageCatalog.RequestState state = language.requestRemote(
                locale,
                result -> languageDownloadCompleted(operation, result)
        );
        if (state == RemoteLanguageCatalog.RequestState.SCHEDULED
                || state == RemoteLanguageCatalog.RequestState.IN_FLIGHT) {
            return;
        }
        if (state == RemoteLanguageCatalog.RequestState.CURRENT) {
            submitLanguageActivation(operation, locale, false);
            return;
        }
        scheduleFailure(operation, languageRequestFailure(state));
    }

    private void languageDownloadCompleted(
            SaveOperation operation,
            RemoteLanguageCatalog.DownloadResult result
    ) {
        if (!isCurrentSelection(operation)) {
            return;
        }
        if (result.successful()) {
            submitLanguageActivation(operation, result.locale(), true);
            return;
        }
        Throwable failure = result.failure();
        String detail = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "unknown download failure"
                : failure.getMessage();
        String failureMessage = detail.startsWith("Unable to fetch language file ")
                ? detail
                : "Unable to fetch language file " + result.locale() + " from " + result.source() + ": " + detail;
        plugin.getLogger().warning(failureMessage + "; the configured language was not changed");
        scheduleFailure(operation, "the language download or verification failed; the previous language remains active");
    }

    private void submitLanguageActivation(SaveOperation operation, String locale, boolean downloaded) {
        try {
            writer.execute(() -> activateLanguageOffThread(operation, locale, downloaded));
        } catch (RejectedExecutionException exception) {
            scheduleFailure(operation, "the editor is shutting down");
        }
    }

    private void activateLanguageOffThread(SaveOperation operation, String locale, boolean downloaded) {
        if (!isCurrentSelection(operation)) {
            return;
        }
        try {
            LanguageService.PreparedLanguage preparedLanguage = language.prepare(locale);
            if (!preparedLanguage.selectionReady()) {
                scheduleFailure(operation, "the downloaded language file is not available");
                return;
            }
            if (applyPreparedSave(operation, preparedLanguage) && downloaded) {
                plugin.getLogger().info("Activated ShapedPortals language " + preparedLanguage.locale()
                        + " after download.");
            }
        } catch (IOException | RuntimeException exception) {
            saveFailed(operation, exception);
        }
    }

    private String languageRequestFailure(RemoteLanguageCatalog.RequestState state) {
        return switch (state) {
            case COOLDOWN -> "the language download is cooling down after a recent failure";
            case UNSUPPORTED -> "the language is not available from the configured repository";
            case CLOSED -> "the language downloader is unavailable";
            default -> "the language download could not be started";
        };
    }

    private void reload(Player player, Category category) {
        try {
            writer.execute(() -> {
                long startedNanos = System.nanoTime();
                boolean success = plugin.reloadAll(true);
                long duration = (System.nanoTime() - startedNanos) / 1_000_000L;
                scheduleResult(player, () -> {
                    if (success) {
                        presentation.command(player, ShapedMessages.RELOAD_SUCCESS, MessageArgs.builder()
                                .trusted("duration", duration)
                                .untrusted("locale", configService.runtime().language())
                                .build(), FeedbackTone.SUCCESS);
                    } else {
                        presentation.command(player, ShapedMessages.RELOAD_FAILED, MessageArgs.builder()
                                .trusted("duration", duration)
                                .build(), FeedbackTone.FAILURE);
                    }
                    if (category == null) {
                        openRootOwned(player);
                    } else {
                        openCategoryOwned(player, category);
                    }
                });
            });
        } catch (RejectedExecutionException exception) {
            presentation.command(player, ShapedMessages.COMMAND_FAILED, FeedbackTone.FAILURE);
        }
    }

    private void scheduleResult(Player player, Runnable result) {
        FoliaScheduler.runEntity(plugin, player, result, 0L,
                () -> prompts.remove(player.getUniqueId()));
    }

    private void scheduleFailure(SaveOperation operation, String failure) {
        if (!isCurrentSelection(operation)) {
            return;
        }
        clearSelection(operation);
        String settingName = failureSettingName(
                operation.languageChange(), operation.attemptedLanguage(), plainName(operation.setting()));
        scheduleResult(operation.player(), () -> saveFailedOwned(
                operation.player(),
                operation.category(),
                settingName,
                failure,
                operation.destination()
        ));
    }

    private void saveFailedOwned(
            Player player,
            Category category,
            Setting setting,
            String reason,
            ResultDestination destination
    ) {
        saveFailedOwned(player, category, plainName(setting), reason, destination);
    }

    private void saveFailedOwned(
            Player player,
            Category category,
            String settingName,
            String reason,
            ResultDestination destination
    ) {
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("setting", settingName)
                .untrusted("reason", reason)
                .build();
        showConfigResult(player, ShapedMessages.CONFIG_SAVE_FAILED, arguments);
        if (destination == ResultDestination.CATEGORY) {
            openCategoryOwned(player, category);
        }
    }

    private void showConfigResult(Player player, TextKey message, MessageArgs arguments) {
        String entry = ChatMenuStyle.entry(ComponentText.markup(
                language.renderWithoutPrefix(message, arguments))).miniMessage();
        DirectorMiniMenu.ContentMenu menu = configResultMenu(entry);
        DirectorMiniMenu.deliverContent(player, menu, ChatMenuStyle.theme(), language.directorResolver());
    }

    private void showLanguageSaveResult(
            Player player,
            LanguageMessagePrompt prompt,
            String updatedPreview
    ) {
        String saved = ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.CONFIG_SAVED,
                MessageArgs.builder().untrusted("setting", prompt.key()).build()
        ))).miniMessage();
        String changed = ChatMenuStyle.entry(ComponentText.markup(language.renderWithoutPrefix(
                ShapedMessages.GUI_LANGUAGE_CHANGED,
                MessageArgs.builder()
                        .trusted("old", prompt.previewValue())
                        .trusted("new", updatedPreview)
                        .build()
        ))).miniMessage();
        DirectorMiniMenu.ContentMenu menu = new DirectorMiniMenu.ContentMenu(
                "/shapedportals config",
                "/shapedportals config",
                List.of(saved, changed),
                "",
                1,
                2
        );
        DirectorMiniMenu.deliverContent(player, menu, ChatMenuStyle.theme(), language.directorResolver());
    }

    static DirectorMiniMenu.ContentMenu configResultMenu(String entry) {
        return new DirectorMiniMenu.ContentMenu(
                "/shapedportals config",
                "/shapedportals config",
                List.of(entry),
                "",
                1,
                1
        );
    }

    static String failureSettingName(boolean languageChange, String attemptedLanguage, String settingName) {
        if (languageChange && attemptedLanguage != null && !attemptedLanguage.isBlank()) {
            return attemptedLanguage.trim();
        }
        return settingName;
    }

    static String languageVariables(Set<String> placeholders) {
        if (placeholders.isEmpty()) {
            return "—";
        }
        return placeholders.stream()
                .sorted()
                .map(name -> "{" + name + "}")
                .collect(Collectors.joining(" "));
    }

    private boolean isCurrentSelection(SaveOperation operation) {
        if (!operation.languageChange()) {
            return true;
        }
        Long current = languageSelections.get(operation.player().getUniqueId());
        return current != null && current == operation.languageSelection();
    }

    private void clearSelection(SaveOperation operation) {
        if (operation.languageChange()) {
            languageSelections.remove(operation.player().getUniqueId(), operation.languageSelection());
        }
    }

    private void openDestination(SaveOperation operation) {
        if (operation.destination() == ResultDestination.CATEGORY) {
            openCategoryOwned(operation.player(), operation.category());
        }
    }

    private ItemStack settingItem(Setting setting, ShapedPortalsConfig config) {
        String value = displayValue(setting.reader().apply(config));
        MessageArgs valueArguments;
        if (setting.kind() == SettingKind.BOOLEAN) {
            boolean enabled = Boolean.parseBoolean(value);
            valueArguments = MessageArgs.builder().trusted("value",
                    enabled ? "&aenabled&r" : "&cdisabled&r").build();
        } else {
            valueArguments = MessageArgs.builder().untrusted("value", value).build();
        }
        TextKey instruction = switch (setting.kind()) {
            case BOOLEAN -> ShapedMessages.GUI_TOGGLE;
            case INTEGER, LONG, FLOAT -> ShapedMessages.GUI_NUMBER;
            case TEXT, LIST -> ShapedMessages.GUI_TEXT;
            case LOCALE -> ShapedMessages.GUI_LANGUAGE_SELECT;
        };
        return item(setting.material(), language.legacy(setting.name()), List.of(
                language.legacy(ShapedMessages.GUI_STATE, valueArguments),
                language.legacy(instruction)
        ));
    }

    private ItemStack languageMessageItem(LanguageService.EditableMessage message) {
        ArrayList<String> lore = new ArrayList<>();
        String currentLabel = language.legacy(
                ShapedMessages.GUI_LANGUAGE_CURRENT,
                MessageArgs.builder().trusted("value", "").build()
        ).stripTrailing();
        lore.add(currentLabel);
        lore.addAll(wrapLegacyPreview(
                ComponentText.markup(message.previewValue()).legacy(),
                LANGUAGE_PREVIEW_WIDTH,
                LANGUAGE_PREVIEW_LINES
        ));
        if (!message.placeholders().isEmpty()) {
            String placeholders = message.placeholders().stream()
                    .sorted()
                    .map(name -> "{" + name + "}")
                    .collect(Collectors.joining(" "));
            lore.add("");
            lore.addAll(wrapLegacyPreview("§8" + placeholders, LANGUAGE_PREVIEW_WIDTH, 2));
        }
        lore.add(language.legacy(ShapedMessages.GUI_TEXT));
        return item(Material.PAPER, "§f" + message.id(), lore);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private List<Setting> settings(Category category) {
        return settings.stream().filter(setting -> setting.category() == category).toList();
    }

    private static Category categoryAt(int slot) {
        Category[] categories = Category.values();
        for (int index = 0; index < CATEGORY_SLOTS.length; index++) {
            if (CATEGORY_SLOTS[index] == slot) {
                return categories[index];
            }
        }
        return null;
    }

    private boolean isPromptClick(ClickType click) {
        return click == ClickType.DROP || click == ClickType.CONTROL_DROP;
    }

    private String adjust(Setting setting, String current, double multiplier) {
        double adjusted = Double.parseDouble(current) + setting.step() * multiplier;
        return switch (setting.kind()) {
            case INTEGER -> Integer.toString((int) Math.round(adjusted));
            case LONG -> Long.toString(Math.round(adjusted));
            case FLOAT -> Float.toString((float) (Math.round(adjusted * 1000D) / 1000D));
            default -> current;
        };
    }

    private String plainName(Setting setting) {
        return ComponentText.markup(language.render(setting.name())).plain();
    }

    private String displayValue(String value) {
        if (value.isBlank()) {
            return "(empty)";
        }
        return value.length() <= 120 ? value : value.substring(0, 117) + "…";
    }

    static String decodeLanguageInput(String input) {
        StringBuilder decoded = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current != '\\' || index + 1 >= input.length()) {
                decoded.append(current);
                continue;
            }
            char next = input.charAt(index + 1);
            if (next == 'n') {
                decoded.append('\n');
                index++;
                continue;
            }
            if (next == '\\') {
                decoded.append('\\');
                index++;
                continue;
            }
            decoded.append(current);
        }
        return decoded.toString();
    }

    static List<String> wrapLegacyPreview(String value, int width, int maximumLines) {
        int safeWidth = Math.max(1, width);
        int safeMaximumLines = Math.max(1, maximumLines);
        String normalized = value == null || value.isEmpty() ? "(empty)" : value;
        ArrayList<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int visibleCharacters = 0;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '§' && index + 1 < normalized.length()) {
                current.append(character).append(normalized.charAt(++index));
                continue;
            }
            if (character == '\r') {
                continue;
            }
            if (character == '\n') {
                lines.add(current.toString());
                current = new StringBuilder(ChatColor.getLastColors(current.toString()));
                visibleCharacters = 0;
                continue;
            }
            if (visibleCharacters >= safeWidth
                    || (Character.isWhitespace(character) && visibleCharacters == safeWidth - 1)) {
                String completed = current.toString().stripTrailing();
                lines.add(completed);
                current = new StringBuilder(ChatColor.getLastColors(completed));
                visibleCharacters = 0;
                if (Character.isWhitespace(character)) {
                    continue;
                }
            }
            current.append(character);
            visibleCharacters++;
        }
        if (visibleCharacters > 0 || lines.isEmpty()) {
            lines.add(current.toString().stripTrailing());
        }
        if (lines.size() <= safeMaximumLines) {
            return List.copyOf(lines);
        }
        ArrayList<String> truncated = new ArrayList<>(lines.subList(0, safeMaximumLines));
        int lastIndex = truncated.size() - 1;
        truncated.set(lastIndex, truncated.get(lastIndex).stripTrailing() + "§8…");
        return List.copyOf(truncated);
    }

    private String reason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "validation or disk write failed; see the console";
        }
        return message;
    }

    static Set<String> editablePaths() {
        return createSettings().stream().map(Setting::path).collect(Collectors.toUnmodifiableSet());
    }

    static boolean languageUsesPicker() {
        return createSettings().stream()
                .anyMatch(setting -> setting.path().equals("general.language")
                        && setting.kind() == SettingKind.LOCALE);
    }

    static String languageStatus(boolean selected) {
        return selected ? "&a✔&r" : "&8•&r";
    }

    static boolean languageScopeLinksVisible(int page) {
        return page == 1;
    }

    static ComponentText languageOptionText(boolean selected, String locale, String name) {
        return ComponentText.markup(languageStatus(selected) + " &f")
                .append(ComponentText.literal(locale))
                .append(ComponentText.markup("&r &7"))
                .append(ComponentText.literal(name))
                .append(ComponentText.markup("&r"));
    }

    static String languageCommand(String scope, String locale) {
        return "/shapedportals language " + scope + " " + locale;
    }

    static int inventorySize() {
        return SIZE;
    }

    static int languageEditorPageSize() {
        return LANGUAGE_EDITOR_PAGE_SIZE;
    }

    static Map<Integer, String> rootCategorySlots() {
        Category[] categories = Category.values();
        LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        for (int index = 0; index < CATEGORY_SLOTS.length; index++) {
            slots.put(CATEGORY_SLOTS[index], categories[index].name());
        }
        return Map.copyOf(slots);
    }

    static Set<Integer> navigationSlots() {
        return Set.of(BACK_SLOT, RELOAD_SLOT, CLOSE_SLOT);
    }

    static int maximumCategorySize() {
        int maximum = 0;
        for (Category category : Category.values()) {
            int size = (int) createSettings().stream().filter(setting -> setting.category() == category).count();
            maximum = Math.max(maximum, size);
        }
        return maximum;
    }

    private static List<Setting> createSettings() {
        return List.of(
                setting(Category.GENERAL, "general.enabled", ShapedMessages.SETTING_GENERAL_ENABLED,
                        Material.FLINT_AND_STEEL, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.general.enabled),
                        (config, value) -> config.general.enabled = parseBoolean(value)),
                setting(Category.GENERAL, "general.language", ShapedMessages.SETTING_GENERAL_LANGUAGE,
                        Material.WRITABLE_BOOK, SettingKind.LOCALE, 1D,
                        config -> config.general.language,
                        (config, value) -> config.general.language = value.trim()),
                setting(Category.GENERAL, "general.requireCreatePermission", ShapedMessages.SETTING_GENERAL_REQUIRE_PERMISSION,
                        Material.TRIPWIRE_HOOK, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.general.requireCreatePermission),
                        (config, value) -> config.general.requireCreatePermission = parseBoolean(value)),
                setting(Category.GENERAL, "general.failureFeedback", ShapedMessages.SETTING_GENERAL_FAILURE_FEEDBACK,
                        Material.PAPER, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.general.failureFeedback),
                        (config, value) -> config.general.failureFeedback = parseBoolean(value)),
                setting(Category.GENERAL, "metrics.enabled", ShapedMessages.SETTING_METRICS_ENABLED,
                        Material.FILLED_MAP, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.metrics.enabled),
                        (config, value) -> config.metrics.enabled = parseBoolean(value)),

                setting(Category.PORTAL, "portal.minimumInteriorBlocks", ShapedMessages.SETTING_PORTAL_MINIMUM,
                        Material.FIRE_CHARGE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.minimumInteriorBlocks),
                        (config, value) -> config.portal.minimumInteriorBlocks = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.maximumInteriorBlocks", ShapedMessages.SETTING_PORTAL_MAXIMUM,
                        Material.OBSIDIAN, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.maximumInteriorBlocks),
                        (config, value) -> config.portal.maximumInteriorBlocks = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.maximumWidth", ShapedMessages.SETTING_PORTAL_WIDTH,
                        Material.OAK_FENCE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.maximumWidth),
                        (config, value) -> config.portal.maximumWidth = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.maximumHeight", ShapedMessages.SETTING_PORTAL_HEIGHT,
                        Material.LADDER, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.maximumHeight),
                        (config, value) -> config.portal.maximumHeight = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.frameMaterials", ShapedMessages.SETTING_PORTAL_FRAMES,
                        Material.CRYING_OBSIDIAN, SettingKind.LIST, 1D,
                        config -> join(config.portal.frameMaterials),
                        (config, value) -> config.portal.frameMaterials = parseList(value)),
                setting(Category.PORTAL, "portal.interiorMaterials", ShapedMessages.SETTING_PORTAL_INTERIORS,
                        Material.GLASS, SettingKind.LIST, 1D,
                        config -> join(config.portal.interiorMaterials),
                        (config, value) -> config.portal.interiorMaterials = parseList(value)),
                setting(Category.PORTAL, "portal.ignitionCauses", ShapedMessages.SETTING_PORTAL_CAUSES,
                        Material.FIREWORK_ROCKET, SettingKind.LIST, 1D,
                        config -> join(config.portal.ignitionCauses),
                        (config, value) -> config.portal.ignitionCauses = parseList(value)),
                setting(Category.PORTAL, "portal.allowedWorlds", ShapedMessages.SETTING_PORTAL_ALLOWED_WORLDS,
                        Material.GRASS_BLOCK, SettingKind.LIST, 1D,
                        config -> join(config.portal.allowedWorlds),
                        (config, value) -> config.portal.allowedWorlds = parseList(value)),
                setting(Category.PORTAL, "portal.deniedWorlds", ShapedMessages.SETTING_PORTAL_DENIED_WORLDS,
                        Material.BARRIER, SettingKind.LIST, 1D,
                        config -> join(config.portal.deniedWorlds),
                        (config, value) -> config.portal.deniedWorlds = parseList(value)),
                setting(Category.PORTAL, "portal.deduplicationMillis", ShapedMessages.SETTING_PORTAL_DEDUPLICATION,
                        Material.REPEATER, SettingKind.LONG, 100D,
                        config -> Long.toString(config.portal.deduplicationMillis),
                        (config, value) -> config.portal.deduplicationMillis = Long.parseLong(value.trim())),
                setting(Category.PORTAL, "portal.endPortalCreation", ShapedMessages.SETTING_PORTAL_END_ENABLED,
                        Material.END_PORTAL_FRAME, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.portal.endPortalCreation),
                        (config, value) -> config.portal.endPortalCreation = parseBoolean(value)),
                setting(Category.PORTAL, "portal.endMinimumInteriorBlocks", ShapedMessages.SETTING_PORTAL_END_MINIMUM,
                        Material.ENDER_EYE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.endMinimumInteriorBlocks),
                        (config, value) -> config.portal.endMinimumInteriorBlocks = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.endMaximumInteriorBlocks", ShapedMessages.SETTING_PORTAL_END_MAXIMUM,
                        Material.END_STONE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.endMaximumInteriorBlocks),
                        (config, value) -> config.portal.endMaximumInteriorBlocks = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.endMaximumWidth", ShapedMessages.SETTING_PORTAL_END_WIDTH,
                        Material.END_ROD, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.endMaximumWidth),
                        (config, value) -> config.portal.endMaximumWidth = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.endMaximumLength", ShapedMessages.SETTING_PORTAL_END_LENGTH,
                        Material.PURPUR_PILLAR, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.portal.endMaximumLength),
                        (config, value) -> config.portal.endMaximumLength = Integer.parseInt(value.trim())),
                setting(Category.PORTAL, "portal.endInteriorMaterials", ShapedMessages.SETTING_PORTAL_END_INTERIORS,
                        Material.END_STONE_BRICKS, SettingKind.LIST, 1D,
                        config -> join(config.portal.endInteriorMaterials),
                        (config, value) -> config.portal.endInteriorMaterials = parseList(value)),

                setting(Category.EFFECTS, "effects.creationSound", ShapedMessages.SETTING_EFFECTS_SOUND,
                        Material.NOTE_BLOCK, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.effects.creationSound),
                        (config, value) -> config.effects.creationSound = parseBoolean(value)),
                setting(Category.EFFECTS, "effects.creationSoundType", ShapedMessages.SETTING_EFFECTS_SOUND_TYPE,
                        Material.JUKEBOX, SettingKind.TEXT, 1D,
                        config -> config.effects.creationSoundType,
                        (config, value) -> config.effects.creationSoundType = value.trim()),
                setting(Category.EFFECTS, "effects.creationSoundVolume", ShapedMessages.SETTING_EFFECTS_VOLUME,
                        Material.GOAT_HORN, SettingKind.FLOAT, 0.1D,
                        config -> Float.toString(config.effects.creationSoundVolume),
                        (config, value) -> config.effects.creationSoundVolume = Float.parseFloat(value.trim())),
                setting(Category.EFFECTS, "effects.creationSoundPitch", ShapedMessages.SETTING_EFFECTS_PITCH,
                        Material.AMETHYST_SHARD, SettingKind.FLOAT, 0.1D,
                        config -> Float.toString(config.effects.creationSoundPitch),
                        (config, value) -> config.effects.creationSoundPitch = Float.parseFloat(value.trim())),
                setting(Category.EFFECTS, "effects.endCreationSound", ShapedMessages.SETTING_EFFECTS_END_SOUND,
                        Material.END_PORTAL_FRAME, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.effects.endCreationSound),
                        (config, value) -> config.effects.endCreationSound = parseBoolean(value)),
                setting(Category.EFFECTS, "effects.endCreationSoundType", ShapedMessages.SETTING_EFFECTS_END_SOUND_TYPE,
                        Material.ENDER_EYE, SettingKind.TEXT, 1D,
                        config -> config.effects.endCreationSoundType,
                        (config, value) -> config.effects.endCreationSoundType = value.trim()),
                setting(Category.EFFECTS, "effects.endCreationSoundVolume", ShapedMessages.SETTING_EFFECTS_END_VOLUME,
                        Material.DRAGON_HEAD, SettingKind.FLOAT, 0.1D,
                        config -> Float.toString(config.effects.endCreationSoundVolume),
                        (config, value) -> config.effects.endCreationSoundVolume = Float.parseFloat(value.trim())),
                setting(Category.EFFECTS, "effects.endCreationSoundPitch", ShapedMessages.SETTING_EFFECTS_END_PITCH,
                        Material.DRAGON_BREATH, SettingKind.FLOAT, 0.1D,
                        config -> Float.toString(config.effects.endCreationSoundPitch),
                        (config, value) -> config.effects.endCreationSoundPitch = Float.parseFloat(value.trim())),

                setting(Category.HOT_RELOAD, "hotReload.enabled", ShapedMessages.SETTING_HOT_RELOAD_ENABLED,
                        Material.COMPARATOR, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.hotReload.enabled),
                        (config, value) -> config.hotReload.enabled = parseBoolean(value)),
                setting(Category.HOT_RELOAD, "hotReload.pollIntervalMillis", ShapedMessages.SETTING_HOT_RELOAD_POLL,
                        Material.CLOCK, SettingKind.LONG, 100D,
                        config -> Long.toString(config.hotReload.pollIntervalMillis),
                        (config, value) -> config.hotReload.pollIntervalMillis = Long.parseLong(value.trim())),
                setting(Category.HOT_RELOAD, "hotReload.cooldownMillis", ShapedMessages.SETTING_HOT_RELOAD_COOLDOWN,
                        Material.REDSTONE_TORCH, SettingKind.LONG, 100D,
                        config -> Long.toString(config.hotReload.cooldownMillis),
                        (config, value) -> config.hotReload.cooldownMillis = Long.parseLong(value.trim())),
                setting(Category.HOT_RELOAD, "hotReload.notifyOperators", ShapedMessages.SETTING_HOT_RELOAD_NOTIFY,
                        Material.BELL, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.hotReload.notifyOperators),
                        (config, value) -> config.hotReload.notifyOperators = parseBoolean(value)),

                setting(Category.INTEGRITY, "integrity.enabled", ShapedMessages.SETTING_INTEGRITY_ENABLED,
                        Material.RESPAWN_ANCHOR, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.integrity.enabled),
                        (config, value) -> config.integrity.enabled = parseBoolean(value)),
                setting(Category.INTEGRITY, "integrity.checkIntervalTicks", ShapedMessages.SETTING_INTEGRITY_INTERVAL,
                        Material.RECOVERY_COMPASS, SettingKind.LONG, 20D,
                        config -> Long.toString(config.integrity.checkIntervalTicks),
                        (config, value) -> config.integrity.checkIntervalTicks = Long.parseLong(value.trim())),
                setting(Category.INTEGRITY, "integrity.maximumChecksPerCycle", ShapedMessages.SETTING_INTEGRITY_MAXIMUM,
                        Material.SPYGLASS, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.integrity.maximumChecksPerCycle),
                        (config, value) -> config.integrity.maximumChecksPerCycle = Integer.parseInt(value.trim())),

                setting(Category.PRESENTATION, "presentation.splashScreen", ShapedMessages.SETTING_PRESENTATION_SPLASH,
                        Material.FIREWORK_STAR, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.presentation.splashScreen),
                        (config, value) -> config.presentation.splashScreen = parseBoolean(value)),
                setting(Category.PRESENTATION, "presentation.commandSounds", ShapedMessages.SETTING_PRESENTATION_SOUNDS,
                        Material.MUSIC_DISC_CAT, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.presentation.commandSounds),
                        (config, value) -> config.presentation.commandSounds = parseBoolean(value)),
                setting(Category.PRESENTATION, "presentation.commandOverlays", ShapedMessages.SETTING_PRESENTATION_COMMAND,
                        Material.NAME_TAG, SettingKind.LIST, 1D,
                        config -> join(config.presentation.commandOverlays),
                        (config, value) -> config.presentation.commandOverlays = parseList(value)),
                setting(Category.PRESENTATION, "presentation.portalNotices", ShapedMessages.SETTING_PRESENTATION_PORTAL,
                        Material.ENDER_EYE, SettingKind.LIST, 1D,
                        config -> join(config.presentation.portalNotices),
                        (config, value) -> config.presentation.portalNotices = parseList(value)),
                setting(Category.PRESENTATION, "presentation.netherCreationNotices", ShapedMessages.SETTING_PRESENTATION_NETHER_CREATION,
                        Material.OBSIDIAN, SettingKind.LIST, 1D,
                        config -> join(config.presentation.netherCreationNotices),
                        (config, value) -> config.presentation.netherCreationNotices = parseList(value)),
                setting(Category.PRESENTATION, "presentation.endCreationNotices", ShapedMessages.SETTING_PRESENTATION_END_CREATION,
                        Material.END_PORTAL_FRAME, SettingKind.LIST, 1D,
                        config -> join(config.presentation.endCreationNotices),
                        (config, value) -> config.presentation.endCreationNotices = parseList(value)),
                setting(Category.PRESENTATION, "presentation.overlayDurationTicks", ShapedMessages.SETTING_PRESENTATION_DURATION,
                        Material.CLOCK, SettingKind.LONG, 10D,
                        config -> Long.toString(config.presentation.overlayDurationTicks),
                        (config, value) -> config.presentation.overlayDurationTicks = Long.parseLong(value.trim())),
                setting(Category.PRESENTATION, "presentation.titleFadeInTicks", ShapedMessages.SETTING_PRESENTATION_FADE_IN,
                        Material.LIGHT_GRAY_DYE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.presentation.titleFadeInTicks),
                        (config, value) -> config.presentation.titleFadeInTicks = Integer.parseInt(value.trim())),
                setting(Category.PRESENTATION, "presentation.titleStayTicks", ShapedMessages.SETTING_PRESENTATION_STAY,
                        Material.PURPLE_DYE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.presentation.titleStayTicks),
                        (config, value) -> config.presentation.titleStayTicks = Integer.parseInt(value.trim())),
                setting(Category.PRESENTATION, "presentation.titleFadeOutTicks", ShapedMessages.SETTING_PRESENTATION_FADE_OUT,
                        Material.BLACK_DYE, SettingKind.INTEGER, 1D,
                        config -> Integer.toString(config.presentation.titleFadeOutTicks),
                        (config, value) -> config.presentation.titleFadeOutTicks = Integer.parseInt(value.trim())),

                setting(Category.DEBUG, "debug.uploadEnabled", ShapedMessages.SETTING_DEBUG_UPLOAD,
                        Material.WRITABLE_BOOK, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.debug.uploadEnabled),
                        (config, value) -> config.debug.uploadEnabled = parseBoolean(value))
        );
    }

    private Setting languageSetting() {
        return settings.stream()
                .filter(candidate -> candidate.kind() == SettingKind.LOCALE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Language editor setting is missing"));
    }

    private static Setting setting(
            Category category,
            String path,
            TextKey name,
            Material material,
            SettingKind kind,
            double step,
            Function<ShapedPortalsConfig, String> reader,
            SettingWriter writer
    ) {
        return new Setting(category, path, name, material, kind, step, reader, writer);
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("true") && !normalized.equals("false")) {
            throw new IllegalArgumentException("expected true or false");
        }
        return Boolean.parseBoolean(normalized);
    }

    private static ArrayList<String> parseList(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("none") || normalized.equals("[]")) {
            return new ArrayList<>();
        }
        String[] split = normalized.split(",", -1);
        ArrayList<String> values = new ArrayList<>(split.length);
        for (String entry : split) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("list entries cannot be blank");
            }
            values.add(trimmed);
        }
        return values;
    }

    private static String join(List<String> values) {
        return String.join(", ", values);
    }

    private enum Category {
        GENERAL(ShapedMessages.GUI_CATEGORY_GENERAL, Material.COMMAND_BLOCK),
        PORTAL(ShapedMessages.GUI_CATEGORY_PORTAL, Material.OBSIDIAN),
        EFFECTS(ShapedMessages.GUI_CATEGORY_EFFECTS, Material.NOTE_BLOCK),
        HOT_RELOAD(ShapedMessages.GUI_CATEGORY_HOT_RELOAD, Material.COMPARATOR),
        INTEGRITY(ShapedMessages.GUI_CATEGORY_INTEGRITY, Material.RESPAWN_ANCHOR),
        PRESENTATION(ShapedMessages.GUI_CATEGORY_PRESENTATION, Material.PAINTING),
        DEBUG(ShapedMessages.GUI_CATEGORY_DEBUG, Material.SPYGLASS),
        LANGUAGES(ShapedMessages.GUI_CATEGORY_LANGUAGES, Material.BOOKSHELF);

        private final TextKey name;
        private final Material material;

        Category(TextKey name, Material material) {
            this.name = name;
            this.material = material;
        }

        private TextKey displayName() {
            return name;
        }

        private Material material() {
            return material;
        }
    }

    private enum SettingKind {
        BOOLEAN,
        INTEGER,
        LONG,
        FLOAT,
        TEXT,
        LIST,
        LOCALE;

        private boolean numeric() {
            return this == INTEGER || this == LONG || this == FLOAT;
        }
    }

    private enum ResultDestination {
        NONE,
        CATEGORY
    }

    @FunctionalInterface
    private interface SettingWriter {
        void write(ShapedPortalsConfig config, String value);
    }

    private record Setting(
            Category category,
            String path,
            TextKey name,
            Material material,
            SettingKind kind,
            double step,
            Function<ShapedPortalsConfig, String> reader,
            SettingWriter writer
    ) {
    }

    private record PromptSession(long id, Category category, Setting setting) {
    }

    private record LanguageMessagePrompt(
            long id,
            String locale,
            String key,
            String previewValue,
            Set<String> placeholders,
            int page
    ) {
        private LanguageMessagePrompt {
            placeholders = Set.copyOf(placeholders);
        }
    }

    private record SaveOperation(
            Player player,
            Category category,
            Setting setting,
            Consumer<ShapedPortalsConfig> mutation,
            boolean languageChange,
            ResultDestination destination,
            long languageSelection,
            String attemptedLanguage
    ) {
    }

    private static final class EditorHolder implements InventoryHolder {
        private final EditorState state;
        private Inventory inventory;

        private EditorHolder(EditorState state) {
            this.state = state;
        }

        private static EditorHolder root() {
            return new EditorHolder(new EditorState(null, null, 1, List.of(), List.of()));
        }

        private static EditorHolder category(Category category) {
            return new EditorHolder(new EditorState(category, null, 1, List.of(), List.of()));
        }

        private static EditorHolder languageLocales(int page, List<String> locales) {
            return new EditorHolder(new EditorState(
                    Category.LANGUAGES, null, page, List.copyOf(locales), List.of()));
        }

        private static EditorHolder languageMessages(
                String locale,
                int page,
                List<LanguageService.EditableMessage> messages
        ) {
            return new EditorHolder(new EditorState(
                    Category.LANGUAGES, locale, page, List.of(), List.copyOf(messages)));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Category category() {
            return state.category();
        }

        private String locale() {
            return state.locale();
        }

        private int page() {
            return state.page();
        }

        private List<String> locales() {
            return state.locales();
        }

        private List<LanguageService.EditableMessage> messages() {
            return state.messages();
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private record EditorState(
            Category category,
            String locale,
            int page,
            List<String> locales,
            List<LanguageService.EditableMessage> messages
    ) {
    }
}
