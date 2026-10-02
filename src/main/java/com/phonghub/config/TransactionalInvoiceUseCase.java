package com.phonghub.config;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalInvoiceUseCase implements InvoiceUseCase {

    private final InvoiceUseCase delegate;

    public TransactionalInvoiceUseCase(InvoiceUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Invoice> listInvoicesForContract(UUID contractId) {
        return delegate.listInvoicesForContract(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Invoice issueMonthlyInvoice(UUID contractId, YearMonth period) {
        return delegate.issueMonthlyInvoice(contractId, period);
    }
}
