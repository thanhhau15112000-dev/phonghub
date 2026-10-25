package com.phonghub.application.service;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gắn hồ sơ người thuê với tài khoản TENANT theo email: dùng tài khoản có sẵn (chưa gắn hồ sơ khác) hoặc tạo tài khoản
 * mới với mật khẩu tạm trả về một lần. Dùng chung cho thêm người ở hợp đồng và trang hồ sơ người thuê để hai nơi theo
 * cùng một quy tắc.
 */
class TenantAccountLinker {

    record Result(Tenant tenant, String username, String temporaryPassword, UUID createdIdentityUserId) {}

    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final IdentityProviderPort identityProviderPort;
    private final AuditPort auditPort;

    TenantAccountLinker(
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        AuditPort auditPort
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.identityProviderPort = identityProviderPort;
        this.auditPort = auditPort;
    }

    Result linkOrCreate(CurrentUser actor, Tenant tenant, String email, String auditAction) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cần cung cấp email để tạo tài khoản người dùng");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Định dạng email không hợp lệ: " + email);
        }
        if (tenant.userId() != null) {
            // Hồ sơ đã có tài khoản: không tạo thêm
            User linkedUser = userRepository == null ? null : userRepository.findById(tenant.userId()).orElse(null);
            if (linkedUser != null && !linkedUser.email().equalsIgnoreCase(normalizedEmail)) {
                throw new DomainException("Email nhập vào không khớp với tài khoản đã liên kết của hồ sơ này.");
            }
            String username = linkedUser == null ? null : linkedUser.username();
            return new Result(tenant, username, null, null);
        }
        if (userRepository == null) {
            throw new DomainException("Chưa cấu hình truy cập tài khoản người thuê.");
        }

        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.role() != UserRole.TENANT || user.status() != User.UserStatus.ACTIVE) {
                throw new ConflictException(
                    "Email này không thuộc tài khoản TENANT đang hoạt động; hãy dùng email khác.");
            }
            return new Result(linkUser(tenant, user), user.username(), null, null);
        }
        if (identityProviderPort == null) {
            throw new DomainException("Chưa cấu hình nhà cung cấp xác thực để tạo tài khoản người thuê.");
        }
        if (tenant.phone() != null && userRepository.findByPhone(tenant.phone()).isPresent()) {
            throw new ConflictException("Số điện thoại đã thuộc một tài khoản khác.");
        }

        String baseUsername = normalizedEmail.split("@")[0].toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (baseUsername.length() < 3) {
            baseUsername = "tenant_" + baseUsername;
        }
        String username = baseUsername;
        int counter = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + counter++;
        }

        String temporaryPassword = AuthService.generateSecureTemporaryPassword(16);
        UUID authUserId = identityProviderPort.adminCreateUser(normalizedEmail, temporaryPassword);
        User savedUser;
        try {
            savedUser = userRepository.save(new User(authUserId, username, normalizedEmail, tenant.fullName(),
                tenant.phone(), UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()));
        } catch (RuntimeException ex) {
            // Không để lại tài khoản đăng nhập mồ côi khi ghi DB thất bại
            try {
                identityProviderPort.adminDeleteUser(authUserId);
            } catch (RuntimeException ignored) {
                // đã báo lỗi gốc
            }
            throw ex;
        }
        if (auditPort != null) {
            auditPort.recordEvent(AuditPort.AuditEvent.of(auditAction, actor == null ? null : actor.id(),
                "USER", savedUser.id().toString(), Map.of("userId", savedUser.id().toString(), "role", "TENANT",
                    "tenantId", tenant.id().toString())));
        }
        return new Result(saveLink(tenant, savedUser.id(), normalizedEmail), savedUser.username(),
            temporaryPassword, authUserId);
    }

    /** Chỉ kiểm tra, không ghi: hồ sơ và tài khoản này gắn được với nhau không. */
    void assertCanLink(Tenant tenant, User user) {
        if (tenant.userId() != null && !tenant.userId().equals(user.id())) {
            throw new ConflictException("Hồ sơ người thuê đã liên kết với một tài khoản khác.");
        }
        Optional<Tenant> linked = tenantRepository.findByUserId(user.id());
        if (linked.isPresent() && !linked.get().id().equals(tenant.id())) {
            throw new ConflictException("Tài khoản này đã được liên kết với hồ sơ người thuê khác.");
        }
    }

    Tenant linkUser(Tenant tenant, User user) {
        assertCanLink(tenant, user);
        if (user.id().equals(tenant.userId()) && user.email().equalsIgnoreCase(tenant.email())) {
            return tenant;
        }
        return saveLink(tenant, user.id(), user.email());
    }

    /** Bước sau thất bại thì xóa tài khoản đăng nhập vừa tạo ở nhà cung cấp xác thực, vì giao dịch DB không hoàn tác được nó. */
    void deleteCreatedIdentityUser(Result result, RuntimeException originalError) {
        if (result == null || result.createdIdentityUserId() == null || identityProviderPort == null) return;
        try {
            identityProviderPort.adminDeleteUser(result.createdIdentityUserId());
        } catch (RuntimeException ignored) {
            originalError.addSuppressed(ignored);
        }
    }

    private Tenant saveLink(Tenant tenant, UUID userId, String email) {
        return tenantRepository.save(new Tenant(tenant.id(), userId, tenant.fullName(), tenant.identityCardNumber(),
            tenant.phone(), email, tenant.permanentAddress(), tenant.createdAt()));
    }
}
