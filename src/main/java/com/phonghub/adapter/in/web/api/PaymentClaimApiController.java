package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.ManualPaymentUseCase;
import com.phonghub.domain.model.PaymentClaim;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Xác nhận thanh toán thủ công (#205). Người thu tiền: chủ trọ và staff được cấp quyền thu tiền; quản trị viên không thao tác. */
@RestController
@RequestMapping("/api")
public class PaymentClaimApiController {

    private final ManualPaymentUseCase manualPayments;

    public PaymentClaimApiController(ManualPaymentUseCase manualPayments) {
        this.manualPayments = manualPayments;
    }

    /** Người thuê báo đã thanh toán, chờ xác nhận. */
    @PostMapping("/invoices/{invoiceId}/payment-claims")
    public ResponseEntity<PaymentClaimResponse> submit(
        @PathVariable UUID invoiceId,
        @Valid @RequestBody PaymentRequest request
    ) {
        PaymentClaimResponse body = PaymentClaimResponse.from(
            manualPayments.submitClaim(invoiceId, request.amount(), request.method(), request.note()));
        return ResponseEntity.created(URI.create("/api/payment-claims/" + body.id())).body(body);
    }

    /** Người thu tiền ghi nhận trực tiếp một khoản đã thu. */
    @PostMapping("/invoices/{invoiceId}/manual-payments")
    public ResponseEntity<PaymentClaimResponse> record(
        @PathVariable UUID invoiceId,
        @Valid @RequestBody PaymentRequest request
    ) {
        PaymentClaimResponse body = PaymentClaimResponse.from(
            manualPayments.recordPayment(invoiceId, request.amount(), request.method(), request.note()));
        return ResponseEntity.created(URI.create("/api/payment-claims/" + body.id())).body(body);
    }

    @GetMapping("/properties/{propertyId}/payment-claims")
    public ResponseEntity<List<PaymentClaimResponse>> pending(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(manualPayments.listPending(propertyId).stream().map(PaymentClaimResponse::from).toList());
    }

    @GetMapping("/contracts/{contractId}/payment-claims")
    public ResponseEntity<List<PaymentClaimResponse>> forContract(@PathVariable UUID contractId) {
        return ResponseEntity.ok(manualPayments.listForContract(contractId).stream().map(PaymentClaimResponse::from).toList());
    }

    @PostMapping("/payment-claims/{claimId}/confirm")
    public ResponseEntity<PaymentClaimResponse> confirm(@PathVariable UUID claimId) {
        return ResponseEntity.ok(PaymentClaimResponse.from(manualPayments.confirm(claimId)));
    }

    @PostMapping("/payment-claims/{claimId}/reject")
    public ResponseEntity<PaymentClaimResponse> reject(
        @PathVariable UUID claimId,
        @Valid @RequestBody RejectRequest request
    ) {
        return ResponseEntity.ok(PaymentClaimResponse.from(manualPayments.reject(claimId, request.reason())));
    }

    public record PaymentRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Method is required")
        PaymentClaim.Method method,

        String note
    ) {}

    public record RejectRequest(@NotBlank(message = "Reason is required") String reason) {}

    public record PaymentClaimResponse(
        UUID id,
        UUID invoiceId,
        UUID contractId,
        BigDecimal amount,
        PaymentClaim.Method method,
        String note,
        PaymentClaim.Source source,
        PaymentClaim.Status status,
        Instant createdAt,
        Instant decidedAt,
        String rejectReason
    ) {
        static PaymentClaimResponse from(PaymentClaim claim) {
            return new PaymentClaimResponse(claim.id(), claim.invoiceId(), claim.contractId(), claim.amount(),
                claim.method(), claim.note(), claim.source(), claim.status(), claim.createdAt(), claim.decidedAt(),
                claim.rejectReason());
        }
    }
}
