package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.team.TeamService;
import com.bekololek.pluginfactory.team.SharedWorkspace;
import com.bekololek.pluginfactory.team.SharedWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BuildSessionService {

    private final SubscriptionService subscriptionService;
    private final TokenBudgetService tokenBudgetService;
    private final BuildSessionRepository buildSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TokenBudgetRepository tokenBudgetRepository;
    private final BuildIterationRepository buildIterationRepository;
    private final BuildErrorRepository buildErrorRepository;
    private final ArtifactRepository artifactRepository;
    private final PlanDocumentRepository planDocumentRepository;
    private final ContainerSessionRepository containerSessionRepository;
    private final TeamService teamService;
    private final SharedWorkspaceRepository sharedWorkspaceRepository;

    @Transactional
    public BuildSession createSession(UUID userId) {
        return createSession(userId, null);
    }

    @Transactional
    public BuildSession createSession(UUID userId, UUID workspaceId) {
        if (!subscriptionService.canBuild(userId)) {
            throw new ForbiddenException("Build limit reached");
        }

        if (workspaceId != null) {
            SharedWorkspace workspace = sharedWorkspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new NotFoundException("Workspace not found"));
            if (!teamService.isMember(workspace.getTeam().getId(), userId)) {
                throw new ForbiddenException("Not a member of the workspace's team");
            }
        }

        Tier tier = subscriptionService.getTierForUser(userId);

        BuildSession session = new BuildSession();
        session.setUserId(userId);
        session.setWorkspaceId(workspaceId);
        session.setStatus(BuildStatus.CHATTING);
        session.setCurrentPhase(BuildPhase.CLARIFICATION);
        BuildSession saved = buildSessionRepository.save(session);

        tokenBudgetService.allocateBudget(saved.getId(), userId, tier);
        subscriptionService.incrementBuildCount(userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public BuildSession getSession(UUID sessionId, UUID userId) {
        BuildSession session = buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));

        if (session.getUserId().equals(userId)) {
            return session;
        }

        if (session.getWorkspaceId() != null) {
            SharedWorkspace workspace = sharedWorkspaceRepository.findById(session.getWorkspaceId())
                    .orElse(null);
            if (workspace != null && teamService.isMember(workspace.getTeam().getId(), userId)) {
                return session;
            }
        }

        throw new NotFoundException("Build session not found");
    }

    @Transactional(readOnly = true)
    public BuildSession getSessionById(UUID sessionId) {
        return buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));
    }

    @Transactional(readOnly = true)
    public Page<BuildSession> listSessions(UUID userId, Pageable pageable) {
        return buildSessionRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<BuildSession> getWorkspaceBuilds(UUID workspaceId, UUID userId) {
        SharedWorkspace workspace = sharedWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        if (!teamService.isMember(workspace.getTeam().getId(), userId)) {
            throw new ForbiddenException("Not a member of the workspace's team");
        }
        return buildSessionRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional
    public BuildSession cancelSession(UUID sessionId, UUID userId) {
        BuildSession session = getSession(sessionId, userId);
        BuildStatus previousStatus = session.getStatus();
        session.setStatus(BuildStatus.CANCELLED);
        session.setCompletedAt(Instant.now());
        BuildSession saved = buildSessionRepository.save(session);
        refundIfFirstNonSuccessTerminal(session.getUserId(), previousStatus, BuildStatus.CANCELLED);
        return saved;
    }

    /**
     * Permanently deletes a build session and all related data.
     * Refunds the build slot if the session wasn't already in a
     * terminal state.
     */
    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        BuildSession session = getSession(sessionId, userId);

        // Refund if not already terminal
        if (!isTerminal(session.getStatus())) {
            subscriptionService.refundBuildSlot(userId);
        }

        // Delete child entities (order matters for FK constraints)
        List<UUID> iterationIds = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId)
                .stream().map(BuildIteration::getId).toList();

        if (!iterationIds.isEmpty()) {
            buildErrorRepository.deleteByIterationIdIn(iterationIds);
            containerSessionRepository.deleteByIterationIdIn(iterationIds);
        }

        artifactRepository.deleteBySessionId(sessionId);
        buildIterationRepository.deleteBySessionId(sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        tokenBudgetRepository.deleteBySessionId(sessionId);
        planDocumentRepository.deleteBySessionId(sessionId);
        buildSessionRepository.delete(session);
    }

    @Transactional
    public BuildSession updateStatus(UUID sessionId, BuildStatus status) {
        BuildSession session = getSessionById(sessionId);
        BuildStatus previousStatus = session.getStatus();
        session.setStatus(status);
        // Stamp completedAt on the first transition into any terminal state so
        // failed/cancelled sessions stop showing up as "completedAt: null"
        // zombies in admin queries. Only set it once — re-entries (e.g.
        // FAILED → CANCELLED) keep the original completion timestamp.
        if (isTerminal(status) && session.getCompletedAt() == null) {
            session.setCompletedAt(Instant.now());
        }
        BuildSession saved = buildSessionRepository.save(session);
        refundIfFirstNonSuccessTerminal(session.getUserId(), previousStatus, status);
        return saved;
    }

    /**
     * Build slots are consumed at session creation, but a build that never
     * produces a successful artifact shouldn't count against the user's
     * monthly quota — that just punishes people for our flakiness. Refund
     * the slot the first time a session enters FAILED or CANCELLED, and
     * never on a re-entry (e.g. FAILED → CANCELLED) so a single session
     * can't refund itself twice. Tokens already burned are still charged
     * via the TokenBudget ledger.
     */
    private void refundIfFirstNonSuccessTerminal(UUID userId, BuildStatus previousStatus, BuildStatus newStatus) {
        if (!isNonSuccessTerminal(newStatus) || isTerminal(previousStatus)) {
            return;
        }
        subscriptionService.refundBuildSlot(userId);
    }

    private boolean isNonSuccessTerminal(BuildStatus status) {
        return status == BuildStatus.FAILED || status == BuildStatus.CANCELLED;
    }

    private boolean isTerminal(BuildStatus status) {
        return status == BuildStatus.COMPLETED
                || status == BuildStatus.FAILED
                || status == BuildStatus.CANCELLED;
    }

    @Transactional
    public BuildSession updatePhase(UUID sessionId, BuildPhase phase) {
        BuildSession session = getSessionById(sessionId);
        session.setCurrentPhase(phase);
        return buildSessionRepository.save(session);
    }

    /** Enable "skip clarification" mode for a session (build-form toggle). */
    public BuildSession enableSkipClarification(UUID sessionId) {
        BuildSession session = getSessionById(sessionId);
        session.setSkipClarification(true);
        return buildSessionRepository.save(session);
    }
}
