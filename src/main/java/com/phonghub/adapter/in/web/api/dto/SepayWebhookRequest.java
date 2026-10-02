package com.phonghub.adapter.in.web.api.dto;

import java.math.BigDecimal;

public record SepayWebhookRequest(
    Long id,
    String gateway,
    String transactionDate,
    String accountNumber,
    String subAccount,
    String code,
    String content,
    String transferType,
    BigDecimal transferAmount,
    BigDecimal accumulated,
    String referenceCode,
    String referenceNumber,
    String description
) {
    public String effectiveReferenceCode() {
        return referenceCode != null && !referenceCode.isBlank() ? referenceCode : referenceNumber;
    }
}
