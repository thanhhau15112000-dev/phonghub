package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.domain.model.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantApiController {

    private final TenantUseCase tenantUseCase;

    public TenantApiController(TenantUseCase tenantUseCase) {
        this.tenantUseCase = tenantUseCase;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        return tenantUseCase.getTenant(id)
            .map(TenantResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTenantRequest request
    ) {
        Tenant updated = tenantUseCase.updateTenant(new TenantUseCase.UpdateTenantCommand(
            id,
            request.fullName(),
            request.identityCardNumber(),
            request.phone(),
            request.email(),
            request.permanentAddress()
        ));
        return ResponseEntity.ok(TenantResponse.from(updated));
    }

    public record UpdateTenantRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        String fullName,

        @NotBlank(message = "Số CCCD không được để trống")
        String identityCardNumber,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0")
        String phone,

        String email,

        String permanentAddress
    ) {}

    public record TenantResponse(
        UUID id,
        UUID userId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress,
        Instant createdAt
    ) {
        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                tenant.id(),
                tenant.userId(),
                tenant.fullName(),
                tenant.identityCardNumber(),
                tenant.phone(),
                tenant.email(),
                tenant.permanentAddress(),
                tenant.createdAt()
            );
        }
    }
}
