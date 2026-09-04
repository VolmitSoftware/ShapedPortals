package com.volmit.shapedportals.gui;

import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.FeedbackTone;
import com.volmit.shapedportals.presentation.PresentationService;
import org.bukkit.Bukkit;
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
    private static final int CLOSE_SLOT = 53;
    private static final int[] CATEGORY_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final long PROMPT_TICKS = 20L * 60L;
    private static final int MAXIMUM_INPUT_LENGTH = 512;

    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final LanguageService language;
    private final PresentationService presentation;
    private final List<Setting> settings;
    private final Map<UUID, PromptSession> prompts = new ConcurrentHashMap<>();
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

    public void shutdown() {
        prompts.clear();
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
        if (holder.category() == null) {
            Category category = categoryAt(slot);
            if (category != null) {
                if (category == Category.LANGUAGES) {
                    plugin.getLanguageSwitcher().openEditor(player, this::openRootOwned);
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
    }

    private void openRootOwned(Player player) {
        prompts.remove(player.getUniqueId());
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
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, language.legacy(ShapedMessages.GUI_CLOSE), List.of()));
    }

    private void handleSettingClick(Player player, Category category, Setting setting, InventoryClickEvent event) {
        if (setting.kind() == SettingKind.LOCALE) {
            player.closeInventory();
            plugin.getLanguageSwitcher().command(player, new String[]{"server"});
            return;
        }
        if (setting.kind() == SettingKind.BOOLEAN) {
            saveMutation(player, category, setting, candidate -> {
                boolean current = Boolean.parseBoolean(setting.reader().apply(candidate));
                setting.writer().write(candidate, Boolean.toString(!current));
            });
            return;
        }
        if (setting.kind().numeric() && !isPromptClick(event.getClick())) {
            double direction = event.isRightClick() ? -1D : 1D;
            double multiplier = event.isShiftClick() ? 10D : 1D;
            double adjustment = direction * multiplier;
            saveMutation(player, category, setting, candidate -> {
                String current = setting.reader().apply(candidate);
                setting.writer().write(candidate, adjust(setting, current, adjustment));
            });
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

    private void processPrompt(Player player, PromptSession prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            presentation.command(player, ShapedMessages.GUI_PROMPT_CANCELLED, FeedbackTone.INFO);
            openCategoryOwned(player, prompt.category());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            saveFailedOwned(player, prompt.category(), prompt.setting(),
                    "the value is longer than 512 characters");
            return;
        }
        save(player, prompt.category(), prompt.setting(), input);
    }

    private void expirePrompt(Player player, PromptSession prompt) {
        if (!prompts.remove(player.getUniqueId(), prompt)) {
            return;
        }
        presentation.command(player, ShapedMessages.GUI_PROMPT_TIMEOUT, FeedbackTone.INFO);
        openCategoryOwned(player, prompt.category());
    }

    private void save(Player player, Category category, Setting setting, String input) {
        saveMutation(player, category, setting, config -> setting.writer().write(config, input));
    }

    private void saveMutation(
            Player player,
            Category category,
            Setting setting,
            Consumer<ShapedPortalsConfig> mutation
    ) {
        SaveOperation operation = new SaveOperation(player, category, setting, mutation);
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
        try {
            String previousValue = operation.setting().reader().apply(configService.editableCopy());
            plugin.applyConfigurationEdit(operation.mutation(), null);
            String appliedValue = operation.setting().reader().apply(configService.editableCopy());
            scheduleResult(operation.player(), () -> {
                MessageArgs arguments = MessageArgs.builder()
                        .untrusted("setting", plainName(operation.setting()))
                        .untrusted("old", displayValue(previousValue))
                        .untrusted("new", displayValue(appliedValue))
                        .build();
                sendConfigResult(operation.player(), ShapedMessages.CONFIG_SAVED, arguments);
                openDestination(operation);
            });
        } catch (IOException | RuntimeException exception) {
            saveFailed(operation, exception);
        }
    }

    private void saveFailed(SaveOperation operation, Exception exception) {
        plugin.getLogger().log(Level.SEVERE,
                "Failed to save ShapedPortals setting " + operation.setting().path(), exception);
        scheduleFailure(operation, reason(exception));
    }

    private void scheduleResult(Player player, Runnable result) {
        FoliaScheduler.runEntity(plugin, player, result, 0L,
                () -> prompts.remove(player.getUniqueId()));
    }

    private void scheduleFailure(SaveOperation operation, String failure) {
        scheduleResult(operation.player(), () -> saveFailedOwned(
                operation.player(),
                operation.category(),
                plainName(operation.setting()),
                failure
        ));
    }

    private void saveFailedOwned(
            Player player,
            Category category,
            Setting setting,
            String reason
    ) {
        saveFailedOwned(player, category, plainName(setting), reason);
    }

    private void saveFailedOwned(
            Player player,
            Category category,
            String settingName,
            String reason
    ) {
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("setting", settingName)
                .untrusted("reason", reason)
                .build();
        sendConfigResult(player, ShapedMessages.CONFIG_SAVE_FAILED, arguments);
        openCategoryOwned(player, category);
    }

    private void sendConfigResult(Player player, TextKey message, MessageArgs arguments) {
        language.sendPrefixed(player, message, arguments);
    }

    private void openDestination(SaveOperation operation) {
        openCategoryOwned(operation.player(), operation.category());
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

    static int inventorySize() {
        return SIZE;
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
        return Set.of(BACK_SLOT, CLOSE_SLOT);
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

    private record SaveOperation(
            Player player,
            Category category,
            Setting setting,
            Consumer<ShapedPortalsConfig> mutation
    ) {
    }

    private static final class EditorHolder implements InventoryHolder {
        private final EditorState state;
        private Inventory inventory;

        private EditorHolder(EditorState state) {
            this.state = state;
        }

        private static EditorHolder root() {
            return new EditorHolder(new EditorState(null));
        }

        private static EditorHolder category(Category category) {
            return new EditorHolder(new EditorState(category));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Category category() {
            return state.category();
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private record EditorState(Category category) {
    }
}
