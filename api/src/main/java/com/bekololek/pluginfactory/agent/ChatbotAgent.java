package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AgentResponse;
import com.bekololek.pluginfactory.agent.dto.AnthropicResponse;
import com.bekololek.pluginfactory.build.BuildPhase;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.build.ChatMessage;
import com.bekololek.pluginfactory.build.ChatMessageService;
import com.bekololek.pluginfactory.build.TokenBudget;
import com.bekololek.pluginfactory.build.TokenBudgetService;
import com.bekololek.pluginfactory.plan.PlanDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatbotAgent {

    private static final String TRANSITION_MARKER = "[TRANSITION:PLAN_GENERATION]";

    /**
     * Matches fenced code blocks (```...```) that the AI may produce despite
     * being told not to. During the clarification phase the user should never
     * see raw code — it will be generated properly in the implementation phase.
     */
    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```[\\s\\S]*?```");

    private final AnthropicClient anthropicClient;
    private final ChatMessageService chatMessageService;
    private final TokenBudgetService tokenBudgetService;
    private final BuildSessionService buildSessionService;
    private final ModelRouter modelRouter;
    private final PromptSanitizer promptSanitizer;
    private final PlanGenerationAgent planGenerationAgent;
    private final String chatbotSystemPrompt;
    private final String planGenerationSystemPrompt;

    public ChatbotAgent(AnthropicClient anthropicClient,
                        ChatMessageService chatMessageService,
                        TokenBudgetService tokenBudgetService,
                        BuildSessionService buildSessionService,
                        ModelRouter modelRouter,
                        PromptSanitizer promptSanitizer,
                        PlanGenerationAgent planGenerationAgent) throws IOException {
        this.anthropicClient = anthropicClient;
        this.chatMessageService = chatMessageService;
        this.tokenBudgetService = tokenBudgetService;
        this.buildSessionService = buildSessionService;
        this.modelRouter = modelRouter;
        this.promptSanitizer = promptSanitizer;
        this.planGenerationAgent = planGenerationAgent;
        this.chatbotSystemPrompt = loadPrompt("prompts/chatbot_system.txt");
        this.planGenerationSystemPrompt = loadPrompt("prompts/plan_generation_system.txt");
    }

    private String loadPrompt(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public AgentResponse handleMessage(UUID sessionId, UUID userId, String userMessage) {
        // 1. Sanitize input
        PromptSanitizer.SanitizationResult sanitized = promptSanitizer.sanitize(userMessage);
        if (sanitized.hasSuspiciousContent()) {
            log.warn("Suspicious content in session {}: {}", sessionId, sanitized.flags());
        }
        String cleanMessage = sanitized.cleanMessage();

        // 2. Load session and verify ownership
        BuildSession session = buildSessionService.getSession(sessionId, userId);

        // 3. Check token budget
        TokenBudget budget = tokenBudgetService.getRemainingBudget(sessionId);
        int remainingTokens = budget.getAllocatedTokens() - budget.getConsumedTokens();
        if (!tokenBudgetService.hasBudget(sessionId, 100)) {
            return new AgentResponse(
                    "Token budget exhausted. Please upgrade your plan or start a new session.",
                    null, 0, 0, null);
        }

        // 4. Determine task type based on session phase
        ModelRouter.TaskType taskType = mapPhaseToTaskType(session.getCurrentPhase());

        // 5. Select model via ModelRouter
        String model = modelRouter.selectModel(taskType);
        int maxTokens = modelRouter.getMaxTokens(taskType);

        // 6. Load and fill system prompt template
        String systemPrompt = resolveSystemPrompt(session, remainingTokens);

        // 7. Build messages array from chat history
        List<ChatMessage> history = chatMessageService.getMessages(sessionId);
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }
        // Add current user message
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", cleanMessage);
        messages.add(userMsg);

        // 8. Call AnthropicClient
        AnthropicResponse response = anthropicClient.sendMessage(model, systemPrompt, messages, maxTokens);

        // 9. Strip transition marker BEFORE storing so the raw marker text
        //    never appears in the chat history or the UI.
        String content = response.content();
        String phaseTransition = null;
        if (content.contains(TRANSITION_MARKER)) {
            content = content.replace(TRANSITION_MARKER, "").trim();
            phaseTransition = "PLAN_GENERATION";
        }

        // 9b. During clarification, strip any code blocks the AI produced
        //     despite being told not to. This is a server-side safety net.
        if (session.getCurrentPhase() == BuildPhase.CLARIFICATION) {
            content = stripCodeBlocks(content);
        }

        // 10. Store both user message and assistant response (cleaned)
        int totalTokens = response.inputTokens() + response.outputTokens();
        chatMessageService.addMessage(sessionId, "user", cleanMessage, null, 0);
        chatMessageService.addMessage(sessionId, "assistant", content, response.model(), totalTokens);

        // 11. Update token consumption
        String tokenPhase = mapPhaseToTokenPhase(session.getCurrentPhase());
        tokenBudgetService.consumeTokens(sessionId, tokenPhase, totalTokens);

        // 12. If the AI signaled readiness, trigger plan generation.
        //     We do NOT set status = PLANNING here — PlanGenerationAgent
        //     sets both status (PLANNING) and phase (PLAN_REVIEW) AFTER the
        //     plan is persisted. Setting it early caused a race: the
        //     frontend would poll, see PLANNING, fire GET /plan, and get a
        //     404 because the plan row didn't exist yet.
        if (phaseTransition != null) {
            try {
                PlanDocument plan = planGenerationAgent.generatePlan(sessionId);
                content += "\n\nPlan generated: " + plan.getPluginName();
            } catch (Exception e) {
                log.error("Plan generation failed for session {}", sessionId, e);
                content += "\n\nPlan generation encountered an issue. Please try revising.";
                // Ensure we stay in CHATTING / CLARIFICATION so the user
                // can keep refining. PlanGenerationAgent may have partially
                // updated status before throwing, so reset explicitly.
                buildSessionService.updateStatus(sessionId, BuildStatus.CHATTING);
                buildSessionService.updatePhase(sessionId, BuildPhase.CLARIFICATION);
                phaseTransition = null;
            }
        }

        // 13. Return AgentResponse
        return new AgentResponse(content, response.model(), response.inputTokens(),
                response.outputTokens(), phaseTransition);
    }

    private ModelRouter.TaskType mapPhaseToTaskType(BuildPhase phase) {
        return switch (phase) {
            case CLARIFICATION -> ModelRouter.TaskType.CLARIFICATION;
            case PLAN_GENERATION, PLAN_REVIEW -> ModelRouter.TaskType.PLAN_GENERATION;
            case IMPLEMENTATION -> ModelRouter.TaskType.CODE_GENERATION;
            case COMPILATION -> ModelRouter.TaskType.ERROR_CLASSIFICATION;
            case SECURITY_SCAN -> ModelRouter.TaskType.SECURITY_ANALYSIS;
            case INTEGRATION_TEST -> ModelRouter.TaskType.TEST_GENERATION;
            default -> ModelRouter.TaskType.CLARIFICATION;
        };
    }

    private String mapPhaseToTokenPhase(BuildPhase phase) {
        return switch (phase) {
            case CLARIFICATION, PLAN_GENERATION, PLAN_REVIEW -> "planning";
            case IMPLEMENTATION, COMPILATION -> "implementation";
            case SECURITY_SCAN, INTEGRATION_TEST -> "testing";
            default -> "planning";
        };
    }

    /**
     * Strips markdown fenced code blocks from AI responses. During the
     * clarification phase the AI should only discuss requirements, not
     * produce code. If it does anyway, we remove the blocks and leave a
     * short note so the conversation still makes sense.
     */
    private String stripCodeBlocks(String content) {
        if (!CODE_BLOCK_PATTERN.matcher(content).find()) {
            return content;
        }
        log.warn("Stripped code block(s) from AI response during clarification phase");
        String stripped = CODE_BLOCK_PATTERN.matcher(content).replaceAll(
                "(Code will be generated automatically during the implementation phase.)");
        // Collapse any double-blank-lines left behind
        stripped = stripped.replaceAll("\\n{3,}", "\n\n").trim();
        return stripped;
    }

    private String resolveSystemPrompt(BuildSession session, int remainingTokens) {
        String template;
        if (session.getCurrentPhase() == BuildPhase.PLAN_GENERATION
                || session.getCurrentPhase() == BuildPhase.PLAN_REVIEW) {
            template = planGenerationSystemPrompt;
        } else {
            template = chatbotSystemPrompt;
        }
        return template
                .replace("{{phase}}", session.getCurrentPhase().name())
                .replace("{{status}}", session.getStatus().name())
                .replace("{{remaining_tokens}}", String.valueOf(remainingTokens));
    }
}
