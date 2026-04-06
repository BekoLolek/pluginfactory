package com.bekololek.pluginfactory.agent.dto;

public record AgentResponse(
        String content,
        String model,
        int inputTokens,
        int outputTokens,
        String phaseTransition
) {
}
