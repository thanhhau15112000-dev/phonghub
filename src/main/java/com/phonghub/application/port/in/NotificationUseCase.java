package com.phonghub.application.port.in;

import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.UUID;

public interface NotificationUseCase {
    Notification requestPasswordReset(User user);
    List<Notification> listAdminNotifications();
    long countUnreadAdminNotifications();
    void markAsResolved(UUID notificationId);
    void deleteNotification(UUID notificationId);
    AuthUseCase.PasswordResetResult processPasswordReset(UUID notificationId);
}
