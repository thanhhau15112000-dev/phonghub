package com.phonghub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phonghub.sepay")
public class SepayProperties {

    /**
     * API Key configured in SePay Webhook Settings (Step 3: Security).
     */
    private String webhookApiKey;

    /**
     * Chỉ bật cho local/test: cho phép nhận webhook khi chưa cấu hình API key.
     * Mặc định false — thiếu key thì webhook bị từ chối (fail-closed).
     */
    private boolean allowUnsignedWebhook;

    /** Mã ngân hàng theo SePay QR (ví dụ: MBBank, Vietcombank). */
    private String bankCode;

    /** Số tài khoản nhận tiền đang được SePay theo dõi. */
    private String bankAccountNumber;

    /** Tên chủ tài khoản hiển thị cho người thuê. */
    private String bankAccountName;

    public String getWebhookApiKey() {
        return webhookApiKey;
    }

    public void setWebhookApiKey(String webhookApiKey) {
        this.webhookApiKey = webhookApiKey;
    }

    public boolean isAllowUnsignedWebhook() {
        return allowUnsignedWebhook;
    }

    public void setAllowUnsignedWebhook(boolean allowUnsignedWebhook) {
        this.allowUnsignedWebhook = allowUnsignedWebhook;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public boolean hasBankAccount() {
        return bankCode != null && !bankCode.isBlank()
            && bankAccountNumber != null && !bankAccountNumber.isBlank();
    }
}
