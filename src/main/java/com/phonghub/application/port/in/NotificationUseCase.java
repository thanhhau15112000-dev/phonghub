package com.phonghub.application.port.in;

import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.User;
import java.util.List;
import java.util.UUID;

public interface NotificationUseCase {
    Notification requestPasswordReset(User user);
    List<Notification> listAdminNotifications();
    default List<Notification> listRecentAdminNotifications(int limit) {
        return listAdminNotifications().stream()
            .sorted(java.util.Comparator.comparing(Notification::isResolved))
            .limit(Math.max(1, limit))
            .toList();
    }
    long countUnreadAdminNotifications();
    void markAsResolved(UUID notificationId);
    void deleteNotification(UUID notificationId);
    AuthUseCase.PasswordResetResult processPasswordReset(UUID notificationId);
}
