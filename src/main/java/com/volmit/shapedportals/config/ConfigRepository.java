package com.volmit.shapedportals.config;

import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.io.AtomicFileIO;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ConfigRepository {
    private static final long MAXIMUM_CONFIG_BYTES = 2L * 1024L * 1024L;

    private final File dataFolder;
    private final File configFile;

    public ConfigRepository(File dataFolder) {
        this.dataFolder = dataFolder;
        this.configFile = new File(dataFolder, "shapedportals.toml");
    }

    public synchronized PreparedConfig load() throws IOException {
        if (!configFile.exists()) {
            ShapedPortalsConfig defaults = new ShapedPortalsConfig();
            PreparedConfig prepared = prepare(defaults, null);
            AtomicFileIO.writeString(configFile.toPath(), prepared.serialized());
            return prepared;
        }
        if (!configFile.isFile()) {
            throw new IOException("Configuration path is not a file: " + configFile);
        }
        if (configFile.length() > MAXIMUM_CONFIG_BYTES) {
            throw new IOException("Configuration exceeds the 2 MiB safety limit");
        }
        String raw = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        try {
            return prepare(TomlCodec.fromToml(raw, ShapedPortalsConfig.class), raw);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid ShapedPortals configuration", exception);
        }
    }

    public synchronized PreparedConfig save(ShapedPortalsConfig config) throws IOException {
        PreparedConfig prepared = prepare(config, null);
        AtomicFileIO.writeString(configFile.toPath(), prepared.serialized());
        return prepared;
    }

    public synchronized String readDisk() throws IOException {
        if (!configFile.exists()) {
            return null;
        }
        if (!configFile.isFile() || configFile.length() > MAXIMUM_CONFIG_BYTES) {
            throw new IOException("Configuration path is not a readable file: " + configFile);
        }
        return Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
    }

    public PreparedConfig defaults() {
        return prepare(new ShapedPortalsConfig(), null);
    }

    public PreparedConfig creationPausedDefaults() {
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        config.general.enabled = false;
        PreparedConfig prepared = prepare(config, null);
        return new PreparedConfig(prepared.source(), prepared.runtime(), prepared.serialized(), null);
    }

    public File configFile() {
        return configFile;
    }

    public File dataFolder() {
        return dataFolder;
    }

    private PreparedConfig prepare(ShapedPortalsConfig config, String diskContent) {
        RuntimeConfig runtime = RuntimeConfig.from(config);
        String serialized = TomlCodec.toToml(runtime.source(), "shapedportals");
        return new PreparedConfig(runtime.source(), runtime, serialized, diskContent == null ? serialized : diskContent);
    }

    /**
     * {@code diskContent} is the shapedportals.toml text this configuration was accepted from, or null when
     * the file on disk was never accepted and must not be overwritten by in-game edits.
     */
    public record PreparedConfig(ShapedPortalsConfig source, RuntimeConfig runtime, String serialized,
                                 String diskContent) {
    }
}
