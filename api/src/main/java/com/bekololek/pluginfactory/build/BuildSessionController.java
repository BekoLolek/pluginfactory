package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.BuildSessionDto;
import com.bekololek.pluginfactory.build.dto.ChatMessageDto;
import com.bekololek.pluginfactory.build.dto.TokenBudgetDto;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/builds")
@RequiredArgsConstructor
public class BuildSessionController {

    private final BuildSessionService buildSessionService;
    private final TokenBudgetService tokenBudgetService;
    private final ChatMessageService chatMessageService;

    @PostMapping
    public ResponseEntity<BuildSessionDto> createSession() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        BuildSession session = buildSessionService.createSession(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @GetMapping
    public ResponseEntity<Page<BuildSessionDto>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        Page<BuildSessionDto> sessions = buildSessionService
                .listSessions(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toDto);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BuildSessionDto> getSession(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        BuildSession session = buildSessionService.getSession(id, userId);
        return ResponseEntity.ok(toDto(session));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.deleteSession(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/budget")
    public ResponseEntity<TokenBudgetDto> getBudget(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(id, userId); // ownership check
        TokenBudget budget = tokenBudgetService.getRemainingBudget(id);
        return ResponseEntity.ok(toBudgetDto(budget));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(id, userId); // ownership check
        List<ChatMessageDto> messages = chatMessageService.getMessages(id).stream()
                .map(this::toMessageDto)
                .toList();
        return ResponseEntity.ok(messages);
    }

    private BuildSessionDto toDto(BuildSession session) {
        return new BuildSessionDto(
                session.getId(),
                session.getUserId(),
                session.getStatus().name(),
                session.getCurrentPhase().name(),
                session.getComplexityScore(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getCompletedAt()
        );
    }

    private TokenBudgetDto toBudgetDto(TokenBudget budget) {
        return new TokenBudgetDto(
                budget.getSessionId(),
                budget.getAllocatedTokens(),
                budget.getConsumedTokens(),
                budget.getPlanningTokens(),
                budget.getImplementationTokens(),
                budget.getTestingTokens(),
                budget.getThresholdStatus().name(),
                budget.getAllocatedTokens() - budget.getConsumedTokens()
        );
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return new ChatMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getModelUsed(),
                message.getTokensConsumed(),
                message.getCreatedAt()
        );
    }
}
