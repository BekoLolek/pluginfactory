package com.bekololek.pluginfactory.plan.dto;

/**
 * Pre-build token-budget feasibility estimate for a plan: how many tokens the
 * build is expected to consume (implementation + testing + a realistic retry
 * reserve) versus the user's remaining monthly budget.
 *
 * @param verdict FITS (comfortable), TIGHT (fits but little headroom), or
 *                EXCEEDS (won't fit — approval is blocked).
 */
public record TokenEstimate(
        int perAttemptTokens,
        int expectedAttempts,
        int estimatedTotalTokens,
        int remainingBudget,
        String verdict
) {}
