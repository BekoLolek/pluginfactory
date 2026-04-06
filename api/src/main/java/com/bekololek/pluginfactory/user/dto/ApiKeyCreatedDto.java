package com.bekololek.pluginfactory.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedDto(
        UUID id,
        String name,
        String key,
        String lastFour,
        Instant createdAt
) {}
