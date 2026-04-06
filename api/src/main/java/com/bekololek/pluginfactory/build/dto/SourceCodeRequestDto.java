package com.bekololek.pluginfactory.build.dto;

import java.time.Instant;
import java.util.UUID;

public record SourceCodeRequestDto(
        UUID id,
        UUID userId,
        UUID artifactId,
        String status,
        String licenseVersion,
        Instant licenseAcceptedAt,
        UUID watermarkId,
        Instant requestedAt,
        Instant fulfilledAt
) {}
