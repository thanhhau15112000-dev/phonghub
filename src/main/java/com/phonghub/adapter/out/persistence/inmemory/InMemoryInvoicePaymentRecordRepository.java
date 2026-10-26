package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.domain.model.InvoicePaymentRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInvoicePaymentRecordRepository implements InvoicePaymentRecordRepositoryPort {
    private final Map<UUID, InvoicePaymentRecord> store = new ConcurrentHashMap<>();

    @Override
    public InvoicePaymentRecord save(InvoicePaymentRecord record) {
        store.put(record.id(), record);
        return record;
    }

    @Override
    public List<InvoicePaymentRecord> findByInvoiceId(UUID invoiceId) {
        return store.values().stream().filter(record -> record.invoiceId().equals(invoiceId))
            .sorted(java.util.Comparator.comparing(InvoicePaymentRecord::paidAt)).toList();
    }

    @Override
    public List<InvoicePaymentRecord> findByContractIds(Collection<UUID> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) return List.of();
        return store.values().stream().filter(record -> contractIds.contains(record.contractId()))
            .sorted(java.util.Comparator.comparing(InvoicePaymentRecord::paidAt).reversed()).toList();
    }

    public void clear() {
        store.clear();
    }
}
