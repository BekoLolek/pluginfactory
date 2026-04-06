package com.bekololek.pluginfactory.plan.dto;

public record DependencySpec(
        String groupId,
        String artifactId,
        String version,
        String reason
) {
}
