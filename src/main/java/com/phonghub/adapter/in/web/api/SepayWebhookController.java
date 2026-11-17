package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.SepayWebhookRequest;
import com.phonghub.adapter.in.web.api.dto.SepayWebhookResponse;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import jakarta.validation.Valid;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class SepayWebhookController {

    private final SepayWebhookUseCase sepayWebhookUseCase;
    private final MeterRegistry meterRegistry;

    public SepayWebhookController(SepayWebhookUseCase sepayWebhookUseCase) {
        this(sepayWebhookUseCase, (ObjectProvider<MeterRegistry>) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SepayWebhookController(
        SepayWebhookUseCase sepayWebhookUseCase,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.sepayWebhookUseCase = sepayWebhookUseCase;
        this.meterRegistry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
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
            recordMetric("success");
            UUID paymentId = result.transaction() != null ? result.transaction().id() : null;
            return ResponseEntity.ok(SepayWebhookResponse.ok(result.message(), paymentId));
        } catch (UnauthorizedWebhookException ex) {
            recordMetric("unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SepayWebhookResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            recordMetric("error");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SepayWebhookResponse.error("Lỗi xử lý webhook: " + ex.getMessage()));
        }
    }

    private void recordMetric(String status) {
        if (meterRegistry != null) {
            meterRegistry.counter("phonghub.sepay.ipn", "status", status).increment();
        }
    }
}
