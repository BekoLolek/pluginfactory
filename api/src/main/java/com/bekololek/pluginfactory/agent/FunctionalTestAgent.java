package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a Mineflayer functional-test script ({@code module.exports = { scenarios: [...] }})
 * for a freshly built plugin, targeting the harness's {@code api.js} surface. The script is run
 * by {@code FunctionalTestService} inside the isolated test container.
 */
@Service
@Slf4j
public class FunctionalTestAgent {

    static final String TOOL_NAME = "submit_test_script";
    private static final String TOOL_DESC =
            "Submit the complete Mineflayer functional-test script as a CommonJS module string.";

    private final AnthropicClient anthropicClient;
    private final ModelRouter modelRouter;
    private final PlanDocumentRepository planDocumentRepository;
    private final String systemPrompt;

    public FunctionalTestAgent(AnthropicClient anthropicClient,
                               ModelRouter modelRouter,
                               PlanDocumentRepository planDocumentRepository) throws IOException {
        this.anthropicClient = anthropicClient;
        this.modelRouter = modelRouter;
        this.planDocumentRepository = planDocumentRepository;
        try (InputStream is = new ClassPathResource("prompts/functional_test_system.txt").getInputStream()) {
            this.systemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record ScenarioScript(String script, int tokensUsed) {}

    public ScenarioScript generate(UUID sessionId, Map<String, String> files) {
        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Plan not found for session: " + sessionId));

        String userMessage = buildUserMessage(plan, files);
        String model = modelRouter.selectModel(ModelRouter.TaskType.TEST_GENERATION);
        int maxTokens = modelRouter.getMaxTokens(ModelRouter.TaskType.TEST_GENERATION);
        Double temperature = modelRouter.getTemperature(ModelRouter.TaskType.TEST_GENERATION);

        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", userMessage));
        AnthropicClient.ToolUseResponse resp = anthropicClient.sendMessageWithTool(
                model, systemPrompt, messages, maxTokens, TOOL_NAME, TOOL_DESC, toolSchema(), temperature, true);

        JsonNode input = resp.input();
        String script = (input != null && input.has("script")) ? input.get("script").asText("") : "";
        log.info("Functional test script generated for session {}: {} chars, {} tokens",
                sessionId, script.length(), resp.inputTokens() + resp.outputTokens());
        return new ScenarioScript(script, resp.inputTokens() + resp.outputTokens());
    }

    String buildUserMessage(PlanDocument plan, Map<String, String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Plugin under test\n");
        sb.append("Name: ").append(plan.getPluginName()).append("\n");
        sb.append("Description: ").append(plan.getDescription()).append("\n\n");

        String pluginYml = files.entrySet().stream()
                .filter(e -> e.getKey().endsWith("plugin.yml"))
                .map(Map.Entry::getValue).findFirst().orElse("");
        sb.append("### plugin.yml (authoritative command names)\n```yaml\n").append(pluginYml).append("\n```\n\n");

        sb.append("### Commands (plan)\n").append(plan.getCommands()).append("\n\n");
        sb.append("### Intended test scenarios (plan)\n").append(plan.getTestScenarios()).append("\n\n");

        String viab = plan.getViabilityStatus();
        if (viab != null && !"READY".equalsIgnoreCase(viab)) {
            sb.append("Viability: ").append(viab)
              .append(" — the server uses the plugin's generated DEFAULT config; test default behaviour.\n\n");
        }

        // The generated source tells you exactly what messages/items each command produces,
        // so your assertions match reality (e.g. the exact chat text or item name).
        sb.append("### Generated source (for accurate assertions)\n");
        int budget = 24000; // chars
        for (Map.Entry<String, String> e : files.entrySet()) {
            if (!e.getKey().endsWith(".java")) continue;
            String body = e.getValue();
            if (budget - body.length() < 0) { body = body.substring(0, Math.max(0, budget)) + "\n…(truncated)"; }
            budget -= body.length();
            sb.append("#### ").append(e.getKey()).append("\n```java\n").append(body).append("\n```\n\n");
            if (budget <= 0) break;
        }

        sb.append("Write the functional-test script per the rules and submit via submit_test_script.");
        return sb.toString();
    }

    static Map<String, Object> toolSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("script", Map.of(
                "type", "string",
                "description", "Complete CommonJS module: module.exports = { scenarios: [ {name, run}, ... ] }")));
        schema.put("required", List.of("script"));
        return schema;
    }
}
