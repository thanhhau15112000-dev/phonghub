package com.phonghub.adapter.out.persistence.inmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryInvoicePriorPeriodsTest {
    @Test
    void returnsLatestDistinctPriorRentPeriodsForSameRoomOnly() {
        InMemoryInvoiceRepository repository = new InMemoryInvoiceRepository();
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Invoice newestSeptember = invoice(roomId, contractId, tenantId, 9, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-10-01T00:00:00Z");
        repository.save(invoice(roomId, contractId, tenantId, 9, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-09-30T00:00:00Z"));
        repository.save(newestSeptember);
        repository.save(invoice(roomId, contractId, tenantId, 8, 2026, InvoiceStatus.DRAFT,
            InvoiceType.RENT, "2026-09-01T00:00:00Z"));
        repository.save(invoice(roomId, UUID.randomUUID(), tenantId, 8, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-09-02T00:00:00Z"));
        repository.save(invoice(roomId, contractId, tenantId, 7, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-08-01T00:00:00Z"));
        repository.save(invoice(roomId, contractId, tenantId, 6, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-07-01T00:00:00Z"));
        repository.save(invoice(roomId, contractId, tenantId, 5, 2026, InvoiceStatus.VOIDED,
            InvoiceType.RENT, "2026-06-01T00:00:00Z"));
        repository.save(invoice(otherRoomId, UUID.randomUUID(), tenantId, 9, 2026, InvoiceStatus.ISSUED,
            InvoiceType.RENT, "2026-10-02T00:00:00Z"));
        repository.save(invoice(roomId, contractId, tenantId, 9, 2026, InvoiceStatus.ISSUED,
            InvoiceType.MAINTENANCE, "2026-10-03T00:00:00Z"));

        List<Invoice> periods = repository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 10), 3);

        assertEquals(3, periods.size());
        assertEquals(List.of(9, 8, 7), periods.stream().map(Invoice::getMonth).toList());
        assertEquals(newestSeptember.getId(), periods.getFirst().getId());
    }

    private static Invoice invoice(UUID roomId, UUID contractId, UUID tenantId, int month, int year,
                                   InvoiceStatus status, InvoiceType type, String createdAt) {
        Instant at = Instant.parse(createdAt);
        return new Invoice(UUID.randomUUID(), contractId, roomId, tenantId, month, year,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, LocalDate.of(year, month, 28), status,
            "PH" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), type,
            type == InvoiceType.MAINTENANCE ? UUID.randomUUID() : null, null, at, at);
    }
}
