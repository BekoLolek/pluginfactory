package com.bekololek.pluginfactory.plan.dto;

import java.util.List;

public record CommandSpec(
        String name,
        String description,
        String permission,
        String usage,
        List<ArgumentSpec> arguments
) {
}
