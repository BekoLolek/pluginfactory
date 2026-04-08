package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBudgetServiceTest {

    @Mock
    private TokenBudgetRepository tokenBudgetRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private TokenBudgetService tokenBudgetService;

    @Test
    void allocateBudget() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionService.getRemainingMonthlyTokens(userId)).thenReturn(Tier.PRO.getTokenBudget());

        TokenBudget result = tokenBudgetService.allocateBudget(sessionId, userId, Tier.PRO);

        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAllocatedTokens()).isEqualTo(Tier.PRO.getTokenBudget());
        assertThat(result.getConsumedTokens()).isEqualTo(0);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.NORMAL);
    }

    @Test
    void consumeTokens_normalThreshold() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 79% consumed -> NORMAL
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "planning", 790);

        assertThat(result.getConsumedTokens()).isEqualTo(790);
        assertThat(result.getPlanningTokens()).isEqualTo(790);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.NORMAL);
    }

    @Test
    void consumeTokens_warningThresholdAt80Percent() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Exactly 80% -> WARNING
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "implementation", 800);

        assertThat(result.getConsumedTokens()).isEqualTo(800);
        assertThat(result.getImplementationTokens()).isEqualTo(800);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.WARNING);
    }

    @Test
    void consumeTokens_warningThresholdAt94Percent() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 94% -> still WARNING
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "implementation", 940);

        assertThat(result.getConsumedTokens()).isEqualTo(940);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.WARNING);
    }

    @Test
    void consumeTokens_criticalThresholdAt95Percent() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Exactly 95% -> CRITICAL
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "testing", 950);

        assertThat(result.getConsumedTokens()).isEqualTo(950);
        assertThat(result.getTestingTokens()).isEqualTo(950);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.CRITICAL);
    }

    @Test
    void consumeTokens_criticalThresholdAt99Percent() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 99% -> still CRITICAL
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "testing", 990);

        assertThat(result.getConsumedTokens()).isEqualTo(990);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.CRITICAL);
    }

    @Test
    void consumeTokens_exhaustedThresholdAt100Percent() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 0);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Exactly 100% -> EXHAUSTED
        TokenBudget result = tokenBudgetService.consumeTokens(sessionId, "planning", 1000);

        assertThat(result.getConsumedTokens()).isEqualTo(1000);
        assertThat(result.getThresholdStatus()).isEqualTo(ThresholdStatus.EXHAUSTED);
    }

    @Test
    void hasBudget_withinLimit() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 500);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));

        assertThat(tokenBudgetService.hasBudget(sessionId, 500)).isTrue();
        assertThat(tokenBudgetService.hasBudget(sessionId, 499)).isTrue();
    }

    @Test
    void hasBudget_overLimit() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 1000, 500);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));

        assertThat(tokenBudgetService.hasBudget(sessionId, 501)).isFalse();
    }

    @Test
    void getRemainingBudget_notFound() {
        UUID sessionId = UUID.randomUUID();
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenBudgetService.getRemainingBudget(sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token budget not found");
    }

    private TokenBudget createBudget(UUID sessionId, int allocated, int consumed) {
        TokenBudget budget = new TokenBudget();
        budget.setSessionId(sessionId);
        budget.setAllocatedTokens(allocated);
        budget.setConsumedTokens(consumed);
        budget.setPlanningTokens(0);
        budget.setImplementationTokens(0);
        budget.setTestingTokens(0);
        budget.setThresholdStatus(ThresholdStatus.NORMAL);
        return budget;
    }
}
