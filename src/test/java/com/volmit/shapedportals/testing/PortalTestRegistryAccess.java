package com.volmit.shapedportals.testing;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.BlockType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PortalTestRegistryAccess implements RegistryAccess {
    private static final Set<String> AIR_BLOCKS = Set.of("air", "cave_air", "void_air");

    @Override
    @SuppressWarnings("removal")
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return new PortalRegistry<>(null);
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        Class<? extends Keyed> type = registryKey == RegistryKey.BLOCK ? BlockType.Typed.class
                : registryKey == RegistryKey.SOUND_EVENT ? Sound.class : null;
        return new PortalRegistry<>(type);
    }

    private static boolean hasBlock(NamespacedKey key) {
        try {
            BlockType.class.getField(key.getKey().toUpperCase(Locale.ROOT));
            return true;
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }

    private static Keyed value(Class<? extends Keyed> type, NamespacedKey key) {
        if (type == null || !key.getNamespace().equals(NamespacedKey.MINECRAFT)
                || type == BlockType.Typed.class && !hasBlock(key)) {
            return null;
        }
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
            case "getKey" -> key;
            case "isAir" -> AIR_BLOCKS.contains(key.getKey());
            case "hashCode" -> key.hashCode();
            case "toString" -> key.toString();
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException(method.toString());
        };
        return (Keyed) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class PortalRegistry<T extends Keyed> implements Registry<T> {
        private final Class<? extends Keyed> type;

        private PortalRegistry(Class<? extends Keyed> type) {
            this.type = type;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get(NamespacedKey key) {
            return (T) value(type, key);
        }

        @Override
        public NamespacedKey getKey(T value) {
            return value.getKey();
        }

        @Override
        public boolean hasTag(TagKey<T> key) {
            return false;
        }

        @Override
        public Tag<T> getTag(TagKey<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<Tag<T>> getTags() {
            return List.of();
        }

        @Override
        public Iterator<T> iterator() {
            return List.<T>of().iterator();
        }

        @Override
        public Stream<T> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<NamespacedKey> keyStream() {
            return Stream.empty();
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
