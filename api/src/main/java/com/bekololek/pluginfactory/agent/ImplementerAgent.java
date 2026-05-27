package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AnthropicResponse;
import com.bekololek.pluginfactory.agent.dto.ImplementationResult;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
@Slf4j
public class ImplementerAgent {

    private final AnthropicClient anthropicClient;
    private final ModelRouter modelRouter;
    private final TemplateService templateService;
    private final PlanDocumentRepository planDocumentRepository;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public ImplementerAgent(AnthropicClient anthropicClient,
                            ModelRouter modelRouter,
                            TemplateService templateService,
                            PlanDocumentRepository planDocumentRepository,
                            ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.modelRouter = modelRouter;
        this.templateService = templateService;
        this.planDocumentRepository = planDocumentRepository;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt() + "\n\n" + loadResource("prompts/bukkit_api_reference.txt", "");
    }

    public ImplementationResult implement(UUID sessionId) {
        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Plan document not found for session: " + sessionId));

        // Render template files
        Map<String, String> templateFiles = templateService.renderTemplate(plan);

        // Build the user message with plan details and template code
        String userMessage = buildUserMessage(plan, templateFiles);

        // Call AI to generate implementation
        String model = modelRouter.selectModel(ModelRouter.TaskType.CODE_GENERATION);
        int maxTokens = modelRouter.getMaxTokens(ModelRouter.TaskType.CODE_GENERATION);
        Double temperature = modelRouter.getTemperature(ModelRouter.TaskType.CODE_GENERATION);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", userMessage)
        );

        AnthropicResponse response = anthropicClient.sendMessage(model, systemPrompt, messages, maxTokens, temperature);
        int tokensUsed = response.inputTokens() + response.outputTokens();

        // Parse the AI response as a file map
        Map<String, String> generatedFiles = parseFileMap(response.content());

        // Merge template files with generated files (generated files override templates)
        Map<String, String> allFiles = new LinkedHashMap<>(templateFiles);
        allFiles.putAll(generatedFiles);

        log.info("Implementation complete for session {}: {} files, {} tokens used",
                sessionId, allFiles.size(), tokensUsed);

        return new ImplementationResult(allFiles, tokensUsed);
    }

    String buildUserMessage(PlanDocument plan, Map<String, String> templateFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Plugin Plan\n\n");
        sb.append("**Plugin Name**: ").append(plan.getPluginName()).append("\n");
        sb.append("**Description**: ").append(plan.getDescription()).append("\n");
        sb.append("**Minecraft Version**: ").append(plan.getMinecraftVersion()).append("\n");
        sb.append("**Server Type**: ").append(plan.getServerType()).append("\n\n");

        sb.append("### Commands\n").append(plan.getCommands()).append("\n\n");
        sb.append("### Event Listeners\n").append(plan.getEventListeners()).append("\n\n");
        sb.append("### Config Schema\n").append(plan.getConfigSchema()).append("\n\n");
        sb.append("### Dependencies\n").append(plan.getDependencies()).append("\n\n");

        String viability = plan.getViabilityStatus() != null ? plan.getViabilityStatus() : "READY";
        if (!"READY".equals(viability)) {
            sb.append("### Viability\n");
            sb.append("**Status**: ").append(viability).append("\n\n");
            try {
                java.util.List<?> steps = objectMapper.readValue(plan.getSetupSteps(), new TypeReference<java.util.List<?>>() {});
                if (!steps.isEmpty()) {
                    sb.append("**Admin setup required**:\n");
                    steps.forEach(s -> sb.append("- ").append(s).append("\n"));
                    sb.append("\n");
                }
                java.util.List<?> handled = objectMapper.readValue(plan.getAutoHandled(), new TypeReference<java.util.List<?>>() {});
                if (!handled.isEmpty()) {
                    sb.append("**Plugin must auto-handle (implement each one)**:\n");
                    handled.forEach(s -> sb.append("- ").append(s).append("\n"));
                    sb.append("\n");
                }
            } catch (Exception ignored) {}
        }

        if (hasClassContracts(plan.getClasses())) {
            sb.append("### Class Contracts (LOCKED — every file must match)\n");
            sb.append("These class definitions were agreed at plan time. Constructor signatures, ")
                    .append("`extends` parents, and `implements` interfaces are NOT negotiable: ")
                    .append("every `new X(...)` call site, every parameter type, and every override ")
                    .append("MUST match the contract below. If a class lists `extends Game`, the ")
                    .append("compiler will only accept it where a `Game` is expected. If a class ")
                    .append("lists 4 constructorParams, every `new` call must pass exactly those 4 ")
                    .append("argument types in that order.\n\n");
            sb.append("```json\n").append(plan.getClasses()).append("\n```\n\n");
        }

        sb.append("## Template Code (already generated - DO NOT include these in your response)\n\n");
        sb.append("### pom.xml\n```xml\n").append(templateFiles.get("pom.xml")).append("\n```\n\n");
        sb.append("### plugin.yml\n```yaml\n").append(templateFiles.get("src/main/resources/plugin.yml")).append("\n```\n\n");

        // Find the main class template
        templateFiles.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".java"))
                .forEach(e -> sb.append("### ").append(e.getKey()).append("\n```java\n")
                        .append(e.getValue()).append("\n```\n\n"));

        sb.append("Generate the complete Java implementation files. ");
        sb.append("Extend the main class skeleton above and add any additional classes needed. ");
        sb.append("Respond with ONLY a JSON object mapping file paths to file contents.");

        return sb.toString();
    }

    Map<String, String> parseFileMap(String responseContent) {
        String content = responseContent.trim();

        // Strip markdown code block wrapper if present
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        content = content.trim();

        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse AI response as file map: {}", e.getMessage());
            // Return empty map on parse failure
            return Map.of();
        }
    }

    private boolean hasClassContracts(String classesJson) {
        if (classesJson == null) {
            return false;
        }
        String trimmed = classesJson.trim();
        return !trimmed.isEmpty() && !trimmed.equals("[]") && !trimmed.equals("null");
    }

    private String loadSystemPrompt() {
        return loadResource("prompts/implementer_system.txt",
                "You are a Minecraft plugin developer. Generate Java code for Paper plugins. " +
                        "Respond with a JSON object mapping file paths to file contents.");
    }

    private String loadResource(String path, String fallback) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to load {}, using fallback", path);
            return fallback;
        }
    }
}
