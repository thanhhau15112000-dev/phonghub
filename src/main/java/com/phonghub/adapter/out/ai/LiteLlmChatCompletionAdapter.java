package com.phonghub.adapter.out.ai;

import tools.jackson.databind.JsonNode;
import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.out.AiCompletionPort;
import com.phonghub.config.AiAssistantProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** OpenAI-compatible LiteLLM adapter. Request and response bodies are never logged. */
public class LiteLlmChatCompletionAdapter implements AiCompletionPort {
    private final AiAssistantProperties properties;
    private final RestClient restClient;

    public LiteLlmChatCompletionAdapter(AiAssistantProperties properties) {
        this.properties = properties;
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())).toMillis());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiUnavailableException();
        }
        try {
            JsonNode response = restClient.post()
                .uri(normalizedBaseUrl() + "/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", Math.max(64, Math.min(properties.getMaxOutputTokens(), 1200)),
                    "temperature", 0.2,
                    "reasoning_effort", "low",
                    "stream", false
                ))
                .retrieve()
                .body(JsonNode.class);
            String answer = response == null ? null : response.path("choices").path(0).path("message").path("content").asText();
            if (answer == null || answer.isBlank() || answer.length() > 8000) {
                throw new AiUnavailableException();
            }
            return answer.trim();
        } catch (RestClientException | ArithmeticException ex) {
            throw new AiUnavailableException();
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiUnavailableException();
        }
        return baseUrl.replaceAll("/+$", "");
    }
}
