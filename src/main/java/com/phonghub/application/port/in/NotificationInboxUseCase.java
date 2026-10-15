package com.phonghub.application.port.in;

import com.phonghub.domain.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Trung tâm thông báo của người dùng đang đăng nhập, mọi vai trò (#86). */
public interface NotificationInboxUseCase {

    int MAX_PAGE_SIZE = 50;

    /** Thông báo cá nhân mới nhất trước; trang bắt đầu từ 1, kích thước bị giới hạn trong 1..{@link #MAX_PAGE_SIZE}. */
    InboxPage inbox(boolean unreadOnly, int page, int size);

    /** 404 nếu không tồn tại, 403 nếu không phải thông báo của người đang đăng nhập. */
    Notification markRead(UUID notificationId);

    int markAllRead();

    /** Đánh dấu đã đọc và trả về đường dẫn của thông báo (nếu có) để chuyển tới. */
    Optional<String> open(UUID notificationId);

    record InboxPage(List<Notification> items, long total, long unread, int page, int size) {
        public int totalPages() {
            return (int) Math.max(1, (total + size - 1) / size);
        }
    }
}
