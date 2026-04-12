package com.bekololek.pluginfactory.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String DASHBOARD_KEY_HEADER = "X-Dashboard-Key";

    @Value("${dashboard.api-key:}")
    private String dashboardApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(DASHBOARD_KEY_HEADER);

        if (StringUtils.hasText(key) && StringUtils.hasText(dashboardApiKey) && dashboardApiKey.equals(key)) {
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(UUID.fromString("00000000-0000-0000-0000-000000000000"), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }
}
