package com.bekololek.pluginfactory.admin.dto;

import java.util.List;
import java.util.Map;

public record BuildStatsResponse(
        long totalBuilds,
        Map<String, Long> byStatus,
        Map<String, Long> byTier,
        double avgComplexityScore,
        double avgTokensPerBuild,
        double avgIterationsPerBuild,
        List<DayStats> timeline
) {
    public record DayStats(String date, long total, long completed, long failed) {}
}
