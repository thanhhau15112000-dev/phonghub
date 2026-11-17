package com.phonghub.adapter.out.ai;

import tools.jackson.databind.JsonNode;
import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.out.AiCompletionPort;
import com.phonghub.config.AiAssistantProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** OpenAI-compatible LiteLLM adapter. Request and response bodies are never logged. */
public class LiteLlmChatCompletionAdapter implements AiCompletionPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiteLlmChatCompletionAdapter.class);
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
        String model = properties.getModel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
        body.put("max_tokens", Math.max(64, Math.min(properties.getMaxOutputTokens(), 1200)));
        body.put("temperature", 0.2);
        String reasoningEffort = properties.getReasoningEffort();
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort.trim());
        }
        body.put("stream", false);
        JsonNode response;
        try {
            response = restClient.post()
                .uri(normalizedBaseUrl() + "/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            // Chỉ ghi mô hình, mã HTTP và loại lỗi; không ghi câu hỏi, câu trả lời hay khóa (#322)
            LOGGER.warn("Gọi mô hình AI thất bại: model={}, {}", model, AiCallFailure.describe(ex));
            throw new AiUnavailableException();
        }
        String answer = response == null ? null : response.path("choices").path(0).path("message").path("content").asText();
        if (answer == null || answer.isBlank() || answer.length() > 8000) {
            LOGGER.warn("Mô hình AI trả về câu trả lời rỗng hoặc quá dài: model={}, length={}", model,
                answer == null ? -1 : answer.length());
            throw new AiUnavailableException();
        }
        return answer.trim();
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiUnavailableException();
        }
        return baseUrl.replaceAll("/+$", "");
    }
}
