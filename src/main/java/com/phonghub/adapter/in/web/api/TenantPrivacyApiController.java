package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.TenantPrivacyUseCase;
import com.phonghub.application.port.in.TenantPrivacyUseCase.PrivacyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TenantPrivacyApiController {

    private final TenantPrivacyUseCase useCase;

    public TenantPrivacyApiController(TenantPrivacyUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/properties/{propertyId}/tenants/{tenantId}/privacy")
    public PrivacyStatus status(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return useCase.status(propertyId, tenantId);
    }

    @PutMapping("/properties/{propertyId}/tenants/{tenantId}/legal-hold")
    public PrivacyStatus setLegalHold(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @Valid @RequestBody LegalHoldRequest request
    ) {
        return useCase.setLegalHold(propertyId, tenantId, request.reason());
    }

    @DeleteMapping("/properties/{propertyId}/tenants/{tenantId}/legal-hold")
    public PrivacyStatus clearLegalHold(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return useCase.clearLegalHold(propertyId, tenantId);
    }

    /** Người thuê tự yêu cầu xóa sớm giấy tờ của mình (phần hợp đồng, hóa đơn phải lưu theo luật vẫn được giữ). */
    @PostMapping("/tenants/me/erasure-request")
    public Map<String, String> requestErasure() {
        return Map.of("outcome", useCase.requestErasure().name());
    }

    public record LegalHoldRequest(@NotBlank(message = "Cần nêu lý do giữ pháp lý") String reason) {}
}
