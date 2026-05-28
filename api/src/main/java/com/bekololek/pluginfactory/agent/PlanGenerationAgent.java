package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.build.BuildErrorRecorder;
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

@Service
@Slf4j
public class PlanGenerationAgent {

    static final String PLAN_TOOL_NAME = "submit_plan";
    private static final String PLAN_TOOL_DESCRIPTION =
            "Submit the finalized Minecraft plugin development plan. " +
            "You MUST call this tool with the complete plan; do not ask follow-up questions.";

    private final AnthropicClient anthropicClient;
    private final ModelRouter modelRouter;
    private final ChatMessageService chatMessageService;
    private final TokenBudgetService tokenBudgetService;
    private final BuildSessionService buildSessionService;
    private final PlanDocumentRepository planDocumentRepository;
    private final ComplexityEstimator complexityEstimator;
    private final ObjectMapper objectMapper;
    private final BuildErrorRecorder buildErrorRecorder;
    private final String systemPrompt;

    public PlanGenerationAgent(AnthropicClient anthropicClient,
                               ModelRouter modelRouter,
                               ChatMessageService chatMessageService,
                               TokenBudgetService tokenBudgetService,
                               BuildSessionService buildSessionService,
                               PlanDocumentRepository planDocumentRepository,
                               ComplexityEstimator complexityEstimator,
                               ObjectMapper objectMapper,
                               BuildErrorRecorder buildErrorRecorder) throws IOException {
        this.anthropicClient = anthropicClient;
        this.modelRouter = modelRouter;
        this.chatMessageService = chatMessageService;
        this.tokenBudgetService = tokenBudgetService;
        this.buildSessionService = buildSessionService;
        this.planDocumentRepository = planDocumentRepository;
        this.complexityEstimator = complexityEstimator;
        this.objectMapper = objectMapper;
        this.buildErrorRecorder = buildErrorRecorder;
        try (InputStream is = new ClassPathResource("prompts/plan_generation_system.txt").getInputStream()) {
            this.systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public PlanDocument generatePlan(UUID sessionId) {
        try {
            return doGeneratePlan(sessionId);
        } catch (RuntimeException e) {
            buildErrorRecorder.record(sessionId, "PLAN_GENERATION", "ERROR", e.getMessage(), e);
            throw e;
        }
    }

    private PlanDocument doGeneratePlan(UUID sessionId) {
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

        // The Anthropic API requires the conversation to end with a user message.
        // The chatbot saves its transition confirmation as an assistant message
        // before this call runs, so strip any trailing assistant turns.
        while (!messages.isEmpty()
                && "assistant".equals(messages.get(messages.size() - 1).get("role"))) {
            messages.remove(messages.size() - 1);
        }

        // 4. Call AnthropicClient with forced tool use — model must return structured input
        AnthropicClient.ToolUseResponse response = anthropicClient.sendMessageWithTool(
                model, systemPrompt, messages, maxTokens,
                PLAN_TOOL_NAME, PLAN_TOOL_DESCRIPTION, planToolSchema());

        JsonNode root = response.input();
        if (root == null || !root.isObject()) {
            log.error("Plan tool returned non-object input: {}", root);
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
        plan.setClasses(getArrayAsString(root, "classes"));

        if (root.has("viabilityStatus") && !root.get("viabilityStatus").isNull()) {
            plan.setViabilityStatus(root.get("viabilityStatus").asText("READY"));
        }
        plan.setSetupSteps(getArrayAsString(root, "setupSteps"));
        plan.setAutoHandled(getArrayAsString(root, "autoHandled"));

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

    static Map<String, Object> planToolSchema() {
        Map<String, Object> commandItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "permission", Map.of("type", "string"),
                        "usage", Map.of("type", "string")
                ),
                "required", List.of("name", "description")
        );

        Map<String, Object> eventItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "event", Map.of("type", "string"),
                        "description", Map.of("type", "string")
                ),
                "required", List.of("event", "description")
        );

        // Class contract: every Java class the plugin will define, with the
        // hierarchy + constructor signature the implementer must honour.
        // Pattern locks on `name` keep the LLM from emitting generics or
        // anonymous-class chatter. constructorParams entries are free-form
        // strings shaped like "Type name" (the order is the call-site order).
        Map<String, Object> classItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "pattern", "^[A-Z][A-Za-z0-9_]*$",
                                "description", "Simple Java class name (no package, no generics)."
                        ),
                        "extends", Map.of(
                                "type", "string",
                                "description", "Parent class simple name, or null/empty if extending Object."
                        ),
                        "implements", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Simple names of interfaces this class implements."
                        ),
                        "constructorParams", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Each entry is `Type name`, in the exact order the " +
                                        "constructor will receive them. Empty list means no-arg constructor."
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "One-line role for this class (e.g. 'main plugin', " +
                                        "'/start command executor', 'GUI for the active board')."
                        )
                ),
                "required", List.of("name")
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pluginName", Map.of("type", "string"));
        properties.put("description", Map.of("type", "string"));
        // Pattern-locked: must be an exact major.minor.patch like "1.21.4".
        // Anthropic enforces JSON Schema patterns server-side when tool_choice
        // forces this tool, so the model literally cannot return "1.21.x" or
        // "latest" — those would have killed Maven before any Java compiled.
        properties.put("minecraftVersion", Map.of(
                "type", "string",
                "pattern", "^\\d+\\.\\d+\\.\\d+$",
                "description", "Exact Paper release version (e.g. 1.21.4). " +
                        "Must be a real published paper-api artifact — no wildcards, no major-only."
        ));
        properties.put("serverType", Map.of("type", "string"));
        properties.put("commands", Map.of("type", "array", "items", commandItem));
        properties.put("eventListeners", Map.of("type", "array", "items", eventItem));
        properties.put("configSchema", Map.of("type", "object", "additionalProperties", true));
        properties.put("dependencies", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("testScenarios", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("classes", Map.of(
                "type", "array",
                "items", classItem,
                "description", "Every Java class this plugin will define, with hierarchy " +
                        "and primary constructor signature locked. The implementer is " +
                        "required to honour these contracts across files. May be empty " +
                        "for trivial single-class plugins."
        ));
        properties.put("estimatedLinesOfCode", Map.of("type", "integer"));

        properties.put("viabilityStatus", Map.of(
                "type", "string",
                "enum", List.of("READY", "NEEDS_SETUP", "PARTIAL"),
                "description", "READY = works on install; NEEDS_SETUP = admin must configure before it functions; PARTIAL = core works but some features need setup"
        ));
        properties.put("setupSteps", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Steps the server admin must complete before the plugin is functional. Empty for READY plugins."
        ));
        properties.put("autoHandled", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Features the plugin itself will provide to make setup easier (in-game setup commands, sample config generation, startup validation messages, /reload command)."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("pluginName", "description", "commands", "eventListeners"));
        return schema;
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
