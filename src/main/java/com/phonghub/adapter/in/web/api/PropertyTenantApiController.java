package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.in.TenantUseCase.PropertyTenantView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý người thuê theo nhà trọ. Danh sách chỉ trả SĐT và CCCD đã che; số CCCD đầy đủ chỉ xem qua
 * luồng giấy tờ (cần nhập lại mật khẩu, có nhật ký).
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/tenants")
public class PropertyTenantApiController {

    private final TenantUseCase tenantUseCase;

    public PropertyTenantApiController(TenantUseCase tenantUseCase) {
        this.tenantUseCase = tenantUseCase;
    }

    @GetMapping
    public TenantPageResponse list(
        @PathVariable UUID propertyId,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "false") boolean includeHidden,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        TenantUseCase.TenantPage result = tenantUseCase.listTenants(propertyId, q, includeHidden, page, size);
        return new TenantPageResponse(result.items().stream().map(TenantItem::from).toList(),
            result.page(), result.size(), result.total(), result.totalPages());
    }

    @GetMapping("/{tenantId}")
    public TenantDetail get(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return TenantDetail.from(tenantUseCase.getPropertyTenant(propertyId, tenantId));
    }

    @PostMapping
    public ResponseEntity<TenantDetail> add(
        @PathVariable UUID propertyId,
        @Valid @RequestBody AddTenantRequest request
    ) {
        PropertyTenantView created = tenantUseCase.addTenant(new TenantUseCase.AddTenantCommand(propertyId,
            request.fullName(), request.identityCardNumber(), request.phone(), request.email(),
            request.permanentAddress()));
        return ResponseEntity.created(URI.create("/api/properties/" + propertyId + "/tenants/" + created.tenant().id()))
            .body(TenantDetail.from(created));
    }

    @PostMapping("/{tenantId}/hide")
    public TenantDetail hide(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return TenantDetail.from(tenantUseCase.hideTenant(propertyId, tenantId));
    }

    @PostMapping("/{tenantId}/restore")
    public TenantDetail restore(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return TenantDetail.from(tenantUseCase.restoreTenant(propertyId, tenantId));
    }

    public record AddTenantRequest(
        @NotBlank(message = "Họ và tên không được để trống") String fullName,
        @NotBlank(message = "Số CCCD không được để trống") String identityCardNumber,
        @NotBlank(message = "Số điện thoại không được để trống") String phone,
        String email,
        String permanentAddress
    ) {}

    public record TenantItem(UUID id, String fullName, String phone, String maskedIdentityCardNumber,
                             boolean hidden, boolean hasActiveContract, Instant createdAt) {
        static TenantItem from(PropertyTenantView view) {
            return new TenantItem(view.tenant().id(), view.tenant().fullName(), view.tenant().phone(),
                view.tenant().maskedIdentityCardNumber(), view.hidden(), view.hasActiveContract(),
                view.tenant().createdAt());
        }
    }

    public record TenantDetail(UUID id, UUID userId, String fullName, String maskedIdentityCardNumber, String phone,
                               String email, String permanentAddress, boolean hidden, boolean hasActiveContract,
                               Instant createdAt) {
        static TenantDetail from(PropertyTenantView view) {
            return new TenantDetail(view.tenant().id(), view.tenant().userId(), view.tenant().fullName(),
                view.tenant().maskedIdentityCardNumber(), view.tenant().phone(), view.tenant().email(),
                view.tenant().permanentAddress(), view.hidden(), view.hasActiveContract(), view.tenant().createdAt());
        }
    }

    public record TenantPageResponse(List<TenantItem> items, int page, int size, long total, int totalPages) {}
}
