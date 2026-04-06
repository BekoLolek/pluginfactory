package com.bekololek.pluginfactory.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String discordId,
        String status,
        String tier,
        Instant createdAt
) {}
