package com.bekololek.pluginfactory.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DashboardKeyFilterTest {

    private DashboardKeyFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new DashboardKeyFilter();
        ReflectionTestUtils.setField(filter, "dashboardApiKey", "test-dashboard-key");
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validKeyGrantsAdminRole() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");
        request.addHeader("X-Dashboard-Key", "test-dashboard-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidKeyDoesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");
        request.addHeader("X-Dashboard-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingHeaderDoesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonAdminPathIsSkipped() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("X-Dashboard-Key", "test-dashboard-key");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void adminPathIsNotSkipped() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void emptyConfigKeyDoesNotAuthenticate() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "dashboardApiKey", "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/overview");
        request.addHeader("X-Dashboard-Key", "test-dashboard-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
