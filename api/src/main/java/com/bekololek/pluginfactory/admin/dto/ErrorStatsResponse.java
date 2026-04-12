package com.bekololek.pluginfactory.admin.dto;

import java.util.List;
import java.util.Map;

public record ErrorStatsResponse(
        long totalErrors,
        Map<String, Long> byCategory,
        Map<String, Long> bySeverity,
        List<TopError> topErrors
) {
    public record TopError(String message, long count, String category) {}
}
