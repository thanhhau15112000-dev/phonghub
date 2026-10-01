package com.phonghub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phonghub.sepay")
public class SepayProperties {

    /**
     * API Key configured in SePay Webhook Settings (Step 3: Security).
     * If blank or null, API Key check is bypassed for local/dev convenience.
     */
    private String webhookApiKey;

    public String getWebhookApiKey() {
        return webhookApiKey;
    }

    public void setWebhookApiKey(String webhookApiKey) {
        this.webhookApiKey = webhookApiKey;
    }
}
