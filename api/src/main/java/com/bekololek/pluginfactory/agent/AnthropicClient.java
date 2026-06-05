package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AnthropicResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@Slf4j
public class AnthropicClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public AnthropicClient(@Qualifier("anthropicRestTemplate") RestTemplate restTemplate,
                           @Value("${anthropic.api-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public AnthropicResponse sendMessage(String model, String systemPrompt,
                                          List<Map<String, String>> messages, int maxTokens) {
        return sendMessage(model, systemPrompt, messages, maxTokens, null);
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "anthropic", fallbackMethod = "fallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "anthropic")
    @SuppressWarnings("unchecked")
    public AnthropicResponse sendMessage(String model, String systemPrompt,
                                          List<Map<String, String>> messages, int maxTokens,
                                          Double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        ResponseEntity<Map> response = restTemplate.postForEntity(
                ANTHROPIC_API_URL, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> responseBody = response.getBody();
        List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
        String text = (String) content.get(0).get("text");
        Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
        int inputTokens = ((Number) usage.get("input_tokens")).intValue();
        int outputTokens = ((Number) usage.get("output_tokens")).intValue();

        return new AnthropicResponse(text, model, inputTokens, outputTokens);
    }

    private AnthropicResponse fallback(String model, String systemPrompt,
                                        List<Map<String, String>> messages, int maxTokens,
                                        Double temperature,
                                        Exception e) {
        log.error("Anthropic API unavailable, circuit breaker triggered", e);
        throw new RuntimeException("AI service temporarily unavailable. Please try again in a moment.");
    }

    /**
     * Forces structured output via tool use. The model must respond by calling {@code toolName}
     * with input matching {@code toolInputSchema}; the parsed input is returned as a JsonNode.
     */
    /** Backwards-compatible overload: server-default temperature, no prompt caching. */
    public ToolUseResponse sendMessageWithTool(String model, String systemPrompt,
                                                List<Map<String, String>> messages, int maxTokens,
                                                String toolName, String toolDescription,
                                                Map<String, Object> toolInputSchema) {
        return sendMessageWithTool(model, systemPrompt, messages, maxTokens,
                toolName, toolDescription, toolInputSchema, null, false);
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "anthropic", fallbackMethod = "toolUseFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "anthropic")
    @SuppressWarnings("unchecked")
    public ToolUseResponse sendMessageWithTool(String model, String systemPrompt,
                                                List<Map<String, String>> messages, int maxTokens,
                                                String toolName, String toolDescription,
                                                Map<String, Object> toolInputSchema,
                                                Double temperature, boolean cacheSystem) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", toolName);
        tool.put("description", toolDescription);
        tool.put("input_schema", toolInputSchema);

        Map<String, Object> toolChoice = new LinkedHashMap<>();
        toolChoice.put("type", "tool");
        toolChoice.put("name", toolName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", buildSystemField(systemPrompt, cacheSystem));
        body.put("messages", messages);
        body.put("tools", List.of(tool));
        body.put("tool_choice", toolChoice);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        ResponseEntity<Map> response = restTemplate.postForEntity(
                ANTHROPIC_API_URL, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> responseBody = response.getBody();
        List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");

        Map<String, Object> toolUseBlock = content.stream()
                .filter(b -> "tool_use".equals(b.get("type")) && toolName.equals(b.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expected tool_use block named '" + toolName + "' but none returned"));

        JsonNode input = new ObjectMapper().valueToTree(toolUseBlock.get("input"));

        Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
        int inputTokens = ((Number) usage.get("input_tokens")).intValue();
        int outputTokens = ((Number) usage.get("output_tokens")).intValue();

        return new ToolUseResponse(input, model, inputTokens, outputTokens);
    }

    private ToolUseResponse toolUseFallback(String model, String systemPrompt,
                                             List<Map<String, String>> messages, int maxTokens,
                                             String toolName, String toolDescription,
                                             Map<String, Object> toolInputSchema,
                                             Double temperature, boolean cacheSystem,
                                             Exception e) {
        log.error("Anthropic API unavailable, circuit breaker triggered", e);
        throw new RuntimeException("AI service temporarily unavailable. Please try again in a moment.");
    }

    /**
     * Builds the {@code system} request field. When {@code cache}, returns a
     * single text content block tagged with {@code cache_control: ephemeral}
     * so Anthropic prompt-caches the (large, static) system prompt — cutting
     * input cost on repeat calls. Otherwise returns the plain string form.
     */
    private Object buildSystemField(String systemPrompt, boolean cache) {
        if (!cache || systemPrompt == null || systemPrompt.isBlank()) {
            return systemPrompt;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", systemPrompt);
        block.put("cache_control", Map.of("type", "ephemeral"));
        return List.of(block);
    }

    public record ToolUseResponse(JsonNode input, String model, int inputTokens, int outputTokens) {}

    public void sendMessageStreaming(String model, String systemPrompt,
                                     List<Map<String, String>> messages, int maxTokens,
                                     Consumer<String> onToken, Runnable onComplete) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        body.put("stream", true);

        restTemplate.execute(ANTHROPIC_API_URL, HttpMethod.POST,
                request -> {
                    request.getHeaders().putAll(headers);
                    new ObjectMapper().writeValue(request.getBody(), body);
                },
                response -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) break;
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> event = new ObjectMapper().readValue(data, Map.class);
                                    String type = (String) event.get("type");
                                    if ("content_block_delta".equals(type)) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                                        String text = (String) delta.get("text");
                                        if (text != null) onToken.accept(text);
                                    }
                                } catch (Exception ignored) {
                                    // Skip malformed SSE events
                                }
                            }
                        }
                    }
                    onComplete.run();
                    return null;
                });
    }
}
