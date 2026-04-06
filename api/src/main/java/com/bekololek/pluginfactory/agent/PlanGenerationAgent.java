package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AnthropicResponse;
import com.bekololek.pluginfactory.build.BuildPhase;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.build.ChatMessage;
import com.bekololek.pluginfactory.build.ChatMessageService;
import com.bekololek.pluginfactory.build.TokenBudgetService;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.plan.ComplexityEstimator;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PlanGenerationAgent {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);

    private final AnthropicClient anthropicClient;
    private final ModelRouter modelRouter;
    private final ChatMessageService chatMessageService;
    private final TokenBudgetService tokenBudgetService;
    private final BuildSessionService buildSessionService;
    private final PlanDocumentRepository planDocumentRepository;
    private final ComplexityEstimator complexityEstimator;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public PlanGenerationAgent(AnthropicClient anthropicClient,
                               ModelRouter modelRouter,
                               ChatMessageService chatMessageService,
                               TokenBudgetService tokenBudgetService,
                               BuildSessionService buildSessionService,
                               PlanDocumentRepository planDocumentRepository,
                               ComplexityEstimator complexityEstimator,
                               ObjectMapper objectMapper) throws IOException {
        this.anthropicClient = anthropicClient;
        this.modelRouter = modelRouter;
        this.chatMessageService = chatMessageService;
        this.tokenBudgetService = tokenBudgetService;
        this.buildSessionService = buildSessionService;
        this.planDocumentRepository = planDocumentRepository;
        this.complexityEstimator = complexityEstimator;
        this.objectMapper = objectMapper;
        try (InputStream is = new ClassPathResource("prompts/plan_generation_system.txt").getInputStream()) {
            this.systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public PlanDocument generatePlan(UUID sessionId) {
        // 1. Load chat history
        List<ChatMessage> history = chatMessageService.getMessages(sessionId);

        // 2. Route to Sonnet (PLAN_GENERATION task type)
        String model = modelRouter.selectModel(ModelRouter.TaskType.PLAN_GENERATION);
        int maxTokens = modelRouter.getMaxTokens(ModelRouter.TaskType.PLAN_GENERATION);

        // 3. Build messages array from chat history
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        // 4. Call AnthropicClient
        AnthropicResponse response = anthropicClient.sendMessage(model, systemPrompt, messages, maxTokens);

        // 5. Parse JSON response
        String jsonContent = extractJson(response.content());
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonContent);
        } catch (Exception e) {
            log.error("Failed to parse plan JSON: {}", response.content(), e);
            throw new ValidationException("Failed to parse plan from AI response. Please try again.");
        }

        // 6. Create or update PlanDocument entity
        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    PlanDocument p = new PlanDocument();
                    p.setSessionId(sessionId);
                    return p;
                });

        plan.setPluginName(getTextOrNull(root, "pluginName"));
        plan.setDescription(getTextOrNull(root, "description"));
        plan.setMinecraftVersion(getTextOrNull(root, "minecraftVersion"));
        plan.setServerType(getTextOrNull(root, "serverType"));

        // Store JSON arrays as strings
        plan.setCommands(getArrayAsString(root, "commands"));
        plan.setEventListeners(getArrayAsString(root, "eventListeners"));
        plan.setConfigSchema(getArrayAsString(root, "configSchema"));
        plan.setDependencies(getArrayAsString(root, "dependencies"));
        plan.setTestScenarios(getArrayAsString(root, "testScenarios"));

        if (root.has("estimatedLinesOfCode")) {
            plan.setEstimatedLoc(root.get("estimatedLinesOfCode").asInt());
        } else if (root.has("estimatedLoc")) {
            plan.setEstimatedLoc(root.get("estimatedLoc").asInt());
        }

        // 7. Calculate complexity score
        ComplexityEstimator.ComplexityResult complexity = complexityEstimator.estimateComplexity(plan);
        plan.setComplexityScore(complexity.totalScore());

        // 8. Increment version if updating
        if (plan.getId() != null) {
            plan.setVersion(plan.getVersion() + 1);
        }

        // 9. Update session status and phase
        buildSessionService.updateStatus(sessionId, BuildStatus.PLANNING);
        buildSessionService.updatePhase(sessionId, BuildPhase.PLAN_REVIEW);

        // 10. Consume tokens
        int totalTokens = response.inputTokens() + response.outputTokens();
        tokenBudgetService.consumeTokens(sessionId, "planning", totalTokens);

        // 11. Save and return
        return planDocumentRepository.save(plan);
    }

    String extractJson(String content) {
        // Try to extract from ```json ... ``` blocks
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Try to find JSON object by locating first { and last }
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return content.substring(firstBrace, lastBrace + 1);
        }

        // Return as-is and let JSON parser handle errors
        return content.trim();
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private String getArrayAsString(JsonNode root, String field) {
        if (root.has(field)) {
            try {
                return objectMapper.writeValueAsString(root.get(field));
            } catch (Exception e) {
                return "[]";
            }
        }
        return "[]";
    }
}
