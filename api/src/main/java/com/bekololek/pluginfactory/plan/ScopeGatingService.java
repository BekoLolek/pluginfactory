package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.subscription.Tier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScopeGatingService {

    public ScopeValidationResult validateScope(PlanDocument plan, Tier tier) {
        List<String> violations = new ArrayList<>();

        if (!tier.isUnlimited(tier.getMaxCommands())
                && plan.getCommandCount() > tier.getMaxCommands()) {
            violations.add("Commands: %d exceeds tier limit of %d"
                    .formatted(plan.getCommandCount(), tier.getMaxCommands()));
        }

        if (!tier.isUnlimited(tier.getMaxEventListeners())
                && plan.getEventListenerCount() > tier.getMaxEventListeners()) {
            violations.add("Event listeners: %d exceeds tier limit of %d"
                    .formatted(plan.getEventListenerCount(), tier.getMaxEventListeners()));
        }

        ScopeStatus status = violations.isEmpty() ? ScopeStatus.PASS : ScopeStatus.EXCEEDS_TIER;
        return new ScopeValidationResult(status, violations);
    }

    public enum ScopeStatus {
        PASS, EXCEEDS_TIER, REQUIRES_SIMPLIFICATION
    }

    public record ScopeValidationResult(ScopeStatus status, List<String> violations) {
    }
}
