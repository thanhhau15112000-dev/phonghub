package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.Receipt;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationFilter;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationReport;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đối soát giao dịch SePay và biên lai (#82). Phân quyền nằm ở {@link PaymentReconciliationUseCase}. */
@RestController
public class PaymentReconciliationApiController {

    private final PaymentReconciliationUseCase useCase;

    public PaymentReconciliationApiController(PaymentReconciliationUseCase useCase) {
        this.useCase = useCase;
    }

    /** Ngày lọc dạng {@code yyyy-MM-dd} theo giờ Việt Nam, bao gồm hai đầu. */
    @GetMapping("/api/v1/payments/reconciliation")
    public ResponseEntity<ReconciliationReport> reconciliation(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) ReconciliationStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(useCase.listTransactions(new ReconciliationFilter(propertyId, status, from, to)));
    }

    @GetMapping("/api/invoices/{invoiceId}/receipt")
    public ResponseEntity<Receipt> receipt(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(useCase.getReceipt(invoiceId));
    }
}
