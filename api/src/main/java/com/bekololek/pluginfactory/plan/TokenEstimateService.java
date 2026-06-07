package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.plan.dto.TokenEstimate;
import com.bekololek.pluginfactory.subscription.Tier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Estimates the token cost of building a plan — implementation + testing + a
 * realistic retry reserve — and compares it to the user's remaining monthly
 * budget, so approval can be blocked before compute is spent on a build that
 * can't finish.
 *
 * <p>Calibrated from real builds: per implementation attempt is roughly flat
 * (~12–23k tokens), only weakly tied to estimated LOC, so we scale off the
 * deterministic {@code complexityScore}. Retries dominate total cost, so the
 * attempt count is the most important term. All constants are config-tunable
 * ({@code estimate.*}) so they can be re-fit from estimate-vs-actual data.
 */
@Service
public class TokenEstimateService {

    private final int implBase;
    private final int perComplexity;
    private final int testWriterTokens;
    private final int complexityPerRetry;
    private final int maxAutoRetries;
    private final double fitsRatio;

    public TokenEstimateService(
            @Value("${estimate.impl-base:10000}") int implBase,
            @Value("${estimate.per-complexity:25}") int perComplexity,
            @Value("${estimate.test-writer-tokens:3000}") int testWriterTokens,
            @Value("${estimate.complexity-per-retry:300}") int complexityPerRetry,
            @Value("${estimate.max-auto-retries:4}") int maxAutoRetries,
            @Value("${estimate.fits-ratio:0.75}") double fitsRatio) {
        this.implBase = implBase;
        this.perComplexity = perComplexity;
        this.testWriterTokens = testWriterTokens;
        this.complexityPerRetry = complexityPerRetry;
        this.maxAutoRetries = maxAutoRetries;
        this.fitsRatio = fitsRatio;
    }

    public TokenEstimate estimate(PlanDocument plan, Tier tier, int remainingBudget) {
        int complexity = plan.getComplexityScore() != null ? plan.getComplexityScore() : 0;

        int perAttempt = implBase + complexity * perComplexity;
        if (tier != Tier.FREE) {
            // Basic+ runs a functional test, which includes one test-writer LLM call.
            perAttempt += testWriterTokens;
        }

        // Realistic retry reserve: ~2 retries baseline, scaling with complexity,
        // capped at the pipeline's hard auto-fix limit.
        int reserve = Math.min(maxAutoRetries, 2 + complexity / complexityPerRetry);
        int attempts = 1 + reserve;
        int total = perAttempt * attempts;

        String verdict;
        if (total <= remainingBudget * fitsRatio) {
            verdict = "FITS";
        } else if (total <= remainingBudget) {
            verdict = "TIGHT";
        } else {
            verdict = "EXCEEDS";
        }
        return new TokenEstimate(perAttempt, attempts, total, remainingBudget, verdict);
    }
}
