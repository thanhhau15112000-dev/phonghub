package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.SepayWebhookRequest;
import com.phonghub.adapter.in.web.api.dto.SepayWebhookResponse;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import com.phonghub.domain.model.PaymentTransaction;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class SepayWebhookController {

    private final SepayWebhookUseCase sepayWebhookUseCase;

    public SepayWebhookController(SepayWebhookUseCase sepayWebhookUseCase) {
        this.sepayWebhookUseCase = sepayWebhookUseCase;
    }

    @PostMapping("/sepay/webhook")
    public ResponseEntity<SepayWebhookResponse> handleSepayWebhook(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @Valid @RequestBody SepayWebhookRequest request
    ) {
        try {
            var command = new SepayWebhookUseCase.SepayWebhookCommand(
                request.id(),
                request.gateway(),
                request.transactionDate(),
                request.accountNumber(),
                request.subAccount(),
                request.code(),
                request.content(),
                request.transferType(),
                request.transferAmount(),
                request.accumulated(),
                request.effectiveReferenceCode(),
                request.description()
            );

            var result = sepayWebhookUseCase.processWebhook(authHeader, command);
            UUID paymentId = result.transaction() != null ? result.transaction().id() : null;
            return ResponseEntity.ok(SepayWebhookResponse.ok(result.message(), paymentId));
        } catch (UnauthorizedWebhookException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SepayWebhookResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SepayWebhookResponse.error("Lỗi xử lý webhook: " + ex.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<PaymentTransaction>> listTransactions() {
        return ResponseEntity.ok(sepayWebhookUseCase.getAllTransactions());
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<PaymentTransaction> getTransaction(@PathVariable UUID id) {
        Optional<PaymentTransaction> tx = sepayWebhookUseCase.getTransactionById(id);
        return tx.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
