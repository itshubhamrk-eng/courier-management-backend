package com.courier.shared.config;

import com.courier.shared.api.RequestIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every request, puts it in the MDC and echoes it back
 * in the {@code X-Request-Id} response header.
 *
 * <p>This is what makes an opaque "internal error" answerable: the id in the error
 * envelope is the same id on every log line for that request. An inbound id from an
 * upstream gateway is honoured so a trace survives across services.
 *
 * <p>Ordered {@link Ordered#HIGHEST_PRECEDENCE} so even authentication failures are
 * logged with an id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolve(request);
        try {
            MDC.put(RequestIdHolder.REQUEST_ID_KEY, requestId);
            response.setHeader(RequestIdHolder.REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled; a stale id would be attributed to the next request.
            MDC.remove(RequestIdHolder.REQUEST_ID_KEY);
        }
    }

    private String resolve(HttpServletRequest request) {
        String inbound = request.getHeader(RequestIdHolder.REQUEST_ID_HEADER);
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }
        // The header is caller-controlled: it lands in logs and in a DB column, so it
        // is length-capped and stripped of anything that could forge a log line.
        String sanitised = inbound.replaceAll("[^A-Za-z0-9\\-_.]", "");
        if (sanitised.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sanitised.length() > MAX_LENGTH ? sanitised.substring(0, MAX_LENGTH) : sanitised;
    }
}
