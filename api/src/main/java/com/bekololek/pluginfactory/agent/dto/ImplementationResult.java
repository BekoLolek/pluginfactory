package com.bekololek.pluginfactory.agent.dto;

import java.util.Map;

public record ImplementationResult(
        Map<String, String> files,
        int tokensUsed
) {
}
