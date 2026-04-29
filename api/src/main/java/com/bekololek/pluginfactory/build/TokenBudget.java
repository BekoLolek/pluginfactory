package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_budgets")
@Getter
@Setter
@NoArgsConstructor
public class TokenBudget extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "allocated_tokens", nullable = false)
    private int allocatedTokens;

    @Column(name = "consumed_tokens", nullable = false)
    private int consumedTokens;

    @Column(name = "planning_tokens", nullable = false)
    private int planningTokens;

    @Column(name = "implementation_tokens", nullable = false)
    private int implementationTokens;

    @Column(name = "testing_tokens", nullable = false)
    private int testingTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_status", nullable = false)
    private ThresholdStatus thresholdStatus = ThresholdStatus.NORMAL;

    /**
     * Set the moment an admin refunds this session's tokens. Acts as the
     * idempotency guard: a non-null value means {@code refundedAmount}
     * has already been credited back to the user's monthly pool, so
     * subsequent refund calls must be no-ops.
     */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    /**
     * Tokens credited back to the user when this session was refunded.
     * Equals {@code consumedTokens} at refund time. Null until refunded.
     */
    @Column(name = "refunded_amount")
    private Integer refundedAmount;
}
