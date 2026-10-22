package com.volmit.shapedportals.portal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PortalRegistry implements AutoCloseable {
    private static final long MAXIMUM_STORE_BYTES = 128L * 1024L * 1024L;

    private final Object mutationLock = new Object();
    private final Object persistenceLock = new Object();
    private final File storeFile;
    private final Gson gson;
    private final Consumer<Throwable> failureHandler;
    private final ExecutorService persistenceExecutor;
    private final Map<UUID, PortalRecord> records = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> interiorIndex = new ConcurrentHashMap<>();
    private final Map<BlockKey, Set<UUID>> affectedIndex = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Set<UUID>> chunkIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean persistenceEnabled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PortalRegistry(File dataFolder, Consumer<Throwable> failureHandler) {
        this.storeFile = new File(dataFolder, "portals.json");
        this.gson = new GsonBuilder().create();
        this.failureHandler = failureHandler;
        this.persistenceExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ShapedPortals-Persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void load() throws IOException {
        if (!storeFile.exists()) {
            persistenceEnabled.set(true);
            return;
        }
        if (!storeFile.isFile() || storeFile.length() > MAXIMUM_STORE_BYTES) {
            throw new IOException("Portal store is missing, invalid, or exceeds the 128 MiB safety limit");
        }
        String raw = Files.readString(storeFile.toPath(), StandardCharsets.UTF_8);
        JsonObject store;
        try {
            store = JsonParser.parseString(raw).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Invalid portal store", exception);
        }
        JsonElement schemaVersion = store.get("schemaVersion");
        JsonElement portals = store.get("portals");
        if (schemaVersion == null || !schemaVersion.isJsonPrimitive()
                || !schemaVersion.getAsJsonPrimitive().isNumber()
                || schemaVersion.getAsInt() != PortalRecord.CURRENT_SCHEMA_VERSION
                || portals == null || !portals.isJsonArray()) {
            throw new IOException("Unsupported or incomplete portal store");
        }
        int unreadable = 0;
        for (JsonElement element : portals.getAsJsonArray()) {
            PortalRecord record = decode(element);
            if (record == null) {
                unreadable++;
                continue;
            }
            if (!register(record, false)) {
                throw new IOException("Portal store contains duplicate or overlapping records");
            }
        }
        if (unreadable > 0) {
            Path backup = storeFile.toPath().resolveSibling(
                    storeFile.getName() + ".unreadable-" + System.currentTimeMillis() + ".bak");
            Files.copy(storeFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            failureHandler.accept(new IOException("Skipped " + unreadable
                    + " unreadable portal record(s), for example a frame material this server no longer has. "
                    + "The original store was copied to " + backup.getFileName()));
        }
        persistenceEnabled.set(true);
    }

    private PortalRecord decode(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        try {
            return gson.fromJson(element, PortalRecord.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean register(PortalRecord record) {
        return register(record, true);
    }

    public boolean register(PortalRecord record, boolean persist) {
        synchronized (mutationLock) {
            if (closed.get()) {
                return false;
            }
            if (records.containsKey(record.id())) {
                return false;
            }
            for (BlockPosition position : record.interior()) {
                BlockKey key = new BlockKey(record.worldId(), position.x(), position.y(), position.z());
                if (interiorIndex.containsKey(key)) {
                    return false;
                }
            }

            records.put(record.id(), record);
            for (BlockPosition position : record.interior()) {
                BlockKey key = new BlockKey(record.worldId(), position.x(), position.y(), position.z());
                interiorIndex.put(key, record.id());
                addIndex(affectedIndex, key, record.id());
            }
            for (BlockPosition position : record.frame()) {
                addIndex(affectedIndex,
                        new BlockKey(record.worldId(), position.x(), position.y(), position.z()), record.id());
            }
            for (ChunkKey chunk : record.chunks()) {
                addIndex(chunkIndex, chunk, record.id());
            }
        }
        if (persist) {
            requestSave();
        }
        return true;
    }

    public PortalRecord unregister(UUID portalId) {
        PortalRecord removed;
        synchronized (mutationLock) {
            if (closed.get()) {
                return null;
            }
            removed = records.remove(portalId);
            if (removed == null) {
                return null;
            }
            for (BlockPosition position : removed.interior()) {
                BlockKey key = new BlockKey(removed.worldId(), position.x(), position.y(), position.z());
                interiorIndex.remove(key, portalId);
                removeIndex(affectedIndex, key, portalId);
            }
            for (BlockPosition position : removed.frame()) {
                removeIndex(affectedIndex,
                        new BlockKey(removed.worldId(), position.x(), position.y(), position.z()), portalId);
            }
            for (ChunkKey chunk : removed.chunks()) {
                removeIndex(chunkIndex, chunk, portalId);
            }
        }
        requestSave();
        return removed;
    }

    public PortalRecord get(UUID portalId) {
        return records.get(portalId);
    }

    public PortalRecord updateFrameMaterialSnapshot(PortalRecord expected, List<Material> materials) {
        PortalRecord updated;
        synchronized (mutationLock) {
            PortalRecord current = records.get(expected.id());
            if (current == null || !current.equals(expected)) {
                return null;
            }
            if (current.frameMaterialSnapshot().equals(materials)) {
                return current;
            }
            updated = current.withFrameMaterialSnapshot(materials);
            records.put(updated.id(), updated);
        }
        requestSave();
        return updated;
    }

    public List<PortalRecord> allRecords() {
        return List.copyOf(records.values());
    }

    public Set<UUID> affectedBy(Block block) {
        Set<UUID> portalIds = affectedIndex.get(BlockKey.from(block));
        return portalIds == null ? Set.of() : portalIds;
    }

    public Set<UUID> inChunk(World world, int chunkX, int chunkZ) {
        Set<UUID> ids = chunkIndex.get(new ChunkKey(world.getUID(), chunkX, chunkZ));
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    public int portalCount() {
        return records.size();
    }

    public int interiorCellCount() {
        return interiorIndex.size();
    }

    public void requestSave() {
        synchronized (persistenceLock) {
            if (!persistenceEnabled.get() || closed.get()) {
                return;
            }
            List<PortalRecord> snapshot = new ArrayList<>(records.values());
            persistenceExecutor.execute(() -> writeSnapshot(snapshot));
        }
    }

    @Override
    public void close() {
        synchronized (mutationLock) {
            synchronized (persistenceLock) {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                if (persistenceEnabled.get()) {
                    List<PortalRecord> snapshot = new ArrayList<>(records.values());
                    persistenceExecutor.execute(() -> writeSnapshot(snapshot));
                }
                persistenceExecutor.shutdown();
            }
        }
        try {
            if (!persistenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                failureHandler.accept(new IOException("Timed out while flushing the portal registry"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failureHandler.accept(exception);
        }
    }

    private <K> void addIndex(Map<K, Set<UUID>> index, K key, UUID portalId) {
        index.compute(key, (ignored, current) -> {
            Set<UUID> updated = current == null ? new HashSet<>() : new HashSet<>(current);
            updated.add(portalId);
            return Set.copyOf(updated);
        });
    }

    private <K> void removeIndex(Map<K, Set<UUID>> index, K key, UUID portalId) {
        index.computeIfPresent(key, (ignored, current) -> {
            Set<UUID> updated = new HashSet<>(current);
            updated.remove(portalId);
            return updated.isEmpty() ? null : Set.copyOf(updated);
        });
    }

    private void writeSnapshot(List<PortalRecord> snapshot) {
        try {
            Files.createDirectories(storeFile.toPath().getParent());
            String serialized = gson.toJson(new PortalStore(PortalRecord.CURRENT_SCHEMA_VERSION, snapshot));
            byte[] encoded = serialized.getBytes(StandardCharsets.UTF_8);
            // Writing past the load limit would produce a store the next startup refuses.
            if (encoded.length > MAXIMUM_STORE_BYTES) {
                throw new IOException("Portal store would exceed the 128 MiB safety limit; the previous store was kept");
            }
            Path target = storeFile.toPath();
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, encoded);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable exception) {
            failureHandler.accept(exception);
        }
    }

    private record PortalStore(int schemaVersion, List<PortalRecord> portals) {
    }
}
