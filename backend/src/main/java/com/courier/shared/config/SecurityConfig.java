package com.courier.shared.config;

import com.courier.shared.api.ApiResponse;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.security.JwtAuthenticationFilter;
import com.courier.shared.security.Roles;
import com.courier.shared.company.CompanyResolutionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT security.
 *
 * <p>The URL rules below are a coarse first gate only. Real authorisation lives on
 * service methods as {@code @PreAuthorize} — a URL pattern cannot express "only the
 * branch this user belongs to", and duplicating rules in two places guarantees they
 * will diverge. {@link EnableMethodSecurity} is what makes that work.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CompanyResolutionFilter companyResolutionFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    /** Endpoints reachable without a token. Keep this list short and reviewed. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/companies/register",
            "/api/v1/track/**",          // public parcel tracking, returns a redacted projection
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies or sessions are used, so there is no CSRF vector to protect:
                // an attacker's page cannot make the browser attach a bearer token.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Everything else under /actuator is operational data.
                        .requestMatchers("/actuator/**").hasRole(Roles.PLATFORM_ADMIN)
                        // Platform-wide pricing configuration. The authoritative check is
                        // @PreAuthorize on SubscriptionPlanService; this is the coarse gate.
                        .requestMatchers("/api/v1/subscription-plans/**").hasRole(Roles.SUPER_ADMIN)
                        // Company lifecycle. Same arrangement: @PreAuthorize on
                        // CompanyService is authoritative, this is the outer gate.
                        .requestMatchers("/api/v1/companies/**").hasRole(Roles.SUPER_ADMIN)
                        // The platform console: platform dashboard and super admin
                        // accounts. Nothing under here is company-scoped, so unlike the
                        // paths below a flat role rule expresses the whole truth.
                        .requestMatchers("/api/v1/super-admin/**").hasRole(Roles.SUPER_ADMIN)
                        // Global masters: the geography every company shares. SUPER_ADMIN
                        // writes; any authenticated user reads, because a booking clerk
                        // needs the pincode list. Split by @PreAuthorize, not by URL.
                        .requestMatchers("/api/v1/global-masters/**").authenticated()
                        // Roles are per-company: COMPANY_ADMIN writes, SUPER_ADMIN reads.
                        // No URL rule can express that split, so it is left to
                        // @PreAuthorize on RoleService and only authentication is
                        // required here.
                        .requestMatchers("/api/v1/roles/**").authenticated()
                        // Permissions catalogue + grants; same reasoning.
                        .requestMatchers("/api/v1/permissions/**").authenticated()
                        // Users: COMPANY_ADMIN writes, SUPER_ADMIN and branch/hub
                        // managers read within their scope. Authoritative on UserService.
                        .requestMatchers("/api/v1/users/**").authenticated()
                        // Company settings: any company user reads, COMPANY_ADMIN writes.
                        // Authoritative on CompanySettingsService.
                        .requestMatchers("/api/v1/company-settings/**").authenticated()
                        // Branches: COMPANY_ADMIN manages, BRANCH_MANAGER their own, users
                        // read their assigned branch. Authoritative on BranchService.
                        .requestMatchers("/api/v1/branches/**").authenticated()
                        // Branch wallet: a branch user reads and recharges their own,
                        // COMPANY_ADMIN credits and debits any. No URL rule can express
                        // "your branch", so it is left to @PreAuthorize plus the in-code
                        // scoping on WalletService.
                        .requestMatchers("/api/v1/branch-wallet/**").authenticated()
                        // Master data: COMPANY_ADMIN writes, any company user reads,
                        // SUPER_ADMIN reads across companies. A URL rule cannot split a
                        // GET from a POST on the same path cleanly enough to be the
                        // authority here, so it is left to @PreAuthorize on each service.
                        .requestMatchers("/api/v1/master/**").authenticated()
                        .anyRequest().authenticated())
                // Authenticate first, then bind the company from the verified principal.
                // The order is load-bearing: CompanyResolutionFilter reads the SecurityContext.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(companyResolutionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Cost 12: roughly 250 ms per hash on current hardware. Slow enough to make
     * offline cracking expensive, fast enough for an interactive login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 401 in the standard envelope. Without this Spring returns an empty body,
     * which breaks clients that always parse {@code ApiResponse}.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeError(response, request, ErrorCode.UNAUTHENTICATED, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, request, ErrorCode.ACCESS_DENIED,
                        ErrorCode.ACCESS_DENIED.getDefaultMessage());
    }

    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            ErrorCode code,
                            String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(code, message, request.getRequestURI()));
    }
}
