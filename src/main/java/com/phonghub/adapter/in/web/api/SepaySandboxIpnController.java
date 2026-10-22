package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.SepaySandboxIpnRequest;
import com.phonghub.adapter.in.web.api.dto.SepaySandboxIpnResponse;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedWebhookException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SepaySandboxIpnController {

    private final SepayWebhookUseCase sepayWebhookUseCase;

    public SepaySandboxIpnController(SepayWebhookUseCase sepayWebhookUseCase) {
        this.sepayWebhookUseCase = sepayWebhookUseCase;
    }

    @PostMapping("/ipn")
    public ResponseEntity<SepaySandboxIpnResponse> receiveIpn(
        @RequestHeader(value = "X-Secret-Key", required = false) String secret,
        @RequestBody SepaySandboxIpnRequest request
    ) {
        try {
            var result = sepayWebhookUseCase.processSandboxIpn(secret, request.toCommand());
            return ResponseEntity.ok(SepaySandboxIpnResponse.ok(result.status(), result.duplicate()));
        } catch (UnauthorizedWebhookException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SepaySandboxIpnResponse.error(ex.getMessage()));
        } catch (DomainException ex) {
            return ResponseEntity.badRequest().body(SepaySandboxIpnResponse.error(ex.getMessage()));
        }
    }
}
