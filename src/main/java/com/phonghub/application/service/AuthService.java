package com.phonghub.application.service;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuthService implements AuthUseCase {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepository;
    private final IdentityProviderPort identityProviderPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;

    public AuthService(
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this.userRepository = userRepository;
        this.identityProviderPort = identityProviderPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
    }

    public AuthService(
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(userRepository, identityProviderPort, currentUserPort, authorizationService, event -> {});
    }

    @Override
    public AuthTokenResponse login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String normalizedUsername = username.trim().toLowerCase();
        User user = userRepository.findByUsername(normalizedUsername)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (user.status() != User.UserStatus.ACTIVE) {
            throw new AccountDisabledException("User account is " + user.status() + " (not ACTIVE)");
        }

        IdentityProviderPort.RawTokenResponse rawToken = identityProviderPort.login(user.email(), password);

        return new AuthTokenResponse(
            rawToken.accessToken(),
            rawToken.refreshToken(),
            rawToken.tokenType(),
            rawToken.expiresIn(),
            new AuthTokenResponse.UserInfo(
                user.id(),
                user.username(),
                user.fullName(),
                user.role(),
                user.mustChangePassword()
            )
        );
    }

    @Override
    public AuthTokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token cannot be blank");
        }

        IdentityProviderPort.RawTokenResponse rawToken = identityProviderPort.refreshToken(refreshToken);

        return new AuthTokenResponse(
            rawToken.accessToken(),
            rawToken.refreshToken(),
            rawToken.tokenType(),
            rawToken.expiresIn(),
            null
        );
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            identityProviderPort.logout(accessToken);
        }
    }

    @Override
    public void changePassword(String newPassword) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser == null) {
            throw new InvalidCredentialsException("Authentication required to change password");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        identityProviderPort.changePassword(currentUser.id(), newPassword);

        User user = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new UserNotFoundException("User profile not found"));

        User updated = new User(
            user.id(),
            user.username(),
            user.email(),
            user.fullName(),
            user.phone(),
            user.role(),
            user.status(),
            false,
            user.createdAt()
        );
        userRepository.save(updated);

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "USER_CHANGE_PASSWORD",
            currentUser.id(),
            "USER",
            currentUser.id().toString(),
            Map.of("username", user.username())
        ));
    }

    @Override
    public PasswordResetResult adminResetPassword(UUID targetUserId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);

        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("Target user not found: " + targetUserId));

        if (targetUser.role() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Manual password reset is prohibited for ADMIN accounts");
        }

        String temporaryPassword = generateSecureTemporaryPassword(16);
        identityProviderPort.adminSetPassword(targetUserId, temporaryPassword);

        User updated = new User(
            targetUser.id(),
            targetUser.username(),
            targetUser.email(),
            targetUser.fullName(),
            targetUser.phone(),
            targetUser.role(),
            targetUser.status(),
            true,
            targetUser.createdAt()
        );
        userRepository.save(updated);

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "ADMIN_RESET_PASSWORD",
            caller.id(),
            "USER",
            targetUserId.toString(),
            Map.of("targetUsername", targetUser.username(), "targetRole", targetUser.role().name())
        ));

        return new PasswordResetResult(
            targetUser.id(),
            targetUser.username(),
            temporaryPassword,
            true,
            "Temporary password generated. It will not be displayed again."
        );
    }

    @Override
    public AdminCreateUserResult adminCreateUser(CreateUserCommand command) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);

        if (command.username() == null || command.username().isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        String normalizedUsername = command.username().trim().toLowerCase();
        String normalizedEmail = command.email().trim().toLowerCase();

        if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Định dạng email không hợp lệ: " + command.email());
        }

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException("Email đã được sử dụng bởi một tài khoản khác: " + normalizedEmail);
        }

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new DuplicateUsernameException("Tên đăng nhập đã tồn tại trong hệ thống: " + normalizedUsername);
        }

        String cleanPhone = null;
        if (command.phone() != null && !command.phone().isBlank()) {
            cleanPhone = command.phone().trim();
            if (!cleanPhone.matches("^0\\d{9}$")) {
                throw new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)");
            }
            final String phoneToCheck = cleanPhone;
            if (userRepository.findAll().stream().anyMatch(u -> phoneToCheck.equals(u.phone()))) {
                throw new DomainException("Số điện thoại đã được sử dụng bởi một tài khoản khác: " + cleanPhone);
            }
        }

        String temporaryPassword = generateSecureTemporaryPassword(16);
        UUID authUserId = identityProviderPort.adminCreateUser(normalizedEmail, temporaryPassword);

        User newUser = new User(
            authUserId,
            normalizedUsername,
            normalizedEmail,
            command.fullName().trim(),
            cleanPhone,
            command.role(),
            User.UserStatus.ACTIVE,
            true,
            Instant.now()
        );

        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (Exception ex) {
            // Compensation: Delete newly provisioned Supabase Auth identity if PostgreSQL write fails
            try {
                identityProviderPort.adminDeleteUser(authUserId);
            } catch (Exception ignored) {}
            if (ex instanceof DomainException de) {
                throw de;
            }
            throw new DomainException("Không thể lưu tài khoản vào cơ sở dữ liệu: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi dữ liệu"), ex);
        }

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "ADMIN_CREATE_USER",
            caller.id(),
            "USER",
            authUserId.toString(),
            Map.of("username", savedUser.username(), "role", savedUser.role().name())
        ));

        return new AdminCreateUserResult(
            savedUser.id(),
            savedUser.username(),
            savedUser.email(),
            savedUser.fullName(),
            savedUser.phone(),
            savedUser.role(),
            savedUser.status(),
            savedUser.mustChangePassword(),
            temporaryPassword,
            savedUser.createdAt()
        );
    }

    @Override
    public void adminDeleteUser(UUID targetUserId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);

        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID cannot be null");
        }

        if (caller != null && caller.id().equals(targetUserId)) {
            throw new IllegalArgumentException("Quản trị viên không thể tự xoá tài khoản của chính mình");
        }

        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("Target user not found: " + targetUserId));

        if (targetUser.role() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Deleting another ADMIN account is prohibited");
        }

        // Bước 1: Xoá user trong Supabase Auth. Nếu bước này lỗi thì dừng, báo lỗi và không đụng vào DB.
        identityProviderPort.adminDeleteUser(targetUserId);

        // Bước 2: Xoá dòng trong public.users
        userRepository.deleteById(targetUserId);

        // Bước 3: Ghi audit ADMIN_DELETE_USER
        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "ADMIN_DELETE_USER",
            caller != null ? caller.id() : null,
            "USER",
            targetUserId.toString(),
            Map.of(
                "username", targetUser.username() != null ? targetUser.username() : "",
                "role", targetUser.role().name()
            )
        ));
    }

    private String generateSecureTemporaryPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
