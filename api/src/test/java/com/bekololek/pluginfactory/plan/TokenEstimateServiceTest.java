package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.plan.dto.TokenEstimate;
import com.bekololek.pluginfactory.subscription.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimateServiceTest {

    // Defaults matching application config: implBase=10000, perComplexity=25,
    // testWriter=3000, complexityPerRetry=300, maxAutoRetries=4, fitsRatio=0.75
    private final TokenEstimateService service =
            new TokenEstimateService(10_000, 25, 3_000, 300, 4, 0.75);

    private PlanDocument plan(int complexity) {
        PlanDocument p = new PlanDocument();
        p.setComplexityScore(complexity);
        return p;
    }

    @Test
    void fitsWhenBudgetAmple() {
        // cplx 100, FREE: perAttempt=12500, reserve=2, attempts=3, total=37500
        TokenEstimate e = service.estimate(plan(100), Tier.FREE, 200_000);
        assertThat(e.perAttemptTokens()).isEqualTo(12_500);
        assertThat(e.expectedAttempts()).isEqualTo(3);
        assertThat(e.estimatedTotalTokens()).isEqualTo(37_500);
        assertThat(e.verdict()).isEqualTo("FITS");
    }

    @Test
    void basicTierAddsTestWriterCost() {
        TokenEstimate free = service.estimate(plan(100), Tier.FREE, 1_000_000);
        TokenEstimate basic = service.estimate(plan(100), Tier.BASIC, 1_000_000);
        assertThat(basic.perAttemptTokens()).isEqualTo(free.perAttemptTokens() + 3_000);
    }

    @Test
    void exceedsWhenBudgetTooSmall() {
        TokenEstimate e = service.estimate(plan(100), Tier.FREE, 10_000);
        assertThat(e.verdict()).isEqualTo("EXCEEDS");
    }

    @Test
    void tightBetweenThresholds() {
        // total=37500; remaining=40000 → > 0.75*40000(30000) but ≤ 40000 → TIGHT
        TokenEstimate e = service.estimate(plan(100), Tier.FREE, 40_000);
        assertThat(e.verdict()).isEqualTo("TIGHT");
    }

    @Test
    void complexityScalesCostAndReserveIsCapped() {
        TokenEstimate simple = service.estimate(plan(50), Tier.FREE, 1_000_000);
        TokenEstimate complex = service.estimate(plan(900), Tier.FREE, 1_000_000);
        assertThat(complex.perAttemptTokens()).isGreaterThan(simple.perAttemptTokens());
        assertThat(complex.expectedAttempts()).isEqualTo(5); // 1 + min(4, 2+3)
    }
}
