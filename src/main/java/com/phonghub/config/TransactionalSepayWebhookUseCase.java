package com.phonghub.config;

import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.domain.model.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalSepayWebhookUseCase implements SepayWebhookUseCase {

    private final SepayWebhookUseCase delegate;

    public TransactionalSepayWebhookUseCase(SepayWebhookUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookProcessResult processWebhook(String authorizationHeader, SepayWebhookCommand command) {
        return delegate.processWebhook(authorizationHeader, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookProcessResult simulateTransfer(UUID invoiceId) {
        return delegate.simulateTransfer(invoiceId);
    }

    @Override
    public List<PaymentTransaction> getAllTransactions() {
        return delegate.getAllTransactions();
    }

    @Override
    public Optional<PaymentTransaction> getTransactionById(UUID id) {
        return delegate.getTransactionById(id);
    }
}
