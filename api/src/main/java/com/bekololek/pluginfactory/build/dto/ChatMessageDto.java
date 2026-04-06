package com.bekololek.pluginfactory.build.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        String role,
        String content,
        String modelUsed,
        int tokensConsumed,
        Instant createdAt
) {}
