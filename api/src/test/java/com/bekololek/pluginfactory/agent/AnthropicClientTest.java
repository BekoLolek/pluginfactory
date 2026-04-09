package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.agent.dto.AnthropicResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicClientTest {

    @Mock
    private RestTemplate restTemplate;

    private static final String API_KEY = "test-api-key";

    @Test
    void sendMessageConstructsCorrectHeadersAndParsesResponse() {
        // Arrange
        AnthropicClient client = new AnthropicClient(restTemplate, API_KEY);

        Map<String, Object> responseBody = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Hello from Claude")),
                "model", "claude-haiku-4-5",
                "usage", Map.of("input_tokens", 100, "output_tokens", 50)
        );

        when(restTemplate.postForEntity(
                eq("https://api.anthropic.com/v1/messages"),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Hello")
        );

        // Act
        AnthropicResponse response = client.sendMessage(
                "claude-haiku-4-5", "System prompt", messages, 2048);

        // Assert
        assertNotNull(response);
        assertEquals("Hello from Claude", response.content());
        assertEquals("claude-haiku-4-5", response.model());
        assertEquals(100, response.inputTokens());
        assertEquals(50, response.outputTokens());

        // Verify headers
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("https://api.anthropic.com/v1/messages"),
                captor.capture(),
                eq(Map.class));

        HttpEntity<?> entity = captor.getValue();
        HttpHeaders headers = entity.getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertEquals("test-api-key", headers.getFirst("x-api-key"));
        assertEquals("2023-06-01", headers.getFirst("anthropic-version"));
    }

    @Test
    void sendMessageIncludesCorrectBody() {
        // Arrange
        AnthropicClient client = new AnthropicClient(restTemplate, API_KEY);

        Map<String, Object> responseBody = Map.of(
                "content", List.of(Map.of("type", "text", "text", "Response")),
                "model", "claude-sonnet-4-5",
                "usage", Map.of("input_tokens", 200, "output_tokens", 100)
        );

        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Generate a plan")
        );

        // Act
        AnthropicResponse response = client.sendMessage(
                "claude-sonnet-4-5", "Plan system prompt", messages, 8192);

        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), captor.capture(), eq(Map.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("claude-sonnet-4-5", body.get("model"));
        assertEquals(8192, body.get("max_tokens"));
        assertEquals("Plan system prompt", body.get("system"));
        assertNotNull(body.get("messages"));
    }

    @Test
    void fallbackThrowsRuntimeException() {
        AnthropicClient client = new AnthropicClient(restTemplate, API_KEY);

        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Hello")
        );

        assertThrows(RuntimeException.class, () ->
                client.sendMessage("claude-haiku-4-5", "System", messages, 2048));
    }
}
