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

    /**
     * Matches the start of a tool-call the model emulates as raw TEXT when it
     * was instructed to call a tool but the request provided none. Claude
     * improvises several shapes — {@code <submit_plan>}, {@code <function_calls>
     * <invoke name="submit_plan">}, {@code <simulate_tool_call>submit_plan({…})},
     * {@code <trigger_plan_submission>} — and may also leak a {@code <thinking>}
     * block. Everything from the first such marker onward is garbage and must
     * never reach the chat history.
     */
    private static final Pattern TOOL_CALL_MARKER = Pattern.compile(
            "(?is)<\\s*(submit_plan|trigger_plan_submission|function_calls|invoke|simulate_tool_call|thinking)\\b"
                    + "|submit_plan\\s*\\(");

    private final AnthropicClient anthropicClient;
    private final ChatMessageService chatMessageService;
    private final TokenBudgetService tokenBudgetService;
    private final BuildSessionService buildSessionService;
    private final ModelRouter modelRouter;
    private final PromptSanitizer promptSanitizer;
    private final PlanGenerationAgent planGenerationAgent;
    private final String chatbotSystemPrompt;

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

        // 9. Detect plan-generation intent and clean the reply BEFORE storing.
        //    The model signals readiness with the [TRANSITION:PLAN_GENERATION]
        //    marker. When it goes off-script it instead emulates a `submit_plan`
        //    tool call as raw text. Treat either signal as the trigger, and
        //    never let the raw marker or the emulated tool-call text reach the
        //    chat history.
        String content = response.content();
        boolean emulatedToolCall = content != null && TOOL_CALL_MARKER.matcher(content).find();
        boolean wantsPlan = (content != null && content.contains(TRANSITION_MARKER)) || emulatedToolCall;
        String phaseTransition = wantsPlan ? "PLAN_GENERATION" : null;

        if (content != null) {
            content = content.replace(TRANSITION_MARKER, "");
        }
        content = stripToolCallEmulation(content);
        // During clarification, strip any code blocks the AI produced despite
        // being told not to. This is a server-side safety net.
        if (session.getCurrentPhase() == BuildPhase.CLARIFICATION) {
            content = stripCodeBlocks(content);
        }
        content = content == null ? "" : content.trim();
        // If the model produced nothing usable and isn't transitioning, ask a
        // gentle follow-up rather than storing an empty assistant turn.
        if (content.isBlank() && phaseTransition == null) {
            content = "Could you share a bit more detail so I can refine the plan?";
        }

        // 10. Store the user message and the cleaned assistant reply (if any).
        int totalTokens = response.inputTokens() + response.outputTokens();
        chatMessageService.addMessage(sessionId, "user", cleanMessage, null, 0);
        if (!content.isBlank()) {
            chatMessageService.addMessage(sessionId, "assistant", content, response.model(), totalTokens);
        }

        // 11. Update token consumption
        String tokenPhase = mapPhaseToTokenPhase(session.getCurrentPhase());
        tokenBudgetService.consumeTokens(sessionId, tokenPhase, totalTokens);

        // 12. If the AI signaled readiness, generate the plan and post a visible
        //     acknowledgment so the conversation reflects what happened. We do
        //     NOT set status = PLANNING here — PlanGenerationAgent sets status
        //     and phase AFTER the plan row is persisted (avoids a poll/404 race).
        if (phaseTransition != null) {
            try {
                PlanDocument plan = planGenerationAgent.generatePlan(sessionId);
                String ack = PlanGenerationAgent.buildAcknowledgment(plan);
                chatMessageService.addMessage(sessionId, "assistant", ack, null, 0);
                content = content.isBlank() ? ack : content + "\n\n" + ack;
            } catch (Exception e) {
                log.error("Plan generation failed for session {}", sessionId, e);
                String failMsg = "I hit a snag while putting your plan together. "
                        + "Please send your request again in a moment and I'll retry.";
                chatMessageService.addMessage(sessionId, "assistant", failMsg, null, 0);
                content = content.isBlank() ? failMsg : content + "\n\n" + failMsg;
                // Stay in CHATTING / CLARIFICATION so the user can keep refining.
                // PlanGenerationAgent may have partially updated status before
                // throwing, so reset explicitly.
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

    /**
     * Removes any tool-call the model emulated as raw text. First strips paired
     * {@code <thinking>…</thinking>} blocks, then cuts everything from the first
     * residual tool-call/thinking marker to the end of the message. Belt-and-
     * suspenders alongside the prompt fix: the conversational call now uses the
     * tool-free chatbot prompt, so this should rarely fire — but if it ever
     * does, a stray {@code submit_plan} dump can never reach a user.
     */
    private String stripToolCallEmulation(String content) {
        if (content == null) {
            return "";
        }
        String result = content.replaceAll("(?is)<thinking>.*?</thinking>", "");
        java.util.regex.Matcher m = TOOL_CALL_MARKER.matcher(result);
        if (m.find()) {
            log.warn("Stripped emulated tool-call text from AI chat response");
            result = result.substring(0, m.start());
        }
        return result.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String resolveSystemPrompt(BuildSession session, int remainingTokens) {
        // Always use the conversational prompt for chat turns. The
        // plan-generation prompt is tool-only ("call the submit_plan tool")
        // and MUST NOT be used with the no-tool sendMessage — doing so makes
        // the model emit a fake tool call as text that leaks into the chat.
        // The chatbot prompt is phase-aware (it knows about PLAN_REVIEW) and
        // drives real plan generation via the [TRANSITION:PLAN_GENERATION]
        // marker, which routes to PlanGenerationAgent's forced tool-use call.
        String prompt = chatbotSystemPrompt
                .replace("{{phase}}", session.getCurrentPhase().name())
                .replace("{{status}}", session.getStatus().name())
                .replace("{{remaining_tokens}}", String.valueOf(remainingTokens));

        // "Skip questions" mode: the user opted out of clarification, so the
        // model must NOT ask anything. It gives one brief confirmation of what
        // it will build (so the user can catch a big misunderstanding) and then
        // immediately emits the transition marker to start plan generation.
        if (session.isSkipClarification() && session.getCurrentPhase() == BuildPhase.CLARIFICATION) {
            prompt += SKIP_CLARIFICATION_DIRECTIVE;
        }
        return prompt;
    }

    private static final String SKIP_CLARIFICATION_DIRECTIVE = """

            ## SKIP-QUESTIONS MODE (the user opted out of clarifying questions)
            The user has turned on "just build it" — do NOT ask any clarifying
            questions, and do NOT wait for more input. Make sensible, conventional
            assumptions for anything unspecified. In 2-3 short sentences, confirm
            what you will build (so they can catch a major misunderstanding), then
            on its own line emit exactly [TRANSITION:PLAN_GENERATION]. Do not ask
            them to confirm or reply first.""";
}
