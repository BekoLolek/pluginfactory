package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.team.SharedWorkspaceRepository;
import com.bekololek.pluginfactory.team.TeamService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildSessionServiceTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private TokenBudgetService tokenBudgetService;

    @Mock
    private BuildSessionRepository buildSessionRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private SharedWorkspaceRepository sharedWorkspaceRepository;

    @InjectMocks
    private BuildSessionService buildSessionService;

    @Test
    void createSession_happyPath() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.canBuild(userId)).thenReturn(true);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);
        when(buildSessionRepository.save(any(BuildSession.class))).thenAnswer(invocation -> {
            BuildSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        BuildSession result = buildSessionService.createSession(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(BuildStatus.CHATTING);
        assertThat(result.getCurrentPhase()).isEqualTo(BuildPhase.CLARIFICATION);

        verify(tokenBudgetService).allocateBudget(result.getId(), userId, Tier.PRO);
        verify(subscriptionService).incrementBuildCount(userId);
    }

    @Test
    void createSession_buildLimitReached() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.canBuild(userId)).thenReturn(false);

        assertThatThrownBy(() -> buildSessionService.createSession(userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Build limit reached");

        verify(buildSessionRepository, never()).save(any());
        verify(tokenBudgetService, never()).allocateBudget(any(), any(), any());
        verify(subscriptionService, never()).incrementBuildCount(any());
    }

    @Test
    void getSession_ownershipCheck() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> buildSessionService.getSession(sessionId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Build session not found");
    }

    @Test
    void getSession_success() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        BuildSession result = buildSessionService.getSession(sessionId, userId);
        assertThat(result.getId()).isEqualTo(sessionId);
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void getSession_wrongUser_noWorkspace() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(ownerId);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> buildSessionService.getSession(sessionId, otherUserId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelSession() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(BuildStatus.CHATTING);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(buildSessionRepository.save(any(BuildSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuildSession result = buildSessionService.cancelSession(sessionId, userId);

        assertThat(result.getStatus()).isEqualTo(BuildStatus.CANCELLED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(subscriptionService).refundBuildSlot(userId);
    }

    @Test
    void updateStatus_failedRefundsBuildSlotOnce() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(BuildStatus.BUILDING);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(buildSessionRepository.save(any(BuildSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        buildSessionService.updateStatus(sessionId, BuildStatus.FAILED);

        verify(subscriptionService).refundBuildSlot(userId);
    }

    @Test
    void updateStatus_completedDoesNotRefundBuildSlot() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(BuildStatus.BUILDING);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(buildSessionRepository.save(any(BuildSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        buildSessionService.updateStatus(sessionId, BuildStatus.COMPLETED);

        verify(subscriptionService, never()).refundBuildSlot(any());
    }

    @Test
    void updateStatus_failedToCancelledDoesNotDoubleRefund() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession session = new BuildSession();
        session.setId(sessionId);
        session.setUserId(userId);
        // Already terminal — a second transition must not refund again.
        session.setStatus(BuildStatus.FAILED);

        when(buildSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(buildSessionRepository.save(any(BuildSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        buildSessionService.updateStatus(sessionId, BuildStatus.CANCELLED);

        verify(subscriptionService, never()).refundBuildSlot(any());
    }
}
