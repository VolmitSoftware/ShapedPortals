package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.config.ConfigFileSupport;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ConfigService {
    private final ConfigRepository repository;
    private final AtomicReference<ConfigRepository.PreparedConfig> current;
    private volatile BiConsumer<File, String> selfWriteListener;

    public ConfigService(File dataFolder) {
        this.repository = new ConfigRepository(dataFolder);
        this.current = new AtomicReference<>(repository.defaults());
    }

    public ConfigRepository.PreparedConfig prepareFromDisk() throws IOException {
        return repository.load();
    }

    public void install(ConfigRepository.PreparedConfig prepared) {
        current.set(Objects.requireNonNull(prepared, "Prepared configuration cannot be null"));
    }

    public synchronized RuntimeConfig update(Consumer<ShapedPortalsConfig> editor) throws IOException {
        rejectUnappliedDiskChanges();
        ShapedPortalsConfig candidate = current.get().source().copy();
        editor.accept(candidate);
        ConfigRepository.PreparedConfig prepared = repository.save(candidate);
        current.set(prepared);
        BiConsumer<File, String> listener = selfWriteListener;
        if (listener != null) {
            listener.accept(repository.configFile(), prepared.serialized());
        }
        return prepared.runtime();
    }

    public ConfigRepository.PreparedConfig creationPausedDefaults() {
        return repository.creationPausedDefaults();
    }

    private void rejectUnappliedDiskChanges() throws IOException {
        String disk = repository.readDisk();
        if (disk == null) {
            return;
        }
        String accepted = current.get().diskContent();
        if (accepted == null || !ConfigFileSupport.normalize(disk).equals(ConfigFileSupport.normalize(accepted))) {
            throw new IOException("config.toml has changes that are not applied yet; fix or wait for the file to reload, then edit again");
        }
    }

    public RuntimeConfig runtime() {
        return current.get().runtime();
    }

    public ShapedPortalsConfig editableCopy() {
        return current.get().source().copy();
    }

    public File configFile() {
        return repository.configFile();
    }

    public File dataFolder() {
        return repository.dataFolder();
    }

    public void setSelfWriteListener(BiConsumer<File, String> listener) {
        selfWriteListener = listener;
    }
}
