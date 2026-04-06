package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BuildProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyPhaseChange(UUID sessionId, BuildPhase phase) {
        messagingTemplate.convertAndSend(
                "/topic/builds/" + sessionId + "/progress",
                Map.of("type", "PHASE_CHANGE", "phase", phase.name(), "sessionId", sessionId.toString())
        );
    }

    public void notifyBudgetUpdate(UUID sessionId, TokenBudget budget) {
        messagingTemplate.convertAndSend(
                "/topic/builds/" + sessionId + "/progress",
                Map.of(
                        "type", "BUDGET_UPDATE",
                        "sessionId", sessionId.toString(),
                        "allocatedTokens", budget.getAllocatedTokens(),
                        "consumedTokens", budget.getConsumedTokens(),
                        "thresholdStatus", budget.getThresholdStatus().name()
                )
        );
    }

    public void notifyError(UUID sessionId, String errorMessage) {
        messagingTemplate.convertAndSend(
                "/topic/builds/" + sessionId + "/progress",
                Map.of("type", "ERROR", "sessionId", sessionId.toString(), "message", errorMessage)
        );
    }

    public void notifyStatusChange(UUID sessionId, BuildStatus status) {
        messagingTemplate.convertAndSend(
                "/topic/builds/" + sessionId + "/progress",
                Map.of("type", "STATUS_CHANGE", "status", status.name(), "sessionId", sessionId.toString())
        );
    }
}
