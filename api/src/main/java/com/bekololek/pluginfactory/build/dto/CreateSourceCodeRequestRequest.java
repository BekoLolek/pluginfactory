package com.bekololek.pluginfactory.build.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSourceCodeRequestRequest(
        @NotNull UUID artifactId,
        @NotNull String licenseVersion
) {}
