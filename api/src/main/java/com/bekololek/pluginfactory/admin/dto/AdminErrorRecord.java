package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminErrorRecord(
        UUID id,
        UUID sessionId,
        UUID iterationId,
        Integer iterationNumber,
        UUID userId,
        String userEmail,
        String category,
        String severity,
        String message,
        String stackTrace,
        int retryCount,
        Instant createdAt
) {}
