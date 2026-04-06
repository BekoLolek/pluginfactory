package com.bekololek.pluginfactory.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateListingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @Size(max = 500) String shortDescription,
        @NotBlank @Size(max = 100) String category,
        @Size(max = 20) String minecraftVersion,
        @Min(0) int priceCents,
        @NotNull UUID artifactId
) {
}
