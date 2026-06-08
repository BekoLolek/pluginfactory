package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AgentResponse;
import com.bekololek.pluginfactory.agent.dto.SendMessageRequest;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.build.ChatMessage;
import com.bekololek.pluginfactory.build.ChatMessageService;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/builds")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatbotAgent chatbotAgent;
    private final BuildSessionService buildSessionService;
    private final ChatMessageService chatMessageService;
    private final AnthropicClient anthropicClient;
    private final ModelRouter modelRouter;

    @PostMapping("/{sessionId}/messages")
    @RateLimiter(name = "chat")
    public ResponseEntity<AgentResponse> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        BuildSession session = buildSessionService.getSession(sessionId, userId);

        validateSessionStatus(session);

        // "Skip questions — just build it": latch the flag on the session so the
        // chatbot skips clarification and goes straight to plan generation.
        if (Boolean.TRUE.equals(request.skipClarification()) && !session.isSkipClarification()) {
            buildSessionService.enableSkipClarification(sessionId);
        }

        AgentResponse response = chatbotAgent.handleMessage(sessionId, userId, request.content());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(@PathVariable UUID sessionId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        BuildSession session = buildSessionService.getSession(sessionId, userId);

        validateSessionStatus(session);

        SseEmitter emitter = new SseEmitter(120_000L);

        // Build messages from chat history
        List<ChatMessage> history = chatMessageService.getMessages(sessionId);
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        ModelRouter.TaskType taskType = ModelRouter.TaskType.CLARIFICATION;
        String model = modelRouter.selectModel(taskType);
        int maxTokens = modelRouter.getMaxTokens(taskType);

        anthropicClient.sendMessageStreaming(
                model,
                "You are a helpful Minecraft plugin development assistant.",
                messages,
                maxTokens,
                token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (Exception e) {
                        log.error("Error sending SSE event", e);
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error completing SSE stream", e);
                    }
                }
        );

        return emitter;
    }

    private void validateSessionStatus(BuildSession session) {
        if (session.getStatus() != BuildStatus.CHATTING
                && session.getStatus() != BuildStatus.PLANNING) {
            throw new ForbiddenException("Session is not in a chat-eligible state");
        }
    }
}
