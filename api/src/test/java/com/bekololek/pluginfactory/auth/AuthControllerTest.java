package com.bekololek.pluginfactory.auth;

import com.bekololek.pluginfactory.auth.dto.AuthResponse;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getDiscordAuthUrl() throws Exception {
        when(authService.getDiscordAuthorizationUrl()).thenReturn("https://discord.com/api/oauth2/authorize?client_id=test");

        mockMvc.perform(get("/api/v1/auth/discord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://discord.com/api/oauth2/authorize?client_id=test"));
    }

    @Test
    void handleDiscordCallback() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthResponse authResponse = new AuthResponse(
                "access-token",
                "refresh-token",
                new AuthResponse.UserInfo(userId, "test@example.com", "TestUser", "123456789")
        );
        when(authService.handleDiscordCallback(eq("test-code"), any(String.class))).thenReturn(authResponse);

        mockMvc.perform(get("/api/v1/auth/discord/callback").param("code", "test-code").param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("TestUser"))
                .andExpect(jsonPath("$.user.discordId").value("123456789"));
    }

    @Test
    void refreshToken() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthResponse authResponse = new AuthResponse(
                "new-access-token",
                "refresh-token",
                new AuthResponse.UserInfo(userId, "test@example.com", "TestUser", "123456789")
        );
        when(authService.refreshAccessToken("refresh-token")).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void logout() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId(any(String.class))).thenReturn(userId);

        // Create a real JWT service for generating a valid token
        JwtService realJwtService = new JwtService(
                "test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
                900000, 604800000);
        String token = realJwtService.generateAccessToken(userId, "USER");

        // Make jwtService mock extract the correct userId from the token
        when(jwtService.extractUserId(token)).thenReturn(userId);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(authService).logout(userId);
    }

    @Test
    void protectedEndpointReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/some-protected-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        when(authService.getDiscordAuthorizationUrl()).thenReturn("https://discord.com/api/oauth2/authorize");

        mockMvc.perform(get("/api/v1/auth/discord"))
                .andExpect(status().isOk());
    }
}
