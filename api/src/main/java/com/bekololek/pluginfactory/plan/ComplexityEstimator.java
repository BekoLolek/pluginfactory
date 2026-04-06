package com.bekololek.pluginfactory.plan;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ComplexityEstimator {

    public ComplexityResult estimateComplexity(PlanDocument plan) {
        int score = 0;
        Map<String, Integer> breakdown = new LinkedHashMap<>();

        int commandScore = plan.getCommandCount() * 10;
        breakdown.put("commands", commandScore);
        score += commandScore;

        int eventScore = plan.getEventListenerCount() * 15;
        breakdown.put("eventListeners", eventScore);
        score += eventScore;

        int configScore = plan.getConfigEntryCount() * 3;
        breakdown.put("configOptions", configScore);
        score += configScore;

        int depScore = plan.getDependencyCount() * 25;
        breakdown.put("dependencies", depScore);
        score += depScore;

        int locScore = (int) (plan.getEstimatedLoc() * 0.1);
        breakdown.put("estimatedLOC", locScore);
        score += locScore;

        return new ComplexityResult(score, breakdown);
    }

    public record ComplexityResult(int totalScore, Map<String, Integer> breakdown) {
    }
}
