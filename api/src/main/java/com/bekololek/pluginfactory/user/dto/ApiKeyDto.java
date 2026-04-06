package com.bekololek.pluginfactory.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyDto(
        UUID id,
        String name,
        String lastFour,
        Instant createdAt
) {}
