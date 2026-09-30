package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.PublicLeadRequest;
import com.courier.modules.support.api.dto.PublicLeadResponse;
import com.courier.modules.support.application.PublicLeadService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The marketing site's Contact/Request-a-Quote form — no bearer token required. Listed at
 * {@code /api/v1/leads} in {@code SecurityConfig.PUBLIC_ENDPOINTS}; kept as its own
 * controller/service pair for the same reason {@code PublicTrackController} is (see
 * {@link PublicLeadService}) — the "no auth" boundary is a file, not a missing
 * {@code @PreAuthorize} to notice.
 */
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Public Leads", description = "Marketing site contact/quote-request submissions — no login required")
public class PublicLeadController {

    private final PublicLeadService publicLeadService;

    @PostMapping
    @Operation(summary = "Submit a contact/quote-request lead (public)",
            description = "Raises a support ticket under the Amazing Logistics tenant. "
                    + "Rate-limited per caller IP.")
    public ApiResponse<PublicLeadResponse> submit(
            @Valid @RequestBody PublicLeadRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(publicLeadService.submit(request, clientIp(httpRequest)));
    }

    /** Same reasoning as {@code ActivityLoggingFilter.clientIp}: a proxy-visible header,
     *  treated as a rate-limiting hint, never as an authentication signal. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() <= 45 ? first : first.substring(0, 45);
        }
        String remote = request.getRemoteAddr();
        return remote != null && remote.length() > 45 ? remote.substring(0, 45) : remote;
    }
}
