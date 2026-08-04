package com.courier.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 definition. Served at {@code /swagger-ui.html}; disabled in production
 * via {@code springdoc.api-docs.enabled} in {@code application-prod.yml}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String COMPANY_HEADER_SCHEME = "companyHeader";

    @Value("${app.version:0.1.0}")
    private String version;

    @Bean
    public OpenAPI courierOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Courier Management API")
                        .description("""
                                Multi-company Courier SaaS backend.

                                **Authentication** — obtain a token from `POST /api/v1/auth/login`
                                and send it as `Authorization: Bearer <token>`.

                                **Tenancy** — the company is taken from the `tid` claim of your token.
                                The `X-Company-ID` header is honoured only for `PLATFORM_ADMIN`
                                and every use is audited.

                                **Responses** — every endpoint returns the same envelope:
                                `{ success, message, data, errorCode, errors, timestamp, path, requestId }`.
                                Quote `requestId` in any support request.
                                """)
                        .version(version)
                        .contact(new Contact().name("Backend Team").email("backend@courier.example"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api-dev.courier.example").description("Development")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from /api/v1/auth/login"))
                        .addSecuritySchemes(COMPANY_HEADER_SCHEME, new SecurityScheme()
                                .name("X-Company-ID")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("PLATFORM_ADMIN only: act on behalf of a company. Audited.")));
    }
}
