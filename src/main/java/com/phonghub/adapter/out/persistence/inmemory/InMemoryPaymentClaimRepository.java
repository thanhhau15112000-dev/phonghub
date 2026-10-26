package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.PaymentClaimRepositoryPort;
import com.phonghub.domain.model.PaymentClaim;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentClaimRepository implements PaymentClaimRepositoryPort {

    private final Map<UUID, PaymentClaim> store = new ConcurrentHashMap<>();

    @Override
    public PaymentClaim save(PaymentClaim claim) {
        store.put(claim.id(), claim);
        return claim;
    }

    @Override
    public Optional<PaymentClaim> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentClaim> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public List<PaymentClaim> findByInvoiceId(UUID invoiceId) {
        return store.values().stream().filter(claim -> claim.invoiceId().equals(invoiceId))
            .sorted(Comparator.comparing(PaymentClaim::createdAt)).toList();
    }

    @Override
    public List<PaymentClaim> findByContractId(UUID contractId) {
        return store.values().stream().filter(claim -> claim.contractId().equals(contractId))
            .sorted(Comparator.comparing(PaymentClaim::createdAt).reversed()).toList();
    }

    @Override
    public List<PaymentClaim> findPendingByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(claim -> claim.propertyId().equals(propertyId) && claim.status() == PaymentClaim.Status.PENDING)
            .sorted(Comparator.comparing(PaymentClaim::createdAt)).toList();
    }

    public void clear() {
        store.clear();
    }
}
