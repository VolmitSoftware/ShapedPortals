package com.volmit.shapedportals.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.localization.LanguageService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionCommandTest {
    @TempDir
    Path directory;

    @Test
    void bothCommandPathsPrintOnlyTheInstalledVersion() {
        ShapedPortals plugin = mock(ShapedPortals.class);
        CommandSender sender = mock(CommandSender.class);
        DirectorSender invocationSender = mock(DirectorSender.class);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("ShapedPortals", "3.2.1-test", "test.Plugin"));
        when(plugin.getLanguageService()).thenReturn(new LanguageService(directory.toFile(), Logger.getAnonymousLogger()));
        when(sender.hasPermission("shapedportals.command")).thenReturn(true);
        when(invocationSender.getName()).thenReturn("Console");
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, arguments) -> sender);
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(new ShapedPortalsCommands(plugin),
                DirectorEngineOptions.builder().contexts(contexts).build());

        assertThat(engine.execute(new DirectorInvocation(invocationSender, "shapedportals", List.of("debug", "version"))).isSuccess()).isTrue();
        assertThat(engine.execute(new DirectorInvocation(invocationSender, "shapedportals", List.of("version"))).isSuccess()).isTrue();

        verify(sender, times(2)).sendMessage("ShapedPortals v3.2.1-test");
    }
}
