package com.phonghub.application.port.in;

import com.phonghub.domain.model.Tenant;
import java.util.Optional;
import java.util.UUID;

public interface TenantUseCase {

    Tenant updateTenant(UpdateTenantCommand command);

    Optional<Tenant> getTenant(UUID tenantId);

    record UpdateTenantCommand(
        UUID tenantId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress
    ) {
        public UpdateTenantCommand {
            if (tenantId == null) {
                throw new IllegalArgumentException("Tenant ID cannot be null");
            }
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Họ và tên không được để trống");
            }
            if (identityCardNumber == null || identityCardNumber.isBlank()) {
                throw new IllegalArgumentException("Số CCCD không được để trống");
            }
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("Số điện thoại không được để trống");
            }
            if (!phone.trim().matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0");
            }
        }
    }
}
