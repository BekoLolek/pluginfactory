package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminTeamSummary(
        UUID id,
        String name,
        String ownerEmail,
        int memberCount,
        int workspaceCount,
        long buildCount,
        Instant createdAt
) {}
