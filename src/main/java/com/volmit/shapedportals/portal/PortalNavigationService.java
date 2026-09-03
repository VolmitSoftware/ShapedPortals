package com.volmit.shapedportals.portal;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.geometry.PortalAxis;
import com.volmit.shapedportals.localization.LanguageService;
import com.volmit.shapedportals.localization.ShapedMessages;
import com.volmit.shapedportals.presentation.ChatMenuStyle;
import com.volmit.shapedportals.presentation.FeedbackTone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class PortalNavigationService {
    private static final DateTimeFormatter PORTAL_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final int MINIMUM_PREFIX_LENGTH = 8;
    private static final int PORTAL_CARD_LINES = 4;
    private static final int PORTALS_PER_PAGE = (DirectorMiniMenu.MENU_LINE_COUNT - 2) / PORTAL_CARD_LINES;
    private static final String UNSAFE_TELEPORT_PERMISSION = "shapedportals.teleport.unsafe";
    private static final long UNSAFE_CONFIRMATION_SECONDS = 10L;
    private static final long UNSAFE_CONFIRMATION_TICKS = UNSAFE_CONFIRMATION_SECONDS * 20L;
    private static final Comparator<PortalRecord> PORTAL_ORDER = Comparator
            .comparing(PortalRecord::worldName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(record -> record.anchor().x())
            .thenComparingInt(record -> record.anchor().y())
            .thenComparingInt(record -> record.anchor().z())
            .thenComparingLong(PortalRecord::createdAtEpochMillis)
            .thenComparing(PortalRecord::id);

    private final ShapedPortals plugin;
    private final ConfigService configService;
    private final PortalRegistry registry;
    private final PortalIntegrityService integrity;
    private final LanguageService language;
    private final UnsafeTeleportConfirmationStore unsafeConfirmations = new UnsafeTeleportConfirmationStore(
            TimeUnit.SECONDS.toNanos(UNSAFE_CONFIRMATION_SECONDS), System::nanoTime);

    public PortalNavigationService(Dependencies dependencies) {
        Dependencies required = Objects.requireNonNull(dependencies, "dependencies");
        this.plugin = required.plugin();
        this.configService = required.configService();
        this.registry = required.registry();
        this.integrity = required.integrity();
        this.language = required.language();
    }

    public void list(CommandSender sender, int requestedPage) {
        List<PortalRecord> portals = sorted(registry.allRecords());
        DirectorMiniMenu.Theme theme = ChatMenuStyle.theme();
        ArrayList<String> entries = new ArrayList<>(portals.size());
        for (PortalRecord record : portals) {
            if (!record.hasFrameMaterialSnapshot()) {
                integrity.markDirty(record.id(), 1L);
            }
            entries.add(entry(sender, record));
        }
        DirectorMiniMenu.ContentMenu menu = portalMenu(entries,
                ComponentText.markup(language.render(sender, ShapedMessages.PORTAL_LIST_EMPTY)).miniMessage(),
                requestedPage);
        DirectorMiniMenu.deliverContent(sender, menu, theme, language.directorResolver());
    }

    public void teleport(Player player, String selection) {
        SelectionResult result = resolve(registry.allRecords(), selection);
        if (result.status() == SelectionStatus.NOT_FOUND) {
            plugin.getPresentationService().command(player, ShapedMessages.PORTAL_NOT_FOUND,
                    MessageArgs.builder().untrusted("portal", selection).build(), FeedbackTone.FAILURE);
            return;
        }
        if (result.status() == SelectionStatus.AMBIGUOUS) {
            plugin.getPresentationService().command(player, ShapedMessages.PORTAL_AMBIGUOUS,
                    MessageArgs.builder().untrusted("portal", selection).trusted("matches", result.matches()).build(),
                    FeedbackTone.FAILURE);
            return;
        }

        PortalRecord record = result.record();
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            plugin.getPresentationService().command(player, ShapedMessages.PORTAL_WORLD_UNAVAILABLE,
                    MessageArgs.builder().untrusted("world", record.worldName()).build(), FeedbackTone.FAILURE);
            return;
        }
        TeleportMode mode = consumeTeleportMode(player, record.id());
        plugin.getPresentationService().command(player, ShapedMessages.PORTAL_TELEPORT_PREPARING,
                MessageArgs.builder().untrusted("portal", shortId(record.id())).build(), FeedbackTone.INFO);
        prepareChunk(player, record, world, mode);
    }

    public List<String> suggestions() {
        ArrayList<String> suggestions = new ArrayList<>();
        suggestions.add("list");
        for (PortalRecord record : sorted(registry.allRecords())) {
            suggestions.add(record.id().toString());
        }
        return List.copyOf(suggestions);
    }

    static DirectorMiniMenu.ContentMenu portalMenu(List<String> entries, String emptyLine, int requestedPage) {
        return new DirectorMiniMenu.ContentMenu(
                "/shapedportals portals",
                "/shapedportals portals",
                entries,
                emptyLine,
                requestedPage,
                PORTALS_PER_PAGE
        );
    }

    static List<PortalRecord> sorted(List<PortalRecord> records) {
        ArrayList<PortalRecord> sorted = new ArrayList<>(records);
        sorted.sort(PORTAL_ORDER);
        return List.copyOf(sorted);
    }

    static String formatCreatedAt(long epochMillis) {
        return PORTAL_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    static SelectionResult resolve(List<PortalRecord> records, String selection) {
        String normalized = selection == null ? "" : selection.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new SelectionResult(SelectionStatus.NOT_FOUND, null, 0);
        }
        try {
            UUID id = UUID.fromString(normalized);
            for (PortalRecord record : records) {
                if (record.id().equals(id)) {
                    return new SelectionResult(SelectionStatus.MATCH, record, 1);
                }
            }
            return new SelectionResult(SelectionStatus.NOT_FOUND, null, 0);
        } catch (IllegalArgumentException ignored) {
        }
        if (normalized.length() < MINIMUM_PREFIX_LENGTH) {
            return new SelectionResult(SelectionStatus.NOT_FOUND, null, 0);
        }
        PortalRecord match = null;
        int matches = 0;
        for (PortalRecord record : records) {
            if (record.id().toString().startsWith(normalized)) {
                match = record;
                matches++;
            }
        }
        if (matches == 1) {
            return new SelectionResult(SelectionStatus.MATCH, match, 1);
        }
        return new SelectionResult(matches == 0 ? SelectionStatus.NOT_FOUND : SelectionStatus.AMBIGUOUS,
                null, matches);
    }

    static List<BlockPosition> landingCandidates(PortalRecord record) {
        if (record.type() == PortalType.END) {
            return record.frame().stream()
                    .map(position -> new BlockPosition(position.x(), position.y() + 1, position.z()))
                    .sorted(Comparator
                            .comparingInt((BlockPosition position) -> distanceSquared(position, record.anchor()))
                            .thenComparingInt(BlockPosition::x)
                            .thenComparingInt(BlockPosition::z))
                    .toList();
        }
        ArrayList<BlockPosition> candidates = new ArrayList<>(record.interior().size() * 4);
        for (BlockPosition position : record.interior()) {
            if (record.axis() == PortalAxis.X) {
                addLandingPair(candidates, position.x(), position.y(), position.z() - 1);
                addLandingPair(candidates, position.x(), position.y(), position.z() + 1);
            } else {
                addLandingPair(candidates, position.x() - 1, position.y(), position.z());
                addLandingPair(candidates, position.x() + 1, position.y(), position.z());
            }
        }
        candidates.sort(Comparator
                .comparingInt((BlockPosition position) -> distanceSquared(position, record.anchor()))
                .thenComparingInt(BlockPosition::y)
                .thenComparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::z));
        return List.copyOf(new LinkedHashSet<>(candidates));
    }

    static List<LandingChunk> landingChunks(PortalRecord record) {
        Map<ChunkCoordinates, ArrayList<BlockPosition>> grouped = new LinkedHashMap<>();
        for (BlockPosition candidate : landingCandidates(record)) {
            ChunkCoordinates chunk = new ChunkCoordinates(candidate.chunkX(), candidate.chunkZ());
            grouped.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(candidate);
        }
        ArrayList<LandingChunk> chunks = new ArrayList<>(grouped.size());
        for (Map.Entry<ChunkCoordinates, ArrayList<BlockPosition>> entry : grouped.entrySet()) {
            chunks.add(new LandingChunk(entry.getKey().x(), entry.getKey().z(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(chunks);
    }

    private String entry(CommandSender sender, PortalRecord record) {
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("id", shortId(record.id()))
                .untrusted("world", record.worldName())
                .trusted("x", record.anchor().x())
                .trusted("y", record.anchor().y())
                .trusted("z", record.anchor().z())
                .untrusted("axis", record.axis().name())
                .untrusted("type", record.type().name())
                .trusted("blocks", record.interior().size())
                .untrusted("creator", record.creator())
                .untrusted("created", formatCreatedAt(record.createdAtEpochMillis()))
                .build();
        String markup = language.render(sender, ShapedMessages.PORTAL_LIST_ENTRY, arguments);
        Set<Material> allowedFrameMaterials = record.type() == PortalType.END
                ? Set.of(Material.END_PORTAL_FRAME)
                : configService.runtime().frameMaterials();
        List<Material> retiredMaterials = retiredFrameMaterials(
                record.frameMaterialSnapshot(), allowedFrameMaterials);
        if (!retiredMaterials.isEmpty()) {
            markup += "\n" + language.render(sender, ShapedMessages.PORTAL_LIST_FRAME_POLICY_NOTE,
                    MessageArgs.builder().untrusted("materials", formatMaterials(retiredMaterials)).build());
        }
        ComponentText row = ChatMenuStyle.entry(ComponentText.markup(markup));
        if (sender instanceof Player player && player.hasPermission("shapedportals.teleport")) {
            ComponentText hover = ComponentText.markup(language.render(sender, ShapedMessages.PORTAL_LIST_HOVER,
                    MessageArgs.builder().untrusted("uuid", record.id().toString()).build()));
            return row.clickRunCommand(teleportCommand(record.id())).hover(hover).miniMessage();
        }
        return row.miniMessage();
    }

    static List<Material> retiredFrameMaterials(List<Material> snapshot, Set<Material> allowed) {
        return snapshot.stream()
                .filter(material -> !allowed.contains(material))
                .distinct()
                .sorted(Comparator.comparing(Material::name))
                .toList();
    }

    static String formatMaterials(List<Material> materials) {
        return String.join(", ", materials.stream().map(Material::name).toList());
    }

    private void prepareChunk(Player player, PortalRecord requested, World world, TeleportMode mode) {
        CompletionStage<?> future = loadChunkAsync(world, requested.anchor().chunkX(), requested.anchor().chunkZ());
        if (future == null) {
            scheduleInspection(player, requested, world, mode, true);
            return;
        }
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to prepare portal destination chunk for "
                        + requested.id(), failure);
                sendFailure(player, ShapedMessages.PORTAL_DESTINATION_UNAVAILABLE);
                return;
            }
            scheduleInspection(player, requested, world, mode, false);
        });
    }

    private CompletionStage<?> loadChunkAsync(World world, int chunkX, int chunkZ) {
        try {
            Method method = world.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            Object result = method.invoke(world, chunkX, chunkZ, true);
            return result instanceof CompletionStage<?> stage ? stage : null;
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to request asynchronous portal chunk preparation", exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void scheduleInspection(
            Player player,
            PortalRecord requested,
            World world,
            TeleportMode mode,
            boolean loadSynchronously
    ) {
        boolean scheduled = FoliaScheduler.runRegion(plugin, world, requested.anchor().chunkX(), requested.anchor().chunkZ(),
                () -> inspectAndTeleport(player, requested, world, mode, loadSynchronously));
        if (!scheduled) {
            sendFailure(player, ShapedMessages.PORTAL_DESTINATION_UNAVAILABLE);
        }
    }

    private void inspectAndTeleport(
            Player player,
            PortalRecord requested,
            World world,
            TeleportMode mode,
            boolean loadSynchronously
    ) {
        PortalRecord current = registry.get(requested.id());
        if (current == null) {
            sendFailure(player, ShapedMessages.PORTAL_REMOVED);
            return;
        }
        if (loadSynchronously) {
            world.getChunkAt(current.anchor().chunkX(), current.anchor().chunkZ());
        }
        if (!isActivePortal(world, current)) {
            integrity.markDirty(current.id(), 1L);
            sendFailure(player, ShapedMessages.PORTAL_INACTIVE);
            return;
        }
        beginLandingSearch(player, current, world, mode);
    }

    private void beginLandingSearch(Player player, PortalRecord record, World world, TeleportMode mode) {
        inspectLandingChunk(player, record, world, landingChunks(record), 0, false, mode);
    }

    private void inspectLandingChunk(
            Player player,
            PortalRecord record,
            World world,
            List<LandingChunk> chunks,
            int index,
            boolean destinationFailure,
            TeleportMode mode
    ) {
        if (index >= chunks.size()) {
            completeLandingSearch(player, record, world, chunks, destinationFailure, mode);
            return;
        }
        LandingChunk chunk = chunks.get(index);
        CompletionStage<?> future = loadChunkAsync(world, chunk.x(), chunk.z());
        if (future == null) {
            scheduleLandingInspection(player, record, world, chunks, index, destinationFailure, mode, true);
            return;
        }
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to prepare landing chunk for portal " + record.id(),
                        failure);
                inspectLandingChunk(player, record, world, chunks, index + 1, true, mode);
                return;
            }
            scheduleLandingInspection(player, record, world, chunks, index, destinationFailure, mode, false);
        });
    }

    private void scheduleLandingInspection(
            Player player,
            PortalRecord record,
            World world,
            List<LandingChunk> chunks,
            int index,
            boolean destinationFailure,
            TeleportMode mode,
            boolean loadSynchronously
    ) {
        LandingChunk chunk = chunks.get(index);
        boolean scheduled = FoliaScheduler.runRegion(plugin, world, chunk.x(), chunk.z(),
                () -> inspectLandingChunkOwned(player, record, world, chunks, index,
                        destinationFailure, mode, loadSynchronously));
        if (!scheduled) {
            inspectLandingChunk(player, record, world, chunks, index + 1, true, mode);
        }
    }

    private void inspectLandingChunkOwned(
            Player player,
            PortalRecord record,
            World world,
            List<LandingChunk> chunks,
            int index,
            boolean destinationFailure,
            TeleportMode mode,
            boolean loadSynchronously
    ) {
        PortalRecord current = registry.get(record.id());
        if (current == null) {
            sendFailure(player, ShapedMessages.PORTAL_REMOVED);
            return;
        }
        LandingChunk chunk = chunks.get(index);
        Location destination;
        try {
            if (loadSynchronously) {
                world.getChunkAt(chunk.x(), chunk.z());
            }
            destination = safeDestination(world, chunk.candidates());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to inspect landing chunk for portal " + record.id(),
                    exception);
            inspectLandingChunk(player, current, world, chunks, index + 1, true, mode);
            return;
        }
        if (destination == null) {
            inspectLandingChunk(player, current, world, chunks, index + 1, destinationFailure, mode);
            return;
        }
        scheduleTeleport(player, current, destination, TeleportMode.SAFE);
    }

    private void completeLandingSearch(
            Player player,
            PortalRecord record,
            World world,
            List<LandingChunk> chunks,
            boolean destinationFailure,
            TeleportMode mode
    ) {
        if (destinationFailure) {
            sendFailure(player, ShapedMessages.PORTAL_DESTINATION_UNAVAILABLE);
            return;
        }
        if (mode == TeleportMode.SAFE) {
            offerUnsafeTeleport(player, record);
            return;
        }
        prepareUnsafeDestination(player, record, world, chunks);
    }

    private void offerUnsafeTeleport(Player player, PortalRecord record) {
        boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                () -> offerUnsafeTeleportOwned(player, record));
        if (!scheduled) {
            sendFailure(player, ShapedMessages.PORTAL_NO_SAFE_LANDING);
        }
    }

    private void offerUnsafeTeleportOwned(Player player, PortalRecord record) {
        if (!player.hasPermission(UNSAFE_TELEPORT_PERMISSION)) {
            plugin.getPresentationService().command(player, ShapedMessages.PORTAL_NO_SAFE_LANDING,
                    FeedbackTone.FAILURE);
            return;
        }
        UUID playerId = player.getUniqueId();
        UnsafeTeleportConfirmationStore.Confirmation confirmation = unsafeConfirmations.arm(playerId, record.id());
        plugin.getPresentationService().command(player, ShapedMessages.PORTAL_UNSAFE_CONFIRMATION,
                MessageArgs.builder()
                        .untrusted("portal", shortId(record.id()))
                        .trusted("seconds", UNSAFE_CONFIRMATION_SECONDS)
                        .build(), FeedbackTone.FAILURE);
        FoliaScheduler.runEntity(plugin, player,
                () -> unsafeConfirmations.expire(playerId, confirmation), UNSAFE_CONFIRMATION_TICKS,
                () -> unsafeConfirmations.expire(playerId, confirmation));
    }

    private void prepareUnsafeDestination(
            Player player,
            PortalRecord record,
            World world,
            List<LandingChunk> chunks
    ) {
        BlockPosition candidate = unsafeCandidate(chunks, world.getMinHeight(), world.getMaxHeight());
        if (candidate == null) {
            sendFailure(player, ShapedMessages.PORTAL_NO_SAFE_LANDING);
            return;
        }
        CompletionStage<?> future = loadChunkAsync(world, candidate.chunkX(), candidate.chunkZ());
        if (future == null) {
            scheduleUnsafeInspection(player, record, world, candidate, true);
            return;
        }
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to prepare unsafe landing chunk for portal "
                        + record.id(), failure);
                sendFailure(player, ShapedMessages.PORTAL_DESTINATION_UNAVAILABLE);
                return;
            }
            scheduleUnsafeInspection(player, record, world, candidate, false);
        });
    }

    private void scheduleUnsafeInspection(
            Player player,
            PortalRecord record,
            World world,
            BlockPosition candidate,
            boolean loadSynchronously
    ) {
        boolean scheduled = FoliaScheduler.runRegion(plugin, world, candidate.chunkX(), candidate.chunkZ(),
                () -> inspectUnsafeDestinationOwned(player, record, world, candidate, loadSynchronously));
        if (!scheduled) {
            sendFailure(player, ShapedMessages.PORTAL_DESTINATION_UNAVAILABLE);
        }
    }

    private void inspectUnsafeDestinationOwned(
            Player player,
            PortalRecord record,
            World world,
            BlockPosition candidate,
            boolean loadSynchronously
    ) {
        PortalRecord current = registry.get(record.id());
        if (current == null) {
            sendFailure(player, ShapedMessages.PORTAL_REMOVED);
            return;
        }
        if (loadSynchronously) {
            world.getChunkAt(candidate.chunkX(), candidate.chunkZ());
        }
        Location destination = new Location(world,
                candidate.x() + 0.5D, candidate.y(), candidate.z() + 0.5D);
        scheduleTeleport(player, current, destination, TeleportMode.UNSAFE);
    }

    private void scheduleTeleport(
            Player player,
            PortalRecord record,
            Location destination,
            TeleportMode mode
    ) {
        boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                () -> invokePermittedTeleport(player, record, destination, mode), 0L,
                () -> sendFailure(player, ShapedMessages.PORTAL_TELEPORT_FAILED));
        if (!scheduled) {
            sendFailure(player, ShapedMessages.PORTAL_TELEPORT_FAILED);
        }
    }

    private void invokePermittedTeleport(
            Player player,
            PortalRecord record,
            Location destination,
            TeleportMode mode
    ) {
        if (mode == TeleportMode.UNSAFE && !player.hasPermission(UNSAFE_TELEPORT_PERMISSION)) {
            plugin.getPresentationService().command(player, ShapedMessages.NO_PERMISSION, FeedbackTone.FAILURE);
            return;
        }
        invokeTeleport(player, destination, success -> teleportComplete(player, record, success));
    }

    private TeleportMode consumeTeleportMode(Player player, UUID portalId) {
        boolean confirmed = unsafeConfirmations.consume(player.getUniqueId(), portalId);
        if (!confirmed || !player.hasPermission(UNSAFE_TELEPORT_PERMISSION)) {
            return TeleportMode.SAFE;
        }
        return TeleportMode.UNSAFE;
    }

    static BlockPosition unsafeCandidate(List<LandingChunk> chunks, int minimumHeight, int maximumHeight) {
        for (LandingChunk chunk : chunks) {
            for (BlockPosition candidate : chunk.candidates()) {
                if (candidate.y() >= minimumHeight && candidate.y() < maximumHeight) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isActivePortal(World world, PortalRecord record) {
        Block anchor = record.anchor().block(world);
        return record.type().matches(anchor.getBlockData(), record.axis());
    }

    private Location safeDestination(World world, List<BlockPosition> candidates) {
        for (BlockPosition candidate : candidates) {
            if (candidate.y() <= world.getMinHeight() || candidate.y() + 1 >= world.getMaxHeight()) {
                continue;
            }
            Block feet = candidate.block(world);
            Block head = world.getBlockAt(candidate.x(), candidate.y() + 1, candidate.z());
            Block floor = world.getBlockAt(candidate.x(), candidate.y() - 1, candidate.z());
            if (feet.getType().isAir() && head.getType().isAir() && floor.getType().isSolid()) {
                return new Location(world, candidate.x() + 0.5D, candidate.y(), candidate.z() + 0.5D);
            }
        }
        return null;
    }

    private void invokeTeleport(Player player, Location destination, Consumer<Boolean> completion) {
        Method method = resolveAsyncTeleport(player);
        if (method == null) {
            completion.accept(player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND));
            return;
        }
        try {
            Object result = method.getParameterCount() == 2
                    ? method.invoke(player, destination, PlayerTeleportEvent.TeleportCause.COMMAND)
                    : method.invoke(player, destination);
            if (!(result instanceof CompletionStage<?> future)) {
                completion.accept(false);
                return;
            }
            future.whenComplete((value, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.WARNING, "Portal teleport failed for " + player.getName(), failure);
                }
                boolean success = failure == null && Boolean.TRUE.equals(value);
                FoliaScheduler.runEntity(plugin, player, () -> completion.accept(success), 0L,
                        () -> completion.accept(false));
            });
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to invoke asynchronous portal teleport for "
                    + player.getName(), exception);
            completion.accept(false);
        }
    }

    private Method resolveAsyncTeleport(Player player) {
        try {
            return player.getClass().getMethod("teleportAsync", Location.class,
                    PlayerTeleportEvent.TeleportCause.class);
        } catch (NoSuchMethodException | SecurityException ignored) {
        }
        try {
            return player.getClass().getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    private void teleportComplete(Player player, PortalRecord record, boolean success) {
        if (!success) {
            plugin.getPresentationService().command(player, ShapedMessages.PORTAL_TELEPORT_FAILED,
                    FeedbackTone.FAILURE);
            return;
        }
        plugin.getPresentationService().command(player, ShapedMessages.PORTAL_TELEPORT_SUCCESS,
                MessageArgs.builder()
                        .untrusted("portal", shortId(record.id()))
                        .untrusted("world", record.worldName())
                        .build(), FeedbackTone.SUCCESS);
    }

    private void sendFailure(Player player, TextKey message) {
        FoliaScheduler.runEntity(plugin, player,
                () -> plugin.getPresentationService().command(player, message, FeedbackTone.FAILURE));
    }

    private static int distanceSquared(BlockPosition first, BlockPosition second) {
        int x = first.x() - second.x();
        int y = first.y() - second.y();
        int z = first.z() - second.z();
        return x * x + y * y + z * z;
    }

    private static String shortId(UUID id) {
        return "#" + id.toString().substring(0, 8);
    }

    static String teleportCommand(UUID id) {
        return "/shapedportals teleport portal=" + id;
    }

    private static void addLandingPair(List<BlockPosition> candidates, int x, int y, int z) {
        candidates.add(new BlockPosition(x, y, z));
        candidates.add(new BlockPosition(x, y - 1, z));
    }

    enum TeleportMode {
        SAFE,
        UNSAFE
    }

    enum SelectionStatus {
        MATCH,
        NOT_FOUND,
        AMBIGUOUS
    }

    record SelectionResult(SelectionStatus status, PortalRecord record, int matches) {
    }

    record LandingChunk(int x, int z, List<BlockPosition> candidates) {
        LandingChunk {
            candidates = List.copyOf(candidates);
        }
    }

    private record ChunkCoordinates(int x, int z) {
    }

    public record Dependencies(
            ShapedPortals plugin,
            ConfigService configService,
            PortalRegistry registry,
            PortalIntegrityService integrity,
            LanguageService language
    ) {
        public Dependencies {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(configService, "configService");
            Objects.requireNonNull(registry, "registry");
            Objects.requireNonNull(integrity, "integrity");
            Objects.requireNonNull(language, "language");
        }
    }
}
