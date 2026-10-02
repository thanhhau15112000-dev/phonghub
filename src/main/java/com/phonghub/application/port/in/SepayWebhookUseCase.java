package com.phonghub.application.port.in;

import com.phonghub.domain.model.PaymentTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

    WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command);

    List<PaymentTransaction> getAllTransactions();

    Optional<PaymentTransaction> getTransactionById(UUID id);
}
