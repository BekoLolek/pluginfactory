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

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "anthropic", fallbackMethod = "fallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "anthropic")
    @SuppressWarnings("unchecked")
    public AnthropicResponse sendMessage(String model, String systemPrompt,
                                          List<Map<String, String>> messages, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", messages);

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
                                        Exception e) {
        log.error("Anthropic API unavailable, circuit breaker triggered", e);
        throw new RuntimeException("AI service temporarily unavailable. Please try again in a moment.");
    }

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
