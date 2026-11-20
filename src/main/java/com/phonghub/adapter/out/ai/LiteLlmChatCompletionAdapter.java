package com.phonghub.adapter.out.ai;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.out.AiCompletionPort;
import com.phonghub.config.AiAssistantProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** OpenAI-compatible chat completion adapter with Groq/fallback support. Prompts & answers are never logged. */
public class LiteLlmChatCompletionAdapter implements AiCompletionPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiteLlmChatCompletionAdapter.class);
    private final AiAssistantProperties properties;
    private final RestClient restClient;

    public LiteLlmChatCompletionAdapter(AiAssistantProperties properties) {
        this.properties = properties;
        this.restClient = AiEndpointSupport.buildRestClient(properties.getTimeoutSeconds());
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, List.of(), userPrompt);
    }

    @Override
    public String complete(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException();
        }
        boolean hasPrimary = hasText(properties.getBaseUrl()) && hasText(properties.getApiKey()) && hasText(properties.getModel());
        boolean hasFallback = hasText(properties.getFallbackBaseUrl())
            && hasText(properties.getFallbackApiKey())
            && hasText(properties.getFallbackModel());
        if (!hasPrimary && !hasFallback) {
            throw new AiUnavailableException();
        }

        List<Map<String, String>> messages = buildMessages(systemPrompt, history, userPrompt);

        if (hasPrimary) {
            boolean sendReasoning = shouldSendReasoningEffort(properties.getBaseUrl(), properties.getModel(), properties.getReasoningEffort());
            String answer = callWithTransientRetry(
                properties.getBaseUrl(), properties.getApiKey(), properties.getModel(),
                messages, sendReasoning, !hasFallback);
            if (answer != null) {
                return answer;
            }
        }

        if (hasFallback) {
            boolean fallbackReasoning = shouldSendReasoningEffort(
                properties.getFallbackBaseUrl(), properties.getFallbackModel(), "");
            String answer = callProviderOnce(
                properties.getFallbackBaseUrl(), properties.getFallbackApiKey(), properties.getFallbackModel(),
                messages, fallbackReasoning, true);
            if (answer != null) {
                return answer;
            }
        }
        throw new AiUnavailableException();
    }

    private String callWithTransientRetry(String baseUrl, String apiKey, String model,
                                          List<Map<String, String>> messages,
                                          boolean sendReasoning, boolean logOnFinalFailure) {
        try {
            return executeCall(baseUrl, apiKey, model, messages, sendReasoning);
        } catch (TransientProviderFailure ex) {
            pauseBriefly();
            try {
                return executeCall(baseUrl, apiKey, model, messages, sendReasoning);
            } catch (TransientProviderFailure retryEx) {
                if (logOnFinalFailure) {
                    LOGGER.warn("Gọi mô hình AI thất bại: model={}, {}", model, retryEx.summary);
                }
                return null;
            } catch (BlankAnswerFailure blankEx) {
                if (logOnFinalFailure) {
                    LOGGER.warn("Mô hình AI trả về câu trả lời rỗng hoặc quá dài: model={}, length={}", model, blankEx.length);
                }
                return null;
            } catch (NonRetryableFailure hardEx) {
                if (logOnFinalFailure) {
                    LOGGER.warn("Gọi mô hình AI thất bại: model={}, {}", model, hardEx.summary);
                }
                return null;
            }
        } catch (BlankAnswerFailure ex) {
            if (sendReasoning) {
                try {
                    return executeCall(baseUrl, apiKey, model, messages, false);
                } catch (ProviderCallFailure retryEx) {
                    // fall through to log
                }
            }
            if (logOnFinalFailure) {
                LOGGER.warn("Mô hình AI trả về câu trả lời rỗng hoặc quá dài: model={}, length={}", model, ex.length);
            }
            return null;
        } catch (NonRetryableFailure ex) {
            if (logOnFinalFailure) {
                LOGGER.warn("Gọi mô hình AI thất bại: model={}, {}", model, ex.summary);
            }
            return null;
        }
    }

    private String callProviderOnce(String baseUrl, String apiKey, String model,
                                    List<Map<String, String>> messages,
                                    boolean sendReasoning, boolean logOnFailure) {
        try {
            return executeCall(baseUrl, apiKey, model, messages, sendReasoning);
        } catch (BlankAnswerFailure ex) {
            if (logOnFailure) {
                LOGGER.warn("Mô hình AI dự phòng trả về câu trả lời rỗng hoặc quá dài: model={}, length={}", model, ex.length);
            }
            return null;
        } catch (ProviderCallFailure ex) {
            if (logOnFailure) {
                LOGGER.warn("Gọi mô hình AI dự phòng thất bại: model={}, {}", model, ex.summary);
            }
            return null;
        }
    }

    private String executeCall(String baseUrl, String apiKey, String model,
                               List<Map<String, String>> messages, boolean sendReasoning)
        throws TransientProviderFailure, NonRetryableFailure, BlankAnswerFailure {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", Math.max(64, Math.min(properties.getMaxOutputTokens(), 2048)));
        body.put("temperature", 0.2);
        if (sendReasoning && hasText(properties.getReasoningEffort())) {
            body.put("reasoning_effort", properties.getReasoningEffort().trim());
        }
        body.put("stream", false);

        JsonNode response;
        try {
            response = restClient.post()
                .uri(AiEndpointSupport.resolveEndpoint(baseUrl, "chat/completions"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            String desc = AiCallFailure.describe(ex);
            if (code == 429 || code >= 500) {
                throw new TransientProviderFailure(desc);
            }
            throw new NonRetryableFailure(desc);
        } catch (RestClientException ex) {
            throw new TransientProviderFailure(AiCallFailure.describe(ex));
        }

        String answer = response == null ? null : response.path("choices").path(0).path("message").path("content").asText();
        if (answer == null || answer.isBlank() || answer.length() > 8000) {
            throw new BlankAnswerFailure(answer == null ? -1 : answer.length());
        }
        return answer.trim();
    }

    private static List<Map<String, String>> buildMessages(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) {
            for (ChatTurn turn : history) {
                if (turn != null && hasText(turn.role()) && hasText(turn.content())) {
                    String role = "assistant".equalsIgnoreCase(turn.role().trim()) ? "assistant" : "user";
                    messages.add(Map.of("role", role, "content", turn.content()));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        return messages;
    }

    private static boolean shouldSendReasoningEffort(String baseUrl, String model, String reasoningEffort) {
        if (!hasText(reasoningEffort)) {
            return false;
        }
        String lowerUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        String lowerModel = model == null ? "" : model.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("groq.com") && !lowerModel.contains("gpt-oss") && !lowerModel.contains("qwen")) {
            return false;
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void pauseBriefly() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static sealed class ProviderCallFailure extends Exception permits TransientProviderFailure, NonRetryableFailure, BlankAnswerFailure {
        final String summary;

        ProviderCallFailure(String summary) {
            this.summary = summary;
        }
    }

    private static final class TransientProviderFailure extends ProviderCallFailure {
        TransientProviderFailure(String summary) {
            super(summary);
        }
    }

    private static final class NonRetryableFailure extends ProviderCallFailure {
        NonRetryableFailure(String summary) {
            super(summary);
        }
    }

    private static final class BlankAnswerFailure extends ProviderCallFailure {
        final int length;

        BlankAnswerFailure(int length) {
            super("blank");
            this.length = length;
        }
    }
}
