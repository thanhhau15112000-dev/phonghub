package com.phonghub.application.port.out;

import com.phonghub.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {
    Notification save(Notification notification);
    List<Notification> findAll();
    Optional<Notification> findById(UUID id);
    void deleteById(UUID id);

    /** Việc của quản trị viên ({@link Notification#isForAdmins()}), mới nhất trước. */
    List<Notification> findForAdmins();

    long countUnresolvedForAdmins();

    /** Thông báo cá nhân của người nhận ({@link Notification#isAddressedTo(UUID)}), mới nhất trước. */
    List<Notification> findByRecipient(UUID userId, boolean unreadOnly, int offset, int limit);

    long countByRecipient(UUID userId, boolean unreadOnly);

    default InboxCounts countInboxCounts(UUID userId, boolean unreadOnly) {
        long total = countByRecipient(userId, unreadOnly);
        long unread = unreadOnly ? total : countByRecipient(userId, true);
        return new InboxCounts(total, unread);
    }

    default List<Notification> findRecentForAdmins(int limit) {
        return findForAdmins().stream()
            .sorted(java.util.Comparator.comparing(Notification::isResolved))
            .limit(Math.max(1, limit))
            .toList();
    }

    /** Đánh dấu đã đọc mọi thông báo cá nhân chưa đọc của người nhận; trả về số thông báo được cập nhật. */
    int markAllRead(UUID userId, Instant at);

    record InboxCounts(long total, long unread) {}
}
