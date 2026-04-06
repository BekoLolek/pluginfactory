package com.bekololek.pluginfactory.plan.dto;

import java.util.List;

public record EventListenerSpec(
        String event,
        String priority,
        String description,
        List<String> conditions
) {
}
