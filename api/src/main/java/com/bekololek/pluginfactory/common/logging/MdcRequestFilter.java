package com.bekololek.pluginfactory.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates SLF4J MDC with per-request correlation keys so every log line
 * for a request carries {@code requestId} and (when authenticated) {@code userId}.
 * Build-pipeline async work additionally puts {@code sessionId}; see
 * {@link MdcAsyncTaskDecorator}.
 *
 * <p>Runs after JwtAuthenticationFilter so the SecurityContext is populated.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdcRequestFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(REQUEST_ID, requestId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID userId) {
            MDC.put(USER_ID, userId.toString());
        }

        try {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
            MDC.remove(SESSION_ID);
        }
    }
}
