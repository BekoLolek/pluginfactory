package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryPolicyTest {

    @Mock
    private TokenBudgetService tokenBudgetService;

    @InjectMocks
    private RetryPolicy retryPolicy;

    @Test
    void shouldRetry_securityCategory_neverRetries() {
        UUID sessionId = UUID.randomUUID();
        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.SECURITY, 0))
                .isFalse();
    }

    @Test
    void shouldRetry_structuralCategory_neverRetries() {
        UUID sessionId = UUID.randomUUID();
        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.STRUCTURAL, 0))
                .isFalse();
    }

    @Test
    void shouldRetry_recoverableWithBudget_retries() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = new TokenBudget();
        budget.setAllocatedTokens(10000);
        budget.setConsumedTokens(1000); // 90% remaining > 20%
        when(tokenBudgetService.getRemainingBudget(sessionId)).thenReturn(budget);

        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.RECOVERABLE, 0))
                .isTrue();
    }

    @Test
    void shouldRetry_recoverableAtMaxRetries_doesNotRetry() {
        UUID sessionId = UUID.randomUUID();
        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.RECOVERABLE, 3))
                .isFalse();
    }

    @Test
    void shouldRetry_recoverableWithLowBudget_doesNotRetry() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = new TokenBudget();
        budget.setAllocatedTokens(10000);
        budget.setConsumedTokens(8500); // 15% remaining < 20%
        when(tokenBudgetService.getRemainingBudget(sessionId)).thenReturn(budget);

        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.RECOVERABLE, 0))
                .isFalse();
    }

    @Test
    void shouldRetry_recoverableWithExactly20PercentBudget_doesNotRetry() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget budget = new TokenBudget();
        budget.setAllocatedTokens(10000);
        budget.setConsumedTokens(8000); // exactly 20% remaining, not > 20%
        when(tokenBudgetService.getRemainingBudget(sessionId)).thenReturn(budget);

        assertThat(retryPolicy.shouldRetry(sessionId, ErrorClassifier.ErrorCategory.RECOVERABLE, 0))
                .isFalse();
    }
}
