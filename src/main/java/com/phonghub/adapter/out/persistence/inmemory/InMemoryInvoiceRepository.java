package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInvoiceRepository implements InvoiceRepositoryPort {

    private final Map<UUID, Invoice> store = new ConcurrentHashMap<>();

    @Override
    public Invoice save(Invoice invoice) {
        store.put(invoice.getId(), invoice);
        return invoice;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Invoice> findByContractId(UUID contractId) {
        return store.values().stream()
            .filter(i -> i.getContractId().equals(contractId))
            .toList();
    }

    @Override
    public Optional<Invoice> findActiveByContractIdAndPeriod(UUID contractId, int year, int month) {
        return store.values().stream()
            .filter(i -> i.getContractId().equals(contractId)
                && i.getYear() == year
                && i.getMonth() == month
                && i.getStatus() != InvoiceStatus.VOIDED)
            .findFirst();
    }

    @Override
    public Optional<Invoice> findByPaymentCodeForUpdate(String paymentCode) {
        return store.values().stream()
            .filter(i -> paymentCode.equals(i.getPaymentCode()))
            .findFirst();
    }

    @Override
    public boolean existsByPaymentCode(String paymentCode) {
        return findByPaymentCodeForUpdate(paymentCode).isPresent();
    }

    public void clear() {
        store.clear();
    }
}
