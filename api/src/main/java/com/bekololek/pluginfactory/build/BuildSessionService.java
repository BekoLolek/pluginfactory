package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
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

        tokenBudgetService.allocateBudget(saved.getId(), tier);
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
        session.setStatus(BuildStatus.CANCELLED);
        session.setCompletedAt(Instant.now());
        return buildSessionRepository.save(session);
    }

    @Transactional
    public BuildSession updateStatus(UUID sessionId, BuildStatus status) {
        BuildSession session = getSessionById(sessionId);
        session.setStatus(status);
        return buildSessionRepository.save(session);
    }

    @Transactional
    public BuildSession updatePhase(UUID sessionId, BuildPhase phase) {
        BuildSession session = getSessionById(sessionId);
        session.setCurrentPhase(phase);
        return buildSessionRepository.save(session);
    }
}
