package com.bekololek.pluginfactory.plan.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanDocumentDto(
        UUID id,
        UUID sessionId,
        String pluginName,
        String description,
        String minecraftVersion,
        String serverType,
        List<CommandSpec> commands,
        List<EventListenerSpec> eventListeners,
        List<ConfigEntry> configSchema,
        List<DependencySpec> dependencies,
        List<TestScenario> testScenarios,
        Integer estimatedLoc,
        Integer complexityScore,
        int version,
        Instant createdAt,
        String viabilityStatus,
        java.util.List<String> setupSteps,
        java.util.List<String> autoHandled,
        TokenEstimate estimate
) {
}
