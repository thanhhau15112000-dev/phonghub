package com.phonghub.config;

import com.phonghub.application.port.in.SepayWebhookUseCase;
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
    @Transactional(rollbackFor = Exception.class)
    public SandboxCheckout createSandboxCheckout(UUID invoiceId) {
        return delegate.createSandboxCheckout(invoiceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SandboxIpnResult processSandboxIpn(String suppliedSecret, SandboxIpnCommand command) {
        return delegate.processSandboxIpn(suppliedSecret, command);
    }
}
