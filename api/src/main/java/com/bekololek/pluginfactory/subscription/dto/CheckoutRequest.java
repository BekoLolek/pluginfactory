package com.bekololek.pluginfactory.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull String tier
) {}
