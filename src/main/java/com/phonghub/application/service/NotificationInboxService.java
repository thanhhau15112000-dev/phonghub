package com.phonghub.application.service;

import com.phonghub.application.port.in.NotificationInboxUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.domain.exception.NotificationNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class NotificationInboxService implements NotificationInboxUseCase {

    private final NotificationRepositoryPort notificationRepository;
    private final CurrentUserPort currentUserPort;

    public NotificationInboxService(NotificationRepositoryPort notificationRepository, CurrentUserPort currentUserPort) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort");
    }

    @Override
    public InboxPage inbox(boolean unreadOnly, int page, int size) {
        CurrentUser user = requireUser();
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        long total = notificationRepository.countByRecipient(user.id(), unreadOnly);
        long unread = unreadOnly ? total : notificationRepository.countByRecipient(user.id(), true);
        int lastPage = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        int currentPage = Math.clamp(page, 1, lastPage);
        List<Notification> items = notificationRepository.findByRecipient(user.id(), unreadOnly,
            (currentPage - 1) * pageSize, pageSize);
        return new InboxPage(items, total, unread, currentPage, pageSize);
    }

    @Override
    public Notification markRead(UUID notificationId) {
        Notification notification = findOwn(notificationId);
        if (!notification.isRead()) {
            notification.markRead(Instant.now());
            notificationRepository.save(notification);
        }
        return notification;
    }

    @Override
    public int markAllRead() {
        return notificationRepository.markAllRead(requireUser().id(), Instant.now());
    }

    @Override
    public Optional<String> open(UUID notificationId) {
        return Optional.ofNullable(markRead(notificationId).link());
    }

    /** Chỉ người nhận được xem/đánh dấu; việc của quản trị viên không thuộc trung tâm thông báo cá nhân. */
    private Notification findOwn(UUID notificationId) {
        CurrentUser user = requireUser();
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException("Không tìm thấy thông báo " + notificationId));
        if (!notification.isAddressedTo(user.id())) {
            throw new UnauthorizedPropertyAccessException("Chỉ xem được thông báo gửi cho chính bạn");
        }
        return notification;
    }

    private CurrentUser requireUser() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Cần đăng nhập để xem thông báo");
        }
        return user;
    }
}
