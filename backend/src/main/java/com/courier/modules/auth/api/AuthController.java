package com.courier.modules.auth.api;

import com.courier.modules.auth.api.dto.ChangePasswordRequest;
import com.courier.modules.auth.api.dto.CurrentUserResponse;
import com.courier.modules.auth.api.dto.ForgotPasswordRequest;
import com.courier.modules.auth.api.dto.LoginRequest;
import com.courier.modules.auth.api.dto.LoginResponse;
import com.courier.modules.auth.api.dto.LogoutRequest;
import com.courier.modules.auth.api.dto.RefreshTokenRequest;
import com.courier.modules.auth.api.dto.ResetPasswordRequest;
import com.courier.modules.auth.api.dto.VerifyEmailRequest;
import com.courier.modules.auth.application.AuthService;
import com.courier.modules.auth.application.EmailVerificationService;
import com.courier.modules.auth.application.PasswordService;
import com.courier.modules.auth.application.SessionService;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Thin by design: request validation, extraction of transport concerns (client
 * IP, user agent) and DTO mapping only. Every decision lives in the application
 * services, which is where {@code @Transactional} and {@code @PreAuthorize} belong.
 *
 * <p>There is intentionally no {@code /register} — creating users is company
 * onboarding, which belongs to {@code modules/company}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Sign-in, tokens, sessions and password lifecycle")
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;
    private final CompanyDirectoryPort companyDirectory;

    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = """
                    Authenticates against a company and opens a session.

                    An unknown email and a wrong password return the identical
                    `401 INVALID_CREDENTIALS`. Repeated failures lock the account
                    (`423`) and, per email+IP, throttle the endpoint (`429`).
                    """)
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Signed in"))
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {

        String ip = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        AuthService.AuthResult result = authService.login(new AuthService.LoginCommand(
                request.companyId(),
                request.companyCode(),
                request.email(),
                request.password(),
                request.rememberMe(),
                ip,
                userAgent,
                SessionService.DeviceInfo.of(request.deviceId(), request.deviceName(), ip, userAgent)));

        return ApiResponse.success(LoginResponse.from(result), "Signed in");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate tokens",
            description = """
                    Exchanges a refresh token for a new pair. The presented token is
                    revoked immediately.

                    Replaying an already-used token revokes the entire rotation
                    family and forces re-authentication — that is the intended
                    response to a stolen token, not a bug.
                    """)
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                              HttpServletRequest httpRequest) {

        AuthService.AuthResult result = authService.refresh(
                request.refreshToken(), clientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT));

        return ApiResponse.success(LoginResponse.from(result), "Tokens refreshed");
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out",
            description = "Ends the current session, or every session when `allDevices` is true. "
                    + "The presented access token is denylisted so it stops working immediately.")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        LogoutRequest effective = request != null ? request : new LogoutRequest(null, false);
        authService.logout(effective.refreshToken(), effective.allDevices());
        return ApiResponse.success(effective.allDevices() ? "Signed out of all devices" : "Signed out");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = """
                    **Always returns 200**, whether or not the address is registered.
                    Anything else would let a caller enumerate accounts.
                    """)
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {

        passwordService.forgotPassword(request.companyId(), request.email(), clientIp(httpRequest));

        // Deliberately identical wording in every case.
        return ApiResponse.success(
                "If that account exists, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset",
            description = "Consumes the emailed token, applies the password policy, clears any "
                    + "account lock, and revokes every existing session.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                           HttpServletRequest httpRequest) {

        passwordService.resetPassword(request.token(), request.newPassword(), clientIp(httpRequest));
        return ApiResponse.success("Password has been reset. Sign in with your new password.");
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change your password",
            description = "Requires the current password. **All sessions are revoked**, including "
                    + "this one — sign in again after a successful response.")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(request.currentPassword(), request.newPassword());
        return ApiResponse.success("Password changed. Please sign in again.");
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify an email address",
            description = "Consumes the emailed token and activates a PENDING account. "
                    + "A new link is sent automatically when an unverified user attempts to sign in.")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ApiResponse.success("Email address verified. You can now sign in.");
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Current user",
            description = "Read from the database rather than from token claims, so role changes "
                    + "and deactivations take effect immediately.")
    public ApiResponse<CurrentUserResponse> me() {
        var user = authService.currentUser();
        var company = companyDirectory.findById(user.getCompanyId()).orElse(null);
        return ApiResponse.success(CurrentUserResponse.from(user, company));
    }

    /**
     * Client address for audit and throttling.
     *
     * <p>{@code X-Forwarded-For} is client-controllable, so this is evidence for an
     * investigation, never an authorisation input. Behind a trusted proxy the first
     * entry is the real client; without one, the socket address is used.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() <= 45 ? first : first.substring(0, 45);
        }
        return request.getRemoteAddr();
    }
}
