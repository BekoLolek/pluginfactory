package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.container.ContainerSession;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildRecoveryServiceTest {

    @Mock
    private BuildSessionRepository buildSessionRepository;

    @Mock
    private BuildIterationRepository buildIterationRepository;

    @Mock
    private BuildErrorRepository buildErrorRepository;

    @Mock
    private ContainerSessionRepository containerSessionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private BuildRecoveryService buildRecoveryService;

    @Test
    void reapStaleBuilds_excludesPlanReviewFromRepositoryQuery() {
        // The whole point of the fix: the periodic reaper must ask the
        // repository to skip PLAN_REVIEW sessions, since those are
        // user-driven (waiting for approval) and have no heartbeat.
        when(buildSessionRepository.findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
                any(), any(), any())).thenReturn(List.of());

        buildRecoveryService.reapStaleBuilds();

        verify(buildSessionRepository).findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
                any(), eq(BuildPhase.PLAN_REVIEW), any());
    }

    @Test
    void recoverInterruptedBuilds_excludesPlanReviewFromRepositoryQuery() {
        // Same exclusion at startup. A session left in PLAN_REVIEW when
        // the JVM died is fine to resume — the user can still approve
        // the plan after the server comes back up.
        when(buildSessionRepository.findByStatusInAndCurrentPhaseNot(any(), any()))
                .thenReturn(List.of());
        when(containerSessionRepository.findByReleasedAtIsNull()).thenReturn(List.of());

        buildRecoveryService.recoverInterruptedBuilds();

        verify(buildSessionRepository).findByStatusInAndCurrentPhaseNot(
                any(), eq(BuildPhase.PLAN_REVIEW));
    }

    @Test
    void reapStaleBuilds_marksReturnedSessionsFailed() {
        // Regression: when the repo returns a stale session (any phase
        // *other* than PLAN_REVIEW — e.g. an IMPLEMENTATION worker that
        // wedged), the reaper still does its job: status -> FAILED,
        // phase -> IDLE, BuildError row written, slot refunded.
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession stale = newSession(sessionId, userId, BuildStatus.BUILDING, BuildPhase.IMPLEMENTATION);

        when(buildSessionRepository.findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
                any(), any(), any())).thenReturn(List.of(stale));
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of());
        when(containerSessionRepository.findByReleasedAtIsNull()).thenReturn(List.of());

        buildRecoveryService.reapStaleBuilds();

        ArgumentCaptor<BuildSession> sessionCaptor = ArgumentCaptor.forClass(BuildSession.class);
        verify(buildSessionRepository).save(sessionCaptor.capture());
        BuildSession saved = sessionCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BuildStatus.FAILED);
        assertThat(saved.getCurrentPhase()).isEqualTo(BuildPhase.IDLE);
        assertThat(saved.getCompletedAt()).isNotNull();

        ArgumentCaptor<BuildError> errorCaptor = ArgumentCaptor.forClass(BuildError.class);
        verify(buildErrorRepository).save(errorCaptor.capture());
        BuildError error = errorCaptor.getValue();
        assertThat(error.getSessionId()).isEqualTo(sessionId);
        assertThat(error.getCategory()).isEqualTo("SYSTEM");
        assertThat(error.getSeverity()).isEqualTo("ERROR");
        assertThat(error.getMessage()).contains("no progress was reported");

        verify(subscriptionService).refundBuildSlot(userId);
    }

    @Test
    void reapStaleBuilds_doesNothingWhenNoSessionsReturned() {
        // The PLAN_REVIEW exclusion happens at the query level, so when
        // the only stale session is a user-waiting one the repo returns
        // empty and no recovery work fires. reapStaleBuilds short-circuits
        // before even touching the container repo, which is why we don't
        // stub it here.
        when(buildSessionRepository.findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
                any(), any(), any())).thenReturn(List.of());

        buildRecoveryService.reapStaleBuilds();

        verify(buildSessionRepository, never()).save(any(BuildSession.class));
        verify(buildErrorRepository, never()).save(any(BuildError.class));
        verify(subscriptionService, never()).refundBuildSlot(any());
        verify(containerSessionRepository, never()).findByReleasedAtIsNull();
    }

    @Test
    void recoverInterruptedBuilds_marksReturnedSessionsFailedWithRestartMessage() {
        // Regression: the startup hook still kills genuinely interrupted
        // sessions (the JVM died mid-build) and uses the restart-specific
        // message, not the stale one.
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BuildSession interrupted = newSession(sessionId, userId, BuildStatus.BUILDING, BuildPhase.COMPILATION);

        when(buildSessionRepository.findByStatusInAndCurrentPhaseNot(any(), any()))
                .thenReturn(List.of(interrupted));
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of());
        when(containerSessionRepository.findByReleasedAtIsNull()).thenReturn(List.of());

        buildRecoveryService.recoverInterruptedBuilds();

        ArgumentCaptor<BuildError> errorCaptor = ArgumentCaptor.forClass(BuildError.class);
        verify(buildErrorRepository).save(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getMessage())
                .contains("Plugin Factory server")
                .contains("restarted");
        verify(subscriptionService).refundBuildSlot(userId);
    }

    @Test
    void reapStaleBuilds_closesOpenContainerSessions() {
        // The container reap runs even when no build sessions were
        // killed — leaked container_sessions rows still need cleaning.
        ContainerSession openContainer = new ContainerSession();
        openContainer.setId(UUID.randomUUID());
        openContainer.setIterationId(UUID.randomUUID());
        openContainer.setContainerId("docker-id");
        openContainer.setContainerType("build");
        openContainer.setMemoryMb(512);
        openContainer.setCpuMillicores(500);

        when(buildSessionRepository.findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
                any(), any(), any()))
                .thenReturn(List.of(newSession(
                        UUID.randomUUID(), UUID.randomUUID(),
                        BuildStatus.BUILDING, BuildPhase.IMPLEMENTATION)));
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(any()))
                .thenReturn(List.of());
        when(containerSessionRepository.findByReleasedAtIsNull())
                .thenReturn(List.of(openContainer));

        buildRecoveryService.reapStaleBuilds();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContainerSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(containerSessionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(c -> assertThat(c.getReleasedAt()).isNotNull());
    }

    private static BuildSession newSession(UUID id, UUID userId, BuildStatus status, BuildPhase phase) {
        BuildSession session = new BuildSession();
        session.setId(id);
        session.setUserId(userId);
        session.setStatus(status);
        session.setCurrentPhase(phase);
        return session;
    }
}
