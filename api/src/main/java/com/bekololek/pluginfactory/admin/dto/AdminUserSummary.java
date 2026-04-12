package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String email,
        String displayName,
        String status,
        String role,
        String tier,
        int buildsUsed,
        Instant lastActiveAt,
        Instant createdAt
) {}
