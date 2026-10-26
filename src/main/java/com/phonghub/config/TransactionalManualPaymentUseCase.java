package com.phonghub.config;

import com.phonghub.application.port.in.ManualPaymentUseCase;
import com.phonghub.domain.model.PaymentClaim;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalManualPaymentUseCase implements ManualPaymentUseCase {

    private final ManualPaymentUseCase delegate;

    public TransactionalManualPaymentUseCase(ManualPaymentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentClaim submitClaim(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note) {
        return delegate.submitClaim(invoiceId, amount, method, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentClaim recordPayment(UUID invoiceId, BigDecimal amount, PaymentClaim.Method method, String note) {
        return delegate.recordPayment(invoiceId, amount, method, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentClaim confirm(UUID claimId) {
        return delegate.confirm(claimId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentClaim reject(UUID claimId, String reason) {
        return delegate.reject(claimId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentClaim> listPending(UUID propertyId) {
        return delegate.listPending(propertyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentClaim> listForContract(UUID contractId) {
        return delegate.listForContract(contractId);
    }
}
