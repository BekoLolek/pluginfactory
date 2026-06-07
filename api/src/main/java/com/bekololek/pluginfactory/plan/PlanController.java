package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.agent.PlanGenerationAgent;
import com.bekololek.pluginfactory.build.BuildLauncher;
import com.bekololek.pluginfactory.build.BuildPhase;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.build.ChatMessageService;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import com.bekololek.pluginfactory.plan.dto.CommandSpec;
import com.bekololek.pluginfactory.plan.dto.ConfigEntry;
import com.bekololek.pluginfactory.plan.dto.DependencySpec;
import com.bekololek.pluginfactory.plan.dto.EventListenerSpec;
import com.bekololek.pluginfactory.plan.dto.BudgetFeasibilityDto;
import com.bekololek.pluginfactory.plan.dto.PlanDocumentDto;
import com.bekololek.pluginfactory.plan.dto.PlanRevisionRequest;
import com.bekololek.pluginfactory.plan.dto.TokenEstimate;
import com.bekololek.pluginfactory.plan.dto.ScopeValidationResultDto;
import com.bekololek.pluginfactory.plan.dto.TestScenario;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/builds")
@RequiredArgsConstructor
public class PlanController {

    private final PlanDocumentRepository planDocumentRepository;
    private final BuildSessionService buildSessionService;
    private final SubscriptionService subscriptionService;
    private final ComplexityEstimator complexityEstimator;
    private final ScopeGatingService scopeGatingService;
    private final TokenEstimateService tokenEstimateService;
    private final PlanGenerationAgent planGenerationAgent;
    private final ChatMessageService chatMessageService;
    private final BuildLauncher buildLauncher;
    private final ObjectMapper objectMapper;

    @GetMapping("/{sessionId}/plan")
    public ResponseEntity<PlanDocumentDto> getPlan(@PathVariable UUID sessionId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(sessionId, userId); // validate ownership

        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Plan not found"));

        return ResponseEntity.ok(toDto(plan, estimateFor(plan, userId)));
    }

    @PostMapping("/{sessionId}/plan/approve")
    public ResponseEntity<?> approvePlan(@PathVariable UUID sessionId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(sessionId, userId); // validate ownership

        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Plan not found"));

        Tier tier = subscriptionService.getTierForUser(userId);
        ScopeGatingService.ScopeValidationResult result = scopeGatingService.validateScope(plan, tier);

        if (result.status() != ScopeGatingService.ScopeStatus.PASS) {
            ScopeValidationResultDto validationDto = new ScopeValidationResultDto(
                    result.status().name(), result.violations());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(validationDto);
        }

        // Token-budget feasibility: don't start a build that can't finish within
        // the user's remaining monthly budget (incl. testing + realistic retries).
        int remaining = subscriptionService.getRemainingMonthlyTokens(userId);
        TokenEstimate estimate = tokenEstimateService.estimate(plan, tier, remaining);
        if ("EXCEEDS".equals(estimate.verdict())) {
            String msg = "This plugin needs about %,dk tokens to build (including testing and retries), "
                    .formatted(estimate.estimatedTotalTokens() / 1000)
                    + "but you have about %,dk left this month. Simplify the plan or upgrade your plan."
                    .formatted(remaining / 1000);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new BudgetFeasibilityDto(estimate, msg));
        }

        // APPROVED is the "queued for build" marker; the pipeline itself flips
        // it to BUILDING when the worker picks it up. Without this kickoff call
        // the session would stay in APPROVED forever.
        buildSessionService.updateStatus(sessionId, BuildStatus.APPROVED);
        buildLauncher.startBuild(sessionId, "INITIAL");
        return ResponseEntity.accepted().body(toDto(plan, estimate));
    }

    @PostMapping("/{sessionId}/plan/revise")
    public ResponseEntity<?> revisePlan(@PathVariable UUID sessionId,
                                        @Valid @RequestBody PlanRevisionRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(sessionId, userId); // validate ownership

        // Re-enter PLANNING phase
        buildSessionService.updateStatus(sessionId, BuildStatus.PLANNING);
        buildSessionService.updatePhase(sessionId, BuildPhase.PLAN_GENERATION);

        // Add feedback as user message
        chatMessageService.addMessage(sessionId, "user", request.feedback(), null, 0);

        try {
            // Re-trigger plan generation (forced tool-use, structured output).
            PlanDocument revised = planGenerationAgent.generatePlan(sessionId);
            // Post a visible acknowledgment so the conversation reflects the
            // revision — without this the chat shows the user's request with no
            // reply, even though the plan was updated.
            chatMessageService.addMessage(sessionId, "assistant",
                    PlanGenerationAgent.buildAcknowledgment(revised), null, 0);
            return ResponseEntity.ok(toDto(revised));
        } catch (RuntimeException e) {
            // Tell the user (in chat) what happened and leave the existing plan
            // intact and approvable instead of stranding the session mid-phase.
            chatMessageService.addMessage(sessionId, "assistant",
                    "I couldn't update the plan just now. Please send your change again "
                            + "in a moment and I'll retry — your current plan is unchanged.",
                    null, 0);
            buildSessionService.updateStatus(sessionId, BuildStatus.PLANNING);
            buildSessionService.updatePhase(sessionId, BuildPhase.PLAN_REVIEW);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message",
                            "Plan revision is temporarily unavailable. Please try again."));
        }
    }

    private TokenEstimate estimateFor(PlanDocument plan, UUID userId) {
        Tier tier = subscriptionService.getTierForUser(userId);
        int remaining = subscriptionService.getRemainingMonthlyTokens(userId);
        return tokenEstimateService.estimate(plan, tier, remaining);
    }

    private PlanDocumentDto toDto(PlanDocument plan) {
        return toDto(plan, null);
    }

    private PlanDocumentDto toDto(PlanDocument plan, TokenEstimate estimate) {
        return new PlanDocumentDto(
                plan.getId(),
                plan.getSessionId(),
                plan.getPluginName(),
                plan.getDescription(),
                plan.getMinecraftVersion(),
                plan.getServerType(),
                parseJson(plan.getCommands(), new TypeReference<List<CommandSpec>>() {}),
                parseJson(plan.getEventListeners(), new TypeReference<List<EventListenerSpec>>() {}),
                parseJson(plan.getConfigSchema(), new TypeReference<List<ConfigEntry>>() {}),
                parseJson(plan.getDependencies(), new TypeReference<List<DependencySpec>>() {}),
                parseJson(plan.getTestScenarios(), new TypeReference<List<TestScenario>>() {}),
                plan.getEstimatedLoc(),
                plan.getComplexityScore(),
                plan.getVersion(),
                plan.getCreatedAt(),
                plan.getViabilityStatus(),
                parseJson(plan.getSetupSteps(), new TypeReference<List<String>>() {}),
                parseJson(plan.getAutoHandled(), new TypeReference<List<String>>() {}),
                estimate
        );
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        try {
            T result = objectMapper.readValue(json, typeRef);
            if (result != null) {
                return result;
            }
        } catch (Exception ignored) {
            // fall through to empty list
        }
        @SuppressWarnings("unchecked")
        T empty = (T) Collections.emptyList();
        return empty;
    }
}
