package com.volmit.shapedportals.update;

import art.arcane.volmlib.util.update.BukkitUpdateService;
import com.volmit.shapedportals.ShapedPortals;
import com.volmit.shapedportals.config.ConfigService;
import com.volmit.shapedportals.config.RuntimeConfig;
import com.volmit.shapedportals.config.ShapedPortalsConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateNotifierTest {
    @Test
    void registersSharedUpdatesWithLiveConfigurationAndClosesRegistration() {
        ShapedPortals plugin = mock(ShapedPortals.class);
        ConfigService configService = mock(ConfigService.class);
        ShapedPortalsConfig config = new ShapedPortalsConfig();
        BukkitUpdateService service = mock(BukkitUpdateService.class);
        AtomicReference<BukkitUpdateService.Options> options = new AtomicReference<>();
        when(plugin.getConfigService()).thenReturn(configService);
        when(configService.runtime()).thenAnswer(ignored -> RuntimeConfig.from(config));
        try (MockedStatic<BukkitUpdateService> shared = mockStatic(BukkitUpdateService.class)) {
            shared.when(() -> BukkitUpdateService.register(eq(plugin), any())).thenAnswer(call -> {
                options.set(call.getArgument(1));
                return service;
            });
            try (UpdateNotifier notifier = new UpdateNotifier(plugin)) {
                assertThat(options.get().owner()).isEqualTo("VolmitSoftware");
                assertThat(options.get().repository()).isEqualTo("ShapedPortals");
                assertThat(options.get().permission()).isEqualTo("shapedportals.update");
                assertThat(options.get().enabled().getAsBoolean()).isTrue();
                config.general.updateNotifications = false;
                assertThat(options.get().enabled().getAsBoolean()).isFalse();
                notifier.reconfigure();
                verify(service).reconfigure();
            }
            verify(service).close();
        }
    }
}
