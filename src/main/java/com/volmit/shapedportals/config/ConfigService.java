package com.volmit.shapedportals.config;

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
