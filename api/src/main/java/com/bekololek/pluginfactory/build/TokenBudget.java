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

import java.util.UUID;

@Entity
@Table(name = "token_budgets")
@Getter
@Setter
@NoArgsConstructor
public class TokenBudget extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

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
}
