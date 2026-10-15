package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.NotificationInboxUseCase;
import com.phonghub.application.port.in.NotificationInboxUseCase.InboxPage;
import com.phonghub.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trung tâm thông báo của người dùng đang đăng nhập (#86). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationInboxUseCase inbox;

    public NotificationApiController(NotificationInboxUseCase inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public InboxResponse list(
        @RequestParam(defaultValue = "false") boolean unread,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return InboxResponse.from(inbox.inbox(unread, page, size));
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId) {
        return NotificationResponse.from(inbox.markRead(notificationId));
    }

    @PostMapping("/read-all")
    public ReadAllResponse markAllRead() {
        return new ReadAllResponse(inbox.markAllRead());
    }

    public record NotificationResponse(UUID id, String title, String message, String link, Instant createdAt,
                                       boolean read, Instant readAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id(), n.title(), n.message(), n.link(), n.createdAt(), n.isRead(),
                n.readAt());
        }
    }

    public record InboxResponse(List<NotificationResponse> items, long total, long unread, int page, int size,
                                int totalPages) {
        static InboxResponse from(InboxPage page) {
            return new InboxResponse(page.items().stream().map(NotificationResponse::from).toList(), page.total(),
                page.unread(), page.page(), page.size(), page.totalPages());
        }
    }

    public record ReadAllResponse(int updated) {}
}
