package com.phonghub.application.port.in;

import com.phonghub.domain.model.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantUseCase {

    Tenant updateTenant(UpdateTenantCommand command);

    Optional<Tenant> getTenant(UUID tenantId);

    /** Danh sách tenant của một nhà trọ (owner/staff phụ trách/admin), tìm theo tên, SĐT hoặc CCCD, có phân trang. */
    TenantPage listTenants(UUID propertyId, String query, boolean includeHidden, int page, int size);

    PropertyTenantView getPropertyTenant(UUID propertyId, UUID tenantId);

    /** Thêm tenant (chưa cần hợp đồng) vào nhà trọ. */
    PropertyTenantView addTenant(AddTenantCommand command);

    /** Tạo hồ sơ và tài khoản đăng nhập người thuê trong một thao tác. */
    TenantAccountResult createTenantAccount(CreateTenantAccountCommand command);

    /** Cấp tài khoản cho hồ sơ cũ chưa có tài khoản (tạo mới hoặc gắn tài khoản TENANT có sẵn cùng email). */
    TenantAccountResult issueTenantAccount(IssueTenantAccountCommand command);

    /** Gắn hồ sơ người thuê với tài khoản TENANT đã tồn tại theo email, không tạo tài khoản mới. */
    Tenant linkAccountByEmail(UUID tenantId, String email);

    /** Ẩn tenant khỏi danh sách của nhà trọ; bị chặn khi còn hợp đồng ACTIVE tại nhà trọ đó. */
    PropertyTenantView hideTenant(UUID propertyId, UUID tenantId);

    PropertyTenantView restoreTenant(UUID propertyId, UUID tenantId);

    record PropertyTenantView(Tenant tenant, boolean hidden, boolean hasActiveContract) implements java.io.Serializable {}

    record TenantAccountResult(PropertyTenantView tenant, String username, String temporaryPassword,
                               boolean accountCreated) implements java.io.Serializable {}

    record TenantPage(List<PropertyTenantView> items, int page, int size, long total) {
        public int totalPages() {
            return size == 0 ? 0 : (int) ((total + size - 1) / size);
        }
    }

    record AddTenantCommand(
        UUID propertyId,
        UUID tenantProfileId,
        UUID tenantUserId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress
    ) {
        public AddTenantCommand(UUID propertyId, String fullName, String identityCardNumber, String phone,
                                String email, String permanentAddress) {
            this(propertyId, null, null, fullName, identityCardNumber, phone, email, permanentAddress);
        }

        public AddTenantCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property ID cannot be null");
            }
            if (tenantProfileId == null) {
                if (fullName == null || fullName.isBlank()) {
                    throw new IllegalArgumentException("Họ và tên không được để trống");
                }
                if (identityCardNumber == null || !identityCardNumber.trim().matches("^(\\d{9}|\\d{12})$")) {
                    throw new IllegalArgumentException("Số CCCD phải gồm 9 hoặc 12 chữ số");
                }
                if (phone == null || !phone.trim().matches("^0\\d{9}$")) {
                    throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0");
                }
            }
            if (email != null && !email.isBlank()
                && !email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException("Định dạng email không hợp lệ");
            }
        }
    }

    record CreateTenantAccountCommand(
        UUID propertyId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress
    ) {
        public CreateTenantAccountCommand {
            if (propertyId == null) throw new IllegalArgumentException("Property ID cannot be null");
            if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Họ và tên không được để trống");
            if (identityCardNumber == null || !identityCardNumber.trim().matches("^(\\d{9}|\\d{12})$")) {
                throw new IllegalArgumentException("Số CCCD phải gồm 9 hoặc 12 chữ số");
            }
            if (phone == null || !phone.trim().matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0");
            }
            if (email == null || !email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException("Email hợp lệ là bắt buộc để cấp tài khoản người thuê");
            }
        }
    }

    record IssueTenantAccountCommand(UUID propertyId, UUID tenantId, String email) {
        public IssueTenantAccountCommand {
            if (propertyId == null || tenantId == null) throw new IllegalArgumentException("Property and tenant IDs are required");
            if (email == null || !email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException("Email hợp lệ là bắt buộc để cấp tài khoản người thuê");
            }
        }
    }

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
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("Số điện thoại không được để trống");
            }
            if (!phone.trim().matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0");
            }
        }
    }
}
