package com.courier.modules.platformintegration.api;

import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSignInControllerTest {

    @Test
    void configPointsTheBrowserAtTheRealmEndpointsAndTheWebClient() {
        var props = new PlatformIntegrationProperties(true, "https://auth.example.com/realms/occpl/", "jwks", "courier-api",
                "occpl-core-services", "STARTER", null, "courier-web", "http://core", "http://token", "courier-service", "secret");
        var config = new PlatformSignInController(null, props).config().data();

        assertThat(config.authorizationEndpoint())
                .isEqualTo("https://auth.example.com/realms/occpl/protocol/openid-connect/auth");
        assertThat(config.tokenEndpoint()).isEqualTo("https://auth.example.com/realms/occpl/protocol/openid-connect/token");
        assertThat(config.clientId()).isEqualTo("courier-web");
    }
}
