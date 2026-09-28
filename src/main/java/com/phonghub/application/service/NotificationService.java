package com.phonghub.application.service;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.User;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NotificationService implements NotificationUseCase {

    private final NotificationRepositoryPort notificationRepository;
    private final AuthUseCase authUseCase;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public NotificationService(
        NotificationRepositoryPort notificationRepository,
        AuthUseCase authUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
        this.authUseCase = Objects.requireNonNull(authUseCase, "authUseCase must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public Notification requestPasswordReset(User user) {
        Notification notification = Notification.createPasswordResetRequest(user);
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> listAdminNotifications() {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        return notificationRepository.findAll().stream()
            .sorted(Comparator.comparing(Notification::createdAt).reversed())
            .toList();
    }

    @Override
    public long countUnreadAdminNotifications() {
        return notificationRepository.countUnresolved();
    }

    @Override
    public void markAsResolved(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new DomainException("Thông báo không tồn tại"));
        notification.markResolved("Đã xử lý bởi quản trị viên");
        notificationRepository.save(notification);
    }

    @Override
    public void deleteNotification(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        notificationRepository.deleteById(notificationId);
    }

    @Override
    public AuthUseCase.PasswordResetResult processPasswordReset(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);

        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new DomainException("Thông báo không tồn tại"));

        if (notification.targetUserId() == null) {
            throw new DomainException("Thông báo không liên kết với người dùng nào");
        }

        AuthUseCase.PasswordResetResult result = authUseCase.adminResetPassword(notification.targetUserId());
        notification.markResolved("Đã cấp lại mật khẩu tạm: " + result.temporaryPassword());
        notificationRepository.save(notification);
        return result;
    }
}
