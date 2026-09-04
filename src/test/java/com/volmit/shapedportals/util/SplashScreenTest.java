package com.volmit.shapedportals.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplashScreenTest {
    @Test
    void detailsUseThePublicStartupFormat() {
        assertThat(SplashScreen.details("2.0.0-1.20.1-26.2", "Paper 26.2", "25", "2026-09-03"))
                .containsExactly(
                        "",
                        "ShapedPortals, Free-form Nether and End Portals",
                        "Version: 2.0.0-1.20.1-26.2",
                        "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                        "Server: Paper 26.2 | MC Support: 1.20.1 - 26.x",
                        "Java: 25 | Date: 2026-09-03"
                );
    }
}
