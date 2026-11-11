package com.phonghub.application.service;

import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.in.PasswordRecoveryUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.PasswordRecoveryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvalidPasswordRecoveryLinkException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quên mật khẩu tự phục vụ (#330): gửi liên kết qua email của Supabase, người dùng đặt mật khẩu mới bằng token trong
 * liên kết. Không có email hoặc không gửi được email thì giữ cách cũ là báo Quản trị viên cấp lại mật khẩu tạm.
 */
public class PasswordRecoveryService implements PasswordRecoveryUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryService.class);
    static final int MIN_PASSWORD_LENGTH = 8;
    static final String RESET_PATH = "/reset-password";

    private final UserRepositoryPort userRepository;
    private final IdentityProviderPort identityProvider;
    private final PasswordRecoveryPort recoveryPort;
    private final NotificationUseCase notificationUseCase;
    private final AuditPort auditPort;
    private final String publicBaseUrl;
    private final Clock clock;
    /** Phiên khôi phục đã dùng để đặt mật khẩu: mỗi liên kết chỉ dùng một lần. Giữ tới khi token hết hạn. */
    private final Map<String, Instant> usedSessions = new ConcurrentHashMap<>();

    public PasswordRecoveryService(UserRepositoryPort userRepository, IdentityProviderPort identityProvider,
                                   PasswordRecoveryPort recoveryPort, NotificationUseCase notificationUseCase,
                                   AuditPort auditPort, String publicBaseUrl, Clock clock) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
        this.recoveryPort = recoveryPort;
        this.notificationUseCase = notificationUseCase;
        this.auditPort = auditPort;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean isEmailRecoveryAvailable() {
        return recoveryPort != null;
    }

    @Override
    public void requestRecovery(String identifier) {
        String clean = identifier == null ? "" : identifier.trim().toLowerCase();
        if (clean.isEmpty()) {
            return;
        }
        Optional<User> found = clean.contains("@") ? userRepository.findByEmail(clean) : userRepository.findByUsername(clean);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (user.status() != User.UserStatus.ACTIVE) {
            // Tài khoản bị khóa không tự mở lại được bằng email; Quản trị viên quyết định
            notifyAdmin(user);
            return;
        }
        if (recoveryPort != null && user.email() != null && !user.email().isBlank()) {
            try {
                recoveryPort.sendRecoveryEmail(user.email(), redirectUrl());
                return;
            } catch (RuntimeException ex) {
                // Không ghi email hay thông báo lỗi của nhà cung cấp; chuyển sang báo Quản trị viên
                LOGGER.warn("Không gửi được email đặt lại mật khẩu: userId={}, error={}", user.id(),
                    ex.getClass().getSimpleName());
            }
        }
        notifyAdmin(user);
    }

    @Override
    public void resetPassword(String recoveryToken, String newPassword, String confirmPassword) {
        if (recoveryPort == null) {
            throw new DomainException("Hệ thống chưa bật đặt lại mật khẩu qua email. Hãy liên hệ Quản trị viên.");
        }
        if (recoveryToken == null || recoveryToken.isBlank()) {
            throw new InvalidPasswordRecoveryLinkException();
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Hai lần nhập mật khẩu mới không khớp.");
        }
        PasswordRecoveryPort.RecoveryToken token = recoveryPort.verifyRecoveryToken(recoveryToken.trim());
        Instant now = clock.instant();
        usedSessions.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        String sessionKey = token.sessionId() != null ? token.sessionId() : token.userId() + "@" + token.expiresAt();
        Instant keepUntil = token.expiresAt() != null ? token.expiresAt() : now.plusSeconds(3600);
        if (usedSessions.putIfAbsent(sessionKey, keepUntil) != null) {
            throw new InvalidPasswordRecoveryLinkException();
        }
        try {
            User user = userRepository.findById(token.userId())
                .filter(u -> u.status() == User.UserStatus.ACTIVE)
                .orElseThrow(InvalidPasswordRecoveryLinkException::new);
            identityProvider.adminSetPassword(user.id(), newPassword);
            userRepository.save(new User(user.id(), user.username(), user.email(), user.fullName(), user.phone(),
                user.role(), user.status(), false, user.createdAt()));
            auditPort.recordEvent(AuditPort.AuditEvent.of("USER_PASSWORD_RECOVERED", user.id(), "USER",
                user.id().toString(), Map.of("userId", user.id().toString(), "channel", "EMAIL")));
        } catch (RuntimeException ex) {
            // Chưa đặt được mật khẩu (mật khẩu yếu, nhà cung cấp lỗi): cho phép dùng lại liên kết để thử lần nữa
            usedSessions.remove(sessionKey);
            throw ex;
        }
        try {
            // Thu hồi phiên khôi phục để token không dùng tiếp được ở nơi khác
            identityProvider.logout(recoveryToken.trim());
        } catch (RuntimeException ex) {
            LOGGER.warn("Không thu hồi được phiên khôi phục sau khi đặt lại mật khẩu: userId={}, error={}",
                token.userId(), ex.getClass().getSimpleName());
        }
    }

    private String redirectUrl() {
        return publicBaseUrl.isEmpty() ? null : publicBaseUrl + RESET_PATH;
    }

    private void notifyAdmin(User user) {
        // Quản trị viên tự xử lý mật khẩu của mình qua Supabase Dashboard: không tạo thông báo cho chính họ
        if (notificationUseCase != null && user.role() != UserRole.ADMIN) {
            notificationUseCase.requestPasswordReset(user);
        }
    }
}
