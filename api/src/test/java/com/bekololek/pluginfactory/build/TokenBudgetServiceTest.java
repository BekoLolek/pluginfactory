package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void refundSessionTokens_creditsUserAndStampsRefund() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 10_000, 1_250);
        budget.setUserId(userId);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        int refunded = tokenBudgetService.refundSessionTokens(sessionId);

        assertThat(refunded).isEqualTo(1_250);
        verify(subscriptionService).refundTokens(userId, 1_250);
        ArgumentCaptor<TokenBudget> captor = ArgumentCaptor.forClass(TokenBudget.class);
        verify(tokenBudgetRepository).save(captor.capture());
        TokenBudget saved = captor.getValue();
        assertThat(saved.getRefundedAt()).isNotNull();
        assertThat(saved.getRefundedAmount()).isEqualTo(1_250);
    }

    @Test
    void refundSessionTokens_isIdempotent() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 10_000, 1_250);
        budget.setUserId(userId);
        // Simulate prior refund.
        budget.setRefundedAt(Instant.now().minusSeconds(60));
        budget.setRefundedAmount(1_250);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));

        int refunded = tokenBudgetService.refundSessionTokens(sessionId);

        // Nothing new consumed since the prior refund → 0 credited (incremental).
        assertThat(refunded).isEqualTo(0);
        // No second debit on the user; no state change written.
        verify(subscriptionService, never()).refundTokens(any(), anyInt());
        verify(tokenBudgetRepository, never()).save(any(TokenBudget.class));
    }

    @Test
    void refundSessionTokens_zeroConsumedStillStampsRefund() {
        // A session that died before any LLM call still gets the refund
        // marker so a future call doesn't keep "trying" to refund a zero.
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 10_000, 0);
        budget.setUserId(userId);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        int refunded = tokenBudgetService.refundSessionTokens(sessionId);

        assertThat(refunded).isEqualTo(0);
        // No debit because there's nothing to refund — but the budget row
        // is still stamped so this becomes a no-op next time.
        verify(subscriptionService, never()).refundTokens(any(), anyInt());
        ArgumentCaptor<TokenBudget> captor = ArgumentCaptor.forClass(TokenBudget.class);
        verify(tokenBudgetRepository).save(captor.capture());
        assertThat(captor.getValue().getRefundedAt()).isNotNull();
        assertThat(captor.getValue().getRefundedAmount()).isEqualTo(0);
    }

    @Test
    void refundSessionTokens_skipsSubscriptionCallWhenUserIdNull() {
        // Some legacy budgets pre-V11 have null userId; refund should still
        // stamp the row but must not crash trying to credit a nonexistent
        // subscription.
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = createBudget(sessionId, 10_000, 500);
        budget.setUserId(null);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));
        when(tokenBudgetRepository.save(any(TokenBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        int refunded = tokenBudgetService.refundSessionTokens(sessionId);

        assertThat(refunded).isEqualTo(500);
        verify(subscriptionService, never()).refundTokens(any(), eq(500));
    }

    @Test
    void refundSessionTokens_throwsWhenBudgetMissing() {
        UUID sessionId = UUID.randomUUID();
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenBudgetService.refundSessionTokens(sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token budget not found");
    }

    @Test
    void refundSessionTokens_topsUpAfterMoreConsumed() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        // An admin refunded 15,998 mid-outage; the build then ran and consumed
        // up to 87,221 before failing. A second refund should credit the delta.
        TokenBudget budget = createBudget(sessionId, 200_000, 87_221);
        budget.setUserId(userId);
        budget.setRefundedAt(Instant.now().minusSeconds(120));
        budget.setRefundedAmount(15_998);
        when(tokenBudgetRepository.findBySessionId(sessionId)).thenReturn(Optional.of(budget));

        int refunded = tokenBudgetService.refundSessionTokens(sessionId);

        assertThat(refunded).isEqualTo(71_223); // 87_221 - 15_998
        verify(subscriptionService).refundTokens(userId, 71_223);
        ArgumentCaptor<TokenBudget> captor = ArgumentCaptor.forClass(TokenBudget.class);
        verify(tokenBudgetRepository).save(captor.capture());
        assertThat(captor.getValue().getRefundedAmount()).isEqualTo(87_221);
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
