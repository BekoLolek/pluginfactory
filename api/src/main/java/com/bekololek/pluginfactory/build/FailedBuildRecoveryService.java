package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Re-runs the implementation/compilation pipeline for a FAILED session
 * using its existing plan and the most recent error as fix-context.
 * Two entry points share the same core: {@link #adminRecover} bypasses
 * ownership and retry limits; {@link #userRecover} enforces both.
 *
 * <p>Tokens already consumed stay charged. No new build slot is consumed
 * (the FAILED transition refunded one). Scope is locked: only the prior
 * plan + the failure message reach the implementer, never new user
 * input — recovery can only fix, never grow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FailedBuildRecoveryService {

    private static final String USER_RECOVERY_TRIGGER = "USER_RECOVERY";
    private static final String ADMIN_RECOVERY_TRIGGER = "ADMIN_RECOVERY";

    private final BuildSessionRepository buildSessionRepository;
    private final BuildErrorRepository buildErrorRepository;
    private final BuildIterationRepository buildIterationRepository;
    private final PlanDocumentRepository planDocumentRepository;
    private final ChatMessageService chatMessageService;
    private final BuildSessionService buildSessionService;
    private final BuildLauncher buildLauncher;

    @Transactional
    public BuildIteration adminRecover(UUID sessionId) {
        log.info("Admin recovery requested for session {}", sessionId);
        BuildSession session = loadAndValidate(sessionId, null);
        return doRecover(session, ADMIN_RECOVERY_TRIGGER);
    }

    @Transactional
    public BuildIteration userRecover(UUID sessionId, UUID userId, int maxUserRetries) {
        log.info("User recovery requested for session {} by user {}", sessionId, userId);
        BuildSession session = loadAndValidate(sessionId, userId);

        long priorUserRetries = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId).stream()
                .filter(it -> USER_RECOVERY_TRIGGER.equals(it.getTrigger()))
                .count();

        if (priorUserRetries >= maxUserRetries) {
            log.warn("User recovery cap hit for session {} (already {} retries, cap {})",
                    sessionId, priorUserRetries, maxUserRetries);
            throw new ForbiddenException(
                    "Recovery limit reached for this build (" + maxUserRetries +
                            " attempts). Please start a new build or contact support.");
        }

        return doRecover(session, USER_RECOVERY_TRIGGER);
    }

    private BuildSession loadAndValidate(UUID sessionId, UUID requireOwnerId) {
        BuildSession session = buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));

        if (requireOwnerId != null && !session.getUserId().equals(requireOwnerId)) {
            // Don't leak existence to non-owners.
            throw new NotFoundException("Build session not found");
        }

        if (session.getStatus() != BuildStatus.FAILED) {
            throw new ValidationException(
                    "Only FAILED sessions can be recovered (current: " + session.getStatus() + ")");
        }

        if (planDocumentRepository.findBySessionId(sessionId).isEmpty()) {
            throw new ValidationException(
                    "Cannot recover: session has no approved plan to rebuild from");
        }

        return session;
    }

    private BuildIteration doRecover(BuildSession session, String trigger) {
        UUID sessionId = session.getId();
        BuildError latest = buildErrorRepository.findFirstBySessionIdOrderByCreatedAtDesc(sessionId);
        String errorContext = latest != null ? latest.getMessage() : "Previous build failed.";
        String phase = session.getCurrentPhase() != null ? session.getCurrentPhase().name() : "unknown";

        chatMessageService.addMessage(sessionId, "system",
                "RECOVERY (" + trigger + "): previous build failed at phase " + phase + ":\n\n" +
                        errorContext + "\n\n" +
                        "Fix this issue. Do NOT add new features, change scope, or " +
                        "introduce anything not already in the approved plan. Only repair " +
                        "the failure described above.",
                null, 0);

        // Clear FAILED-stamped completedAt so the next terminal transition
        // records the actual recovery completion time.
        session.setCompletedAt(null);
        buildSessionRepository.save(session);

        buildSessionService.updateStatus(sessionId, BuildStatus.BUILDING);
        buildSessionService.updatePhase(sessionId, BuildPhase.IMPLEMENTATION);

        BuildIteration iteration = buildLauncher.startBuild(sessionId, trigger);
        log.info("Recovery launched for session {}: trigger={}, iteration={}",
                sessionId, trigger, iteration.getIterationNumber());
        return iteration;
    }

    /**
     * Returns how many times a user can still trigger recovery on this session.
     * Lets controllers / DTOs surface "Retry available (1/2)" in the UI.
     */
    public int remainingUserRecoveries(UUID sessionId, int maxUserRetries) {
        long used = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId).stream()
                .filter(it -> USER_RECOVERY_TRIGGER.equals(it.getTrigger()))
                .count();
        return Math.max(0, maxUserRetries - (int) used);
    }
}
