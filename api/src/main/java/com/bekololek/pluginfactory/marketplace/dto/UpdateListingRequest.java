package com.bekololek.pluginfactory.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateListingRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @Size(max = 500) String shortDescription,
        @Size(max = 100) String category,
        @Min(0) Integer priceCents
) {
}
