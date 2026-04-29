package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of an admin token-refund call. {@code alreadyRefunded=true}
 * means the session had a prior refund recorded and this call was a
 * no-op; {@code refundedAmount} is the originally credited amount in
 * that case.
 */
public record TokenRefundResponse(
        UUID sessionId,
        int refundedAmount,
        boolean alreadyRefunded,
        Instant refundedAt
) {}
