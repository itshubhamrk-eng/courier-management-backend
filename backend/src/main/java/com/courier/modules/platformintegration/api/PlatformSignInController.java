package com.courier.modules.platformintegration.api;

import com.courier.modules.auth.api.dto.LoginResponse;
import com.courier.modules.auth.application.SessionService;
import com.courier.modules.platformintegration.application.PlatformSignInService;
import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import com.courier.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exchanges a platform access token for courier tokens. Public like {@code /auth/login}: the platform
 * token in the body is the credential, and it is verified (signature, issuer, audience, client) before use.
 */
@RestController
@RequestMapping("/api/v1/auth/platform")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
public class PlatformSignInController {

    private final PlatformSignInService service;
    private final PlatformIntegrationProperties properties;

    public record ExchangeRequest(@NotBlank @Size(max = 8000) String accessToken,
                                  @NotNull UUID platformCompanyId,
                                  @Size(max = 100) String deviceId,
                                  @Size(max = 150) String deviceName) {
    }

    /** Where the browser sends people to sign in with the platform. */
    public record SsoConfig(String authorizationEndpoint, String tokenEndpoint, String clientId) {
    }

    /**
     * Public: tells the web app how to start the platform sign-in (public Keycloak addresses and the web
     * client id), so those are set with the rest of the server settings and need no web rebuild. A 404 here
     * means the platform sign-in is not switched on.
     */
    @GetMapping("/config")
    public ApiResponse<SsoConfig> config() {
        String issuer = properties.issuerUri().replaceAll("/+$", "");
        return ApiResponse.success(new SsoConfig(issuer + "/protocol/openid-connect/auth",
                issuer + "/protocol/openid-connect/token", properties.webClientId()));
    }

    @PostMapping("/exchange")
    public ApiResponse<LoginResponse> exchange(@Valid @RequestBody ExchangeRequest request,
                                               HttpServletRequest http) {
        String ip = clientIp(http);
        String userAgent = http.getHeader(HttpHeaders.USER_AGENT);
        var result = service.signIn(new PlatformSignInService.Request(request.accessToken(),
                request.platformCompanyId(), ip, userAgent,
                SessionService.DeviceInfo.of(request.deviceId(), request.deviceName(), ip, userAgent)));
        return ApiResponse.success(LoginResponse.from(result), "Signed in");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() <= 45 ? first : first.substring(0, 45);
        }
        return request.getRemoteAddr();
    }
}
