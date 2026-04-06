package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.auth.JwtAuthenticationFilter;
import com.bekololek.pluginfactory.auth.JwtService;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private StripeService stripeService;

    @Test
    void listTiers_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("FREE"))
                .andExpect(jsonPath("$[0].maxBuilds").value(1))
                .andExpect(jsonPath("$[1].name").value("BASIC"))
                .andExpect(jsonPath("$[2].name").value("PRO"))
                .andExpect(jsonPath("$[3].name").value("TEAM"));
    }

    @Test
    void listTiers_containsAllFields() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tokenBudget").value(50000))
                .andExpect(jsonPath("$[0].maxParallel").value(0))
                .andExpect(jsonPath("$[0].maxIterations").value(0))
                .andExpect(jsonPath("$[0].maxCommands").value(5))
                .andExpect(jsonPath("$[0].maxEventListeners").value(3))
                .andExpect(jsonPath("$[0].jarRetentionDays").value(7))
                .andExpect(jsonPath("$[0].marketplaceSlots").value(0))
                .andExpect(jsonPath("$[0].sourceCodeAccess").value(false));
    }

    @Test
    void getCurrentSubscription_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/current"))
                .andExpect(status().isUnauthorized());
    }
}
