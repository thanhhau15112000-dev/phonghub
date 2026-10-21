package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.domain.model.Invoice;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thao tác trên một hóa đơn (#89): hiện chỉ có hủy hóa đơn tiền thuê chưa có tiền. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceApiController {

    private final InvoiceUseCase invoiceUseCase;

    public InvoiceApiController(InvoiceUseCase invoiceUseCase) {
        this.invoiceUseCase = invoiceUseCase;
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<VoidedInvoiceResponse> voidInvoice(
        @PathVariable UUID id,
        @RequestBody(required = false) VoidInvoiceRequest request
    ) {
        Invoice invoice = invoiceUseCase.voidInvoice(id, request == null ? null : request.reason());
        return ResponseEntity.ok(new VoidedInvoiceResponse(invoice.getId(), invoice.getContractId(), invoice.getYear(),
            invoice.getMonth(), invoice.getTotalAmount(), invoice.getStatus().name()));
    }

    public record VoidInvoiceRequest(String reason) {}

    public record VoidedInvoiceResponse(UUID id, UUID contractId, int year, int month, BigDecimal totalAmount,
                                        String status) {}
}
