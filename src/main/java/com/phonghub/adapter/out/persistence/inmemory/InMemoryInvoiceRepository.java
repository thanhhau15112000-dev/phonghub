package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    public List<Invoice> findPriorRentByContractId(UUID contractId, YearMonth beforePeriod, int periodLimit) {
        if (contractId == null || beforePeriod == null || periodLimit <= 0) {
            return List.of();
        }
        LinkedHashMap<YearMonth, Invoice> latestByPeriod = new LinkedHashMap<>();
        store.values().stream()
            .filter(invoice -> contractId.equals(invoice.getContractId()))
            .filter(invoice -> invoice.getType() == InvoiceType.RENT)
            .filter(invoice -> invoice.getStatus() != InvoiceStatus.VOIDED && invoice.getStatus() != InvoiceStatus.DRAFT)
            .filter(invoice -> YearMonth.of(invoice.getYear(), invoice.getMonth()).isBefore(beforePeriod))
            .sorted(Comparator.comparing(Invoice::getCreatedAt).reversed())
            .forEach(invoice -> latestByPeriod.putIfAbsent(YearMonth.of(invoice.getYear(), invoice.getMonth()), invoice));
        return latestByPeriod.values().stream()
            .sorted(Comparator.comparing((Invoice invoice) -> YearMonth.of(invoice.getYear(), invoice.getMonth())).reversed())
            .limit(periodLimit)
            .toList();
    }

    @Override
    public List<Invoice> findPayableDueBetween(LocalDate from, LocalDate to) {
        return store.values().stream()
            .filter(i -> i.isPayable() && !i.getDueDate().isBefore(from) && !i.getDueDate().isAfter(to))
            .toList();
    }

    @Override
    public Optional<Invoice> findActiveByTicketId(UUID ticketId) {
        return store.values().stream()
            .filter(i -> ticketId.equals(i.getTicketId()) && i.getStatus() != InvoiceStatus.VOIDED)
            .findFirst();
    }

    @Override
    public Optional<Invoice> findActiveRentByContractIdAndPeriod(UUID contractId, int year, int month) {
        return store.values().stream()
            .filter(i -> i.getContractId().equals(contractId)
                && i.getType() == InvoiceType.RENT
                && i.getYear() == year
                && i.getMonth() == month
                && i.getStatus() != InvoiceStatus.VOIDED)
            .findFirst();
    }

    @Override
    public Optional<Invoice> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public List<UUID> findOverdueRentInvoiceIds(java.time.LocalDate today) {
        return store.values().stream()
            .filter(i -> i.getType() == InvoiceType.RENT && i.isOverdue(today))
            .map(Invoice::getId)
            .toList();
    }

    @Override
    public List<UUID> findOverdueMaintenanceInvoiceIds(java.time.LocalDate today) {
        return store.values().stream()
            .filter(i -> i.getType() == InvoiceType.MAINTENANCE && !i.isConsolidated() && i.isOverdue(today))
            .map(Invoice::getId)
            .toList();
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

    /** Toàn bộ hóa đơn, dùng cho thống kê in-memory (#93). */
    public List<Invoice> findAll() {
        return List.copyOf(store.values());
    }

    public void clear() {
        store.clear();
    }
}
