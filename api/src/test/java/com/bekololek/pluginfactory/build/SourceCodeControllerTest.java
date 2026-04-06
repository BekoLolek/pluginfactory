package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.auth.JwtAuthenticationFilter;
import com.bekololek.pluginfactory.auth.JwtService;
import com.bekololek.pluginfactory.build.dto.SourceCodeRequestDto;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import com.bekololek.pluginfactory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourceCodeController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class SourceCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SourceCodeRequestService sourceCodeRequestService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void requestSourceCode_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID watermarkId = UUID.randomUUID();

        String token = "valid-token";
        when(jwtService.extractUserId(token)).thenReturn(userId);

        SourceCodeRequestDto dto = new SourceCodeRequestDto(
                requestId, userId, artifactId, "PENDING", "1.0",
                Instant.now(), watermarkId, Instant.now(), null
        );

        when(sourceCodeRequestService.requestSourceCode(eq(userId), eq(artifactId), eq("1.0"), any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/v1/source-code/request")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"artifactId\":\"" + artifactId + "\",\"licenseVersion\":\"1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void requestSourceCode_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/source-code/request")
                        .contentType("application/json")
                        .content("{\"artifactId\":\"" + UUID.randomUUID() + "\",\"licenseVersion\":\"1.0\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fulfillRequest_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/source-code/" + UUID.randomUUID() + "/fulfill"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadSourceCode_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/source-code/" + UUID.randomUUID() + "/download"))
                .andExpect(status().isUnauthorized());
    }
}
