package com.phonghub.application.port.in;

import com.phonghub.domain.model.PaymentTransaction;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface SepayWebhookUseCase {

    record SepayWebhookCommand(
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
        String description
    ) {}

    record WebhookProcessResult(
        boolean success,
        String message,
        PaymentTransaction transaction
    ) {}

    record SandboxCheckout(String action, Map<String, String> fields) {}
    record SandboxOrder(String orderStatus, String orderCurrency, String orderAmount, String orderInvoiceNumber) {}
    record SandboxTransaction(
        String transactionType,
        String transactionStatus,
        String transactionCurrency,
        String transactionId,
        String transactionAmount
    ) {}
    record SandboxIpnCommand(String notificationType, SandboxOrder order, SandboxTransaction transaction) {}
    record SandboxIpnResult(String status, boolean duplicate) {}

    WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command);

    SandboxCheckout createSandboxCheckout(UUID invoiceId);

    SandboxIpnResult processSandboxIpn(String suppliedSecret, SandboxIpnCommand command);
}
