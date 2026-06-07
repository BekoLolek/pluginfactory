package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.agent.PlanGenerationAgent;
import com.bekololek.pluginfactory.auth.JwtAuthenticationFilter;
import com.bekololek.pluginfactory.auth.JwtService;
import com.bekololek.pluginfactory.build.BuildLauncher;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.ChatMessageService;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, TokenEstimateService.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PlanDocumentRepository planDocumentRepository;

    @MockBean
    private BuildSessionService buildSessionService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private ComplexityEstimator complexityEstimator;

    @MockBean
    private ScopeGatingService scopeGatingService;

    @MockBean
    private PlanGenerationAgent planGenerationAgent;

    @MockBean
    private ChatMessageService chatMessageService;

    @MockBean
    private BuildLauncher buildLauncher;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @Test
    void getPlan_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/builds/{sessionId}/plan", sessionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlan_returns404WhenNoPlan() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/builds/{sessionId}/plan", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlan_returnsPlanWhenExists() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        PlanDocument plan = createPlan();

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(plan));

        mockMvc.perform(get("/api/v1/builds/{sessionId}/plan", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginName").value("TestPlugin"))
                .andExpect(jsonPath("$.description").value("A test plugin"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void approvePlan_passes_returnsOk() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        PlanDocument plan = createPlan();

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);
        when(subscriptionService.getRemainingMonthlyTokens(userId)).thenReturn(900_000);
        when(scopeGatingService.validateScope(any(), eq(Tier.PRO)))
                .thenReturn(new ScopeGatingService.ScopeValidationResult(
                        ScopeGatingService.ScopeStatus.PASS, List.of()));

        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/approve", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pluginName").value("TestPlugin"));

        verify(buildLauncher).startBuild(sessionId, "INITIAL");
    }

    @Test
    void approvePlan_exceedsBudget_returns422() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        PlanDocument plan = createPlan();

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.FREE);
        when(scopeGatingService.validateScope(any(), eq(Tier.FREE)))
                .thenReturn(new ScopeGatingService.ScopeValidationResult(
                        ScopeGatingService.ScopeStatus.PASS, List.of()));
        // Scope passes, but only 5k tokens remain — the build needs far more.
        when(subscriptionService.getRemainingMonthlyTokens(userId)).thenReturn(5_000);

        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/approve", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estimate.verdict").value("EXCEEDS"))
                .andExpect(jsonPath("$.message").exists());

        verify(buildLauncher, org.mockito.Mockito.never()).startBuild(any(), any());
    }

    @Test
    void approvePlan_exceedsTier_returns422() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        PlanDocument plan = createPlan();

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.FREE);
        when(scopeGatingService.validateScope(any(), eq(Tier.FREE)))
                .thenReturn(new ScopeGatingService.ScopeValidationResult(
                        ScopeGatingService.ScopeStatus.EXCEEDS_TIER,
                        List.of("Commands: 6 exceeds tier limit of 5")));

        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/approve", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("EXCEEDS_TIER"))
                .andExpect(jsonPath("$.violations[0]").value("Commands: 6 exceeds tier limit of 5"));
    }

    @Test
    void approvePlan_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/approve", sessionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revisePlan_returnsRevisedPlan() throws Exception {
        String token = setupAuth();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        PlanDocument revisedPlan = createPlan();
        revisedPlan.setPluginName("RevisedPlugin");
        revisedPlan.setVersion(2);

        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(planGenerationAgent.generatePlan(sessionId)).thenReturn(revisedPlan);

        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/revise", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"Add more commands please\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginName").value("RevisedPlugin"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void revisePlan_blankFeedback_returns400() throws Exception {
        String token = setupAuth();

        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/revise", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revisePlan_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/builds/{sessionId}/plan/revise", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"Add more\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String setupAuth() {
        String token = "test-token";
        when(jwtService.extractUserId(token)).thenReturn(userId);
        return token;
    }

    private PlanDocument createPlan() {
        PlanDocument plan = new PlanDocument();
        plan.setId(UUID.randomUUID());
        plan.setSessionId(sessionId);
        plan.setPluginName("TestPlugin");
        plan.setDescription("A test plugin");
        plan.setMinecraftVersion("1.20.4");
        plan.setServerType("SPIGOT");
        plan.setCommands("[{\"name\":\"test\",\"description\":\"Test command\",\"permission\":\"test.cmd\",\"usage\":\"/test\"}]");
        plan.setEventListeners("[{\"event\":\"PlayerJoinEvent\",\"description\":\"Greet player\"}]");
        plan.setConfigSchema("[{\"key\":\"enabled\",\"type\":\"boolean\",\"defaultValue\":\"true\",\"description\":\"Enable plugin\"}]");
        plan.setDependencies("[]");
        plan.setTestScenarios("[{\"name\":\"Basic test\",\"description\":\"Test basic flow\",\"type\":\"unit\"}]");
        plan.setEstimatedLoc(200);
        plan.setComplexityScore(25);
        plan.setVersion(1);
        plan.setCreatedAt(Instant.now());
        plan.setUpdatedAt(Instant.now());
        return plan;
    }
}
