package com.phonghub.application.port.out;

import com.phonghub.domain.model.InvoicePaymentRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvoicePaymentRecordRepositoryPort {
    InvoicePaymentRecord save(InvoicePaymentRecord record);
    List<InvoicePaymentRecord> findByInvoiceId(UUID invoiceId);
    List<InvoicePaymentRecord> findByContractIds(Collection<UUID> contractIds);
}
