package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.domain.model.PaymentTransaction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentRepository implements PaymentRepositoryPort {

    private final Map<UUID, PaymentTransaction> store = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean saveIfNew(PaymentTransaction transaction) {
        if (existsBySepayId(transaction.sepayId())) {
            return false;
        }
        store.put(transaction.id(), transaction);
        return true;
    }

    @Override
    public Optional<PaymentTransaction> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentTransaction> findBySepayId(Long sepayId) {
        if (sepayId == null) {
            return Optional.empty();
        }
        return store.values().stream()
            .filter(t -> sepayId.equals(t.sepayId()))
            .findFirst();
    }

    @Override
    public boolean existsBySepayId(Long sepayId) {
        if (sepayId == null) {
            return false;
        }
        return store.values().stream()
            .anyMatch(t -> sepayId.equals(t.sepayId()));
    }

    @Override
    public List<PaymentTransaction> findAll() {
        return store.values().stream()
            .sorted(Comparator.comparing(PaymentTransaction::createdAt).reversed())
            .toList();
    }

    @Override
    public List<PaymentTransaction> findByContractIds(Collection<UUID> contractIds) {
        return findAll().stream()
            .filter(t -> t.contractId() != null && contractIds.contains(t.contractId()))
            .toList();
    }

    @Override
    public List<PaymentTransaction> findByInvoiceId(UUID invoiceId) {
        return store.values().stream()
            .filter(t -> invoiceId.equals(t.invoiceId()))
            .sorted(Comparator.comparing(PaymentTransaction::createdAt))
            .toList();
    }

    public void clear() {
        store.clear();
    }
}
