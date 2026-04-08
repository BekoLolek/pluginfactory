package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.subscription.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ScopeGatingServiceTest {

    private final ScopeGatingService service = new ScopeGatingService();

    @Test
    void freeTier_withinLimits_passes() {
        // FREE tier: max 5 commands, 3 event listeners
        PlanDocument plan = createPlan(3, 2);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.FREE);

        assertEquals(ScopeGatingService.ScopeStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void freeTier_exactlyAtLimit_passes() {
        // FREE tier: max 5 commands, 3 event listeners
        PlanDocument plan = createPlan(5, 3);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.FREE);

        assertEquals(ScopeGatingService.ScopeStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void freeTier_exceedsCommands_fails() {
        // FREE tier: max 5 commands, 3 event listeners
        PlanDocument plan = createPlan(6, 2);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.FREE);

        assertEquals(ScopeGatingService.ScopeStatus.EXCEEDS_TIER, result.status());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).contains("Commands: 6 exceeds tier limit of 5"));
    }

    @Test
    void freeTier_exceedsEvents_fails() {
        // FREE tier: max 5 commands, 3 event listeners
        PlanDocument plan = createPlan(3, 5);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.FREE);

        assertEquals(ScopeGatingService.ScopeStatus.EXCEEDS_TIER, result.status());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).contains("Event listeners: 5 exceeds tier limit of 3"));
    }

    @Test
    void freeTier_exceedsBoth_twoViolations() {
        PlanDocument plan = createPlan(10, 10);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.FREE);

        assertEquals(ScopeGatingService.ScopeStatus.EXCEEDS_TIER, result.status());
        assertEquals(2, result.violations().size());
    }

    @Test
    void proTier_atLimits_passes() {
        // PRO tier: max 50 commands, 30 event listeners
        PlanDocument plan = createPlan(50, 30);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.PRO);

        assertEquals(ScopeGatingService.ScopeStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void basicTier_withinLimits_passes() {
        // BASIC tier: max 15 commands, 10 event listeners
        PlanDocument plan = createPlan(15, 10);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.BASIC);

        assertEquals(ScopeGatingService.ScopeStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void basicTier_exceedsCommands_fails() {
        // BASIC tier: max 15 commands, 10 event listeners
        PlanDocument plan = createPlan(16, 5);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.BASIC);

        assertEquals(ScopeGatingService.ScopeStatus.EXCEEDS_TIER, result.status());
        assertEquals(1, result.violations().size());
    }

    @Test
    void teamTier_atLimits_passes() {
        // TEAM tier: max 50 commands, 30 event listeners
        PlanDocument plan = createPlan(50, 30);

        ScopeGatingService.ScopeValidationResult result = service.validateScope(plan, Tier.TEAM);

        assertEquals(ScopeGatingService.ScopeStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    private PlanDocument createPlan(int commandCount, int eventCount) {
        PlanDocument plan = new PlanDocument();
        plan.setCommands(generateJsonArray(commandCount));
        plan.setEventListeners(generateJsonArray(eventCount));
        plan.setConfigSchema("[]");
        plan.setDependencies("[]");
        plan.setTestScenarios("[]");
        return plan;
    }

    private String generateJsonArray(int count) {
        if (count == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"item" + i + "\"}");
        }
        sb.append("]");
        return sb.toString();
    }
}
