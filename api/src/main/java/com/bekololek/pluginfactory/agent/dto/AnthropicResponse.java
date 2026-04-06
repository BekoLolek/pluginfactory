package com.bekololek.pluginfactory.agent.dto;

public record AnthropicResponse(
        String content,
        String model,
        int inputTokens,
        int outputTokens
) {
}
