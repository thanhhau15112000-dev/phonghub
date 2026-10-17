package com.phonghub.application.service;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.User;
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
        // Chỉ việc của quản trị viên; thông báo cá nhân của chủ trọ/người thuê nằm ở trung tâm thông báo của họ (#86).
        return notificationRepository.findForAdmins();
    }

    @Override
    public long countUnreadAdminNotifications() {
        return notificationRepository.countUnresolvedForAdmins();
    }

    @Override
    public void markAsResolved(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        Notification notification = findAdminNotification(notificationId);
        notification.markResolved("Đã xử lý bởi quản trị viên");
        notificationRepository.save(notification);
    }

    @Override
    public void deleteNotification(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);
        notificationRepository.deleteById(findAdminNotification(notificationId).id());
    }

    @Override
    public AuthUseCase.PasswordResetResult processPasswordReset(UUID notificationId) {
        CurrentUser caller = currentUserPort.getCurrentUser();
        authorizationService.assertAdmin(caller);

        Notification notification = findAdminNotification(notificationId);
        if (notification.type() != NotificationType.PASSWORD_RESET_REQUEST || notification.targetUserId() == null) {
            throw new DomainException("Thông báo không phải yêu cầu cấp lại mật khẩu");
        }

        AuthUseCase.PasswordResetResult result = authUseCase.adminResetPassword(notification.targetUserId());
        // Mật khẩu tạm chỉ hiện một lần cho quản trị viên, không lưu vào ghi chú (ghi chú được lưu và hiện lại về sau).
        notification.markResolved("Đã cấp lại mật khẩu tạm cho tài khoản " + result.username());
        notificationRepository.save(notification);
        return result;
    }

    private Notification findAdminNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId)
            .filter(Notification::isForAdmins)
            .orElseThrow(() -> new DomainException("Thông báo không tồn tại"));
    }
}
