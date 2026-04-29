package com.bekololek.pluginfactory.admin;

import com.bekololek.pluginfactory.admin.dto.RetriggerResponse;
import com.bekololek.pluginfactory.admin.dto.TokenRefundResponse;
import com.bekololek.pluginfactory.build.BuildIteration;
import com.bekololek.pluginfactory.build.BuildIterationRepository;
import com.bekololek.pluginfactory.build.BuildPhase;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionRepository;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.build.FailedBuildRecoveryService;
import com.bekololek.pluginfactory.build.TokenBudget;
import com.bekololek.pluginfactory.build.TokenBudgetRepository;
import com.bekololek.pluginfactory.build.TokenBudgetService;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for the admin recovery actions on {@link AdminService}:
 * {@code refundTokens} and {@code retriggerFailedBuild}. Other dependencies
 * of AdminService are left as default null mocks; the methods under test
 * never touch them.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceRecoveryTest {

    @Mock
    private BuildSessionRepository buildSessionRepository;

    @Mock
    private BuildIterationRepository buildIterationRepository;

    @Mock
    private TokenBudgetRepository tokenBudgetRepository;

    @Mock
    private FailedBuildRecoveryService failedBuildRecoveryService;

    @Mock
    private PlanDocumentRepository planDocumentRepository;

    @Mock
    private BuildSessionService buildSessionService;

    @Mock
    private TokenBudgetService tokenBudgetService;

    @InjectMocks
    private AdminService adminService;

    // ── refundTokens ──────────────────────────────────────────────────────

    @Test
    void refundTokens_returnsAmountAndIsNotMarkedAlreadyRefundedFirstTime() {
        UUID sessionId = UUID.randomUUID();
        TokenBudget before = budget(sessionId, /* refundedAt */ null, /* refundedAmount */ null);
        TokenBudget after = budget(sessionId, Instant.now(), 1_250);

        when(buildSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE)));
        when(tokenBudgetRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(tokenBudgetService.refundSessionTokens(sessionId)).thenReturn(1_250);

        TokenRefundResponse response = adminService.refundTokens(sessionId);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.refundedAmount()).isEqualTo(1_250);
        assertThat(response.alreadyRefunded()).isFalse();
        assertThat(response.refundedAt()).isNotNull();
    }

    @Test
    void refundTokens_marksAlreadyRefundedWhenBudgetWasPreviouslyRefunded() {
        UUID sessionId = UUID.randomUUID();
        Instant priorRefund = Instant.now().minusSeconds(3600);
        TokenBudget previouslyRefunded = budget(sessionId, priorRefund, 1_250);

        when(buildSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE)));
        when(tokenBudgetRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(previouslyRefunded));
        when(tokenBudgetService.refundSessionTokens(sessionId)).thenReturn(1_250);

        TokenRefundResponse response = adminService.refundTokens(sessionId);

        assertThat(response.alreadyRefunded()).isTrue();
        assertThat(response.refundedAmount()).isEqualTo(1_250);
        assertThat(response.refundedAt()).isEqualTo(priorRefund);
    }

    @Test
    void refundTokens_throwsWhenSessionMissing() {
        UUID sessionId = UUID.randomUUID();
        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.refundTokens(sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Build session not found");
        verify(tokenBudgetService, never()).refundSessionTokens(any());
    }

    // ── retriggerFailedBuild ──────────────────────────────────────────────

    @Test
    void retrigger_pipelineRestartsWhenPlanAndIterationsExist() {
        UUID sessionId = UUID.randomUUID();
        BuildSession s = session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(s));
        when(planDocumentRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(new PlanDocument()));
        when(buildIterationRepository.countBySessionId(sessionId)).thenReturn(2L);

        BuildIteration iteration = new BuildIteration();
        iteration.setIterationNumber(3);
        when(failedBuildRecoveryService.adminRecover(sessionId)).thenReturn(iteration);

        RetriggerResponse response = adminService.retriggerFailedBuild(sessionId);

        assertThat(response.action()).isEqualTo("PIPELINE_RESTARTED");
        assertThat(response.status()).isEqualTo(BuildStatus.BUILDING.name());
        assertThat(response.currentPhase()).isEqualTo(BuildPhase.IMPLEMENTATION.name());
        assertThat(response.iterationNumber()).isEqualTo(3);
        verify(failedBuildRecoveryService).adminRecover(sessionId);
        // Pipeline path must NOT independently flip status — the recovery
        // service handles that internally.
        verify(buildSessionService, never()).updateStatus(any(), any());
    }

    @Test
    void retrigger_resetsToPlanReviewWhenPlanExistsButNoIterations() {
        // The exact PLAN_REVIEW reaper-bug case: plan was generated and
        // persisted, but the session was killed before any iteration was
        // created.
        UUID sessionId = UUID.randomUUID();
        BuildSession s = session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(s));
        when(planDocumentRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(new PlanDocument()));
        when(buildIterationRepository.countBySessionId(sessionId)).thenReturn(0L);

        RetriggerResponse response = adminService.retriggerFailedBuild(sessionId);

        assertThat(response.action()).isEqualTo("RESET_TO_PLAN_REVIEW");
        assertThat(response.status()).isEqualTo(BuildStatus.PLANNING.name());
        assertThat(response.currentPhase()).isEqualTo(BuildPhase.PLAN_REVIEW.name());
        assertThat(response.iterationNumber()).isNull();
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.PLANNING);
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.PLAN_REVIEW);
        verify(failedBuildRecoveryService, never()).adminRecover(any());
    }

    @Test
    void retrigger_resetsToClarificationWhenNoPlan() {
        UUID sessionId = UUID.randomUUID();
        BuildSession s = session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(s));
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        RetriggerResponse response = adminService.retriggerFailedBuild(sessionId);

        assertThat(response.action()).isEqualTo("RESET_TO_CLARIFICATION");
        assertThat(response.status()).isEqualTo(BuildStatus.CHATTING.name());
        assertThat(response.currentPhase()).isEqualTo(BuildPhase.CLARIFICATION.name());
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.CHATTING);
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.CLARIFICATION);
        verify(failedBuildRecoveryService, never()).adminRecover(any());
    }

    @Test
    void retrigger_clearsCompletedAtWhenResetting() {
        UUID sessionId = UUID.randomUUID();
        BuildSession s = session(sessionId, BuildStatus.FAILED, BuildPhase.IDLE);
        s.setCompletedAt(Instant.now());

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(s));
        when(planDocumentRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        adminService.retriggerFailedBuild(sessionId);

        assertThat(s.getCompletedAt()).isNull();
        verify(buildSessionRepository).save(s);
    }

    @Test
    void retrigger_rejectsNonFailedSessions() {
        UUID sessionId = UUID.randomUUID();
        BuildSession s = session(sessionId, BuildStatus.BUILDING, BuildPhase.IMPLEMENTATION);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> adminService.retriggerFailedBuild(sessionId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only FAILED sessions can be retriggered");
        verify(buildSessionService, never()).updateStatus(any(), any());
        verify(failedBuildRecoveryService, never()).adminRecover(any());
    }

    @Test
    void retrigger_throwsWhenSessionMissing() {
        UUID sessionId = UUID.randomUUID();
        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.retriggerFailedBuild(sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Build session not found");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static BuildSession session(UUID id, BuildStatus status, BuildPhase phase) {
        BuildSession s = new BuildSession();
        s.setId(id);
        s.setUserId(UUID.randomUUID());
        s.setStatus(status);
        s.setCurrentPhase(phase);
        return s;
    }

    private static TokenBudget budget(UUID sessionId, Instant refundedAt, Integer refundedAmount) {
        TokenBudget b = new TokenBudget();
        b.setSessionId(sessionId);
        b.setUserId(UUID.randomUUID());
        b.setAllocatedTokens(10_000);
        b.setConsumedTokens(1_250);
        b.setRefundedAt(refundedAt);
        b.setRefundedAmount(refundedAmount);
        return b;
    }
}
