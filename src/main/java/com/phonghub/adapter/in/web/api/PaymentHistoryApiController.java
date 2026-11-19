package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.PaymentHistoryUseCase;
import com.phonghub.application.port.in.PaymentHistoryUseCase.Filter;
import com.phonghub.application.port.in.PaymentHistoryUseCase.PaymentHistory;
import com.phonghub.application.port.in.PaymentHistoryUseCase.Status;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lịch sử thanh toán của người thuê (#248). Phân quyền nằm ở {@link PaymentHistoryUseCase}. */
@RestController
public class PaymentHistoryApiController {

    private final PaymentHistoryUseCase useCase;

    public PaymentHistoryApiController(PaymentHistoryUseCase useCase) {
        this.useCase = useCase;
    }

    /** Người thuê xem lịch sử của chính mình. Ngày lọc dạng {@code yyyy-MM-dd} theo giờ Việt Nam, gồm hai đầu. */
    @GetMapping("/api/me/payments")
    public ResponseEntity<PaymentHistory> mine(
        @RequestParam(required = false) UUID contractId,
        @RequestParam(required = false) Status status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(useCase.history(null, new Filter(contractId, status, from, to, page, size)));
    }

    /** Chủ trọ hoặc nhân viên có quyền thu tiền xem lịch sử của một người thuê trong nhà trọ của họ. */
    @GetMapping("/api/tenants/{tenantId}/payments")
    public ResponseEntity<PaymentHistory> ofTenant(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) UUID contractId,
        @RequestParam(required = false) Status status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(useCase.history(tenantId, new Filter(contractId, status, from, to, page, size)));
    }
}
