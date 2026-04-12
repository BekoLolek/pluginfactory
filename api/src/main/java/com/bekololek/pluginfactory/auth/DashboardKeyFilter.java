package com.bekololek.pluginfactory.auth;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates requests from the admin dashboard using a static API key.
 * When a valid X-Dashboard-Key header is present on /api/v1/admin/** requests,
 * sets ROLE_ADMIN authentication so Spring Security's hasRole("ADMIN") passes.
 *
 * Runs before JwtAuthenticationFilter.
 */
@Component
public class DashboardKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DashboardKeyFilter.class);
    private static final String DASHBOARD_KEY_HEADER = "X-Dashboard-Key";

    @Value("${dashboard.api-key:}")
    private String dashboardApiKey;

    @PostConstruct
    void logConfig() {
        if (StringUtils.hasText(dashboardApiKey)) {
            log.info("DashboardKeyFilter: key configured ({} chars, starts: {}...)",
                    dashboardApiKey.length(), dashboardApiKey.substring(0, Math.min(4, dashboardApiKey.length())));
        } else {
            log.warn("DashboardKeyFilter: DASHBOARD_API_KEY is NOT set — dashboard auth disabled");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(DASHBOARD_KEY_HEADER);

        log.debug("DashboardKeyFilter: path={}, headerPresent={}, configuredKeyPresent={}",
                request.getRequestURI(), StringUtils.hasText(key), StringUtils.hasText(dashboardApiKey));

        if (StringUtils.hasText(key) && StringUtils.hasText(dashboardApiKey)) {
            if (dashboardApiKey.equals(key)) {
                log.info("DashboardKeyFilter: key matched — granting ROLE_ADMIN for {}", request.getRequestURI());
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(UUID.fromString("00000000-0000-0000-0000-000000000000"), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("DashboardKeyFilter: key MISMATCH for {} (received {} chars starts: {}..., expected {} chars starts: {}...)",
                        request.getRequestURI(),
                        key.length(), key.substring(0, Math.min(4, key.length())),
                        dashboardApiKey.length(), dashboardApiKey.substring(0, Math.min(4, dashboardApiKey.length())));
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }
}
