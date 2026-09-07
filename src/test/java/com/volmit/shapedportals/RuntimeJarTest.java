package com.volmit.shapedportals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

final class RuntimeJarTest {
    private static final String PLUGIN_PACKAGE = "com.volmit.shapedportals.";
    private static final String LIBRARY_PACKAGE = PLUGIN_PACKAGE + "libs.";

    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedConfigCreatesAndReloadsToml() throws Exception {
        try (RuntimeLoader loader = runtimeLoader()) {
            Class<?> repositoryType = loader.loadClass(PLUGIN_PACKAGE + "config.ConfigRepository");
            Object repository = repositoryType.getConstructor(File.class).newInstance(temporaryDirectory.toFile());
            repositoryType.getMethod("load").invoke(repository);
            Path configFile = temporaryDirectory.resolve("config.toml");
            String saved = Files.readString(configFile).replace("maximumInteriorBlocks = 256", "maximumInteriorBlocks = 512");
            Files.writeString(configFile, saved);
            Object prepared = repositoryType.getMethod("load").invoke(repository);
            String serialized = (String) prepared.getClass().getMethod("serialized").invoke(prepared);
            assertThat(serialized).contains("[portal]").contains("maximumInteriorBlocks = 512");
        }
    }

    @Test
    void packagedAdventureLoadsSerializers() throws Exception {
        try (RuntimeLoader loader = runtimeLoader()) {
            Class<?> textType = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.plugin.ComponentText");
            Object text = textType.getMethod("markup", String.class).invoke(null, "<red>Portal</red>");
            assertThat(textType.getMethod("plain").invoke(text)).isEqualTo("Portal");
            assertThat((String) textType.getMethod("legacy").invoke(text)).endsWith("Portal");
            assertThat((String) textType.getMethod("miniMessage").invoke(text)).contains("<red>Portal");
        }
    }

    @Test
    void packagedDirectorReflectsCommandTree() throws Exception {
        try (RuntimeLoader loader = runtimeLoader()) {
            Class<?> pluginType = loader.loadClass(PLUGIN_PACKAGE + "ShapedPortals");
            Class<?> commandsType = loader.loadClass(PLUGIN_PACKAGE + "command.ShapedPortalsCommands");
            Object commands = commandsType.getConstructor(pluginType).newInstance(new Object[]{null});
            Class<?> factoryType = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.director.compat.DirectorEngineFactory");
            Object engine = factoryType.getMethod("create", Object.class).invoke(null, commands);
            Object root = engine.getClass().getMethod("getRoot").invoke(engine);
            List<?> children = (List<?>) root.getClass().getMethod("getChildren").invoke(root);
            assertThat(children).isNotEmpty();
            for (Object child : children) {
                Object descriptor = child.getClass().getMethod("getDescriptor").invoke(child);
                assertThat((String) descriptor.getClass().getMethod("getName").invoke(descriptor)).isNotBlank();
            }
        }
    }

    @Test
    void packagedGsonReadsPortalStoreRecord() throws Exception {
        Files.writeString(temporaryDirectory.resolve("portals.json"), "{\"schemaVersion\":1,\"portals\":[]}");
        try (RuntimeLoader loader = runtimeLoader()) {
            Class<?> registryType = loader.loadClass(PLUGIN_PACKAGE + "portal.PortalRegistry");
            Consumer<Throwable> failureHandler = failure -> {
                throw new AssertionError("Portal persistence failed", failure);
            };
            try (AutoCloseable registry = (AutoCloseable) registryType.getConstructor(File.class, Consumer.class)
                    .newInstance(temporaryDirectory.toFile(), failureHandler)) {
                registryType.getMethod("load").invoke(registry);
                assertThat(registryType.getMethod("portalCount").invoke(registry)).isEqualTo(0);
            }
        }
    }

    private RuntimeLoader runtimeLoader() throws Exception {
        String artifact = Objects.requireNonNull(System.getProperty("shapedportals.runtimeJar"));
        String libraries = Objects.requireNonNull(System.getProperty("shapedportals.runtimeTestLibraries"));
        return new RuntimeLoader(new URL[]{Path.of(artifact).toUri().toURL(), Path.of(libraries).toUri().toURL()},
                getClass().getClassLoader());
    }

    private static final class RuntimeLoader extends URLClassLoader {
        private RuntimeLoader(URL[] artifacts, ClassLoader parent) {
            super(artifacts, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
