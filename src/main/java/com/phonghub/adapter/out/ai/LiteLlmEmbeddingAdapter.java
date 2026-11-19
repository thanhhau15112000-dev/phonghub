package com.phonghub.adapter.out.ai;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.out.AiEmbeddingPort;
import com.phonghub.config.AiAssistantProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** LiteLLM/OpenAI-compatible embedding adapter; questions and vectors are never logged. */
public class LiteLlmEmbeddingAdapter implements AiEmbeddingPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiteLlmEmbeddingAdapter.class);
    private final AiAssistantProperties properties;
    private final RestClient restClient;

    public LiteLlmEmbeddingAdapter(AiAssistantProperties properties) {
        this(properties, RestClient.builder());
    }

    LiteLlmEmbeddingAdapter(AiAssistantProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        int timeoutMillis = Math.max(1000, properties.getTimeoutSeconds() * 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    @Override
    public double[] embed(String text) {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()
            || text == null || text.isBlank()) {
            throw new AiUnavailableException();
        }
        try {
            JsonNode response = restClient.post()
                .uri(normalizedBaseUrl() + "/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(Map.of(
                    "model", properties.getEmbeddingModel(),
                    "input", text,
                    "encoding_format", "float",
                    "dimensions", properties.getEmbeddingDimensions()
                ))
                .retrieve()
                .body(JsonNode.class);
            JsonNode vector = response == null ? null : response.path("data").path(0).path("embedding");
            if (vector == null || !vector.isArray() || vector.size() != properties.getEmbeddingDimensions()) {
                LOGGER.warn("Mô hình embedding trả vector sai kích thước: model={}, expected={}, actual={}",
                    properties.getEmbeddingModel(), properties.getEmbeddingDimensions(),
                    vector == null || !vector.isArray() ? -1 : vector.size());
                throw new AiUnavailableException();
            }
            double[] values = new double[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                JsonNode value = vector.get(i);
                if (value == null || !value.isNumber()) {
                    throw new AiUnavailableException();
                }
                values[i] = value.doubleValue();
                if (!Double.isFinite(values[i])) {
                    throw new AiUnavailableException();
                }
            }
            return values;
        } catch (RestClientException ex) {
            LOGGER.warn("Gọi mô hình embedding thất bại: model={}, {}", properties.getEmbeddingModel(),
                AiCallFailure.describe(ex));
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
