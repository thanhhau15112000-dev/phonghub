package com.phonghub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phonghub.ai")
public class AiAssistantProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:4000";
    private String apiKey = "";
    private String model = "gemini-3.8-flash";
    private String embeddingModel = "gemini-faq-embedding";
    private int embeddingDimensions = 768;
    private int timeoutSeconds = 20;
    private int maxOutputTokens = 600;
    /** Gửi kèm reasoning_effort cho mô hình; để trống thì không gửi (OpenAI gpt-4o-mini không nhận tham số này, #322). */
    private String reasoningEffort = "low";
    private int comparisonPeriods = 3;
    private int faqMaxResults = 3;
    private double faqMinimumSimilarity = 0.65;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
    public int getComparisonPeriods() { return comparisonPeriods; }
    public void setComparisonPeriods(int comparisonPeriods) { this.comparisonPeriods = comparisonPeriods; }
    public int getFaqMaxResults() { return faqMaxResults; }
    public void setFaqMaxResults(int faqMaxResults) { this.faqMaxResults = faqMaxResults; }
    public double getFaqMinimumSimilarity() { return faqMinimumSimilarity; }
    public void setFaqMinimumSimilarity(double faqMinimumSimilarity) { this.faqMinimumSimilarity = faqMinimumSimilarity; }
}
