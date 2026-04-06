package com.bekololek.pluginfactory.plan.dto;

public record ConfigEntry(
        String key,
        String type,
        String defaultValue,
        String description
) {
}
