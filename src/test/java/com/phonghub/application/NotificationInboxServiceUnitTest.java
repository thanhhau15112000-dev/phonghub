package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.application.port.in.NotificationInboxUseCase.InboxPage;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.NotificationInboxService;
import com.phonghub.domain.exception.NotificationNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #86: trung tâm thông báo cá nhân — chỉ người nhận xem/đánh dấu, việc của quản trị viên không lẫn vào. */
class NotificationInboxServiceUnitTest {

    private final User tenant = user("tenant", UserRole.TENANT);
    private final User owner = user("owner", UserRole.OWNER);
    private final AtomicReference<User> caller = new AtomicReference<>(tenant);
    private InMemoryNotificationRepository repository;
    private NotificationInboxService service;

    private static User user(String username, UserRole role) {
        return new User(UUID.randomUUID(), username, username + "@x", username, null, role, User.UserStatus.ACTIVE,
            false, Instant.now());
    }

    private Notification send(User to, String title, String link) {
        return repository.save(Notification.createForUser(title, "Nội dung " + title, to, link));
    }

    @BeforeEach
    void setUp() {
        repository = new InMemoryNotificationRepository();
        service = new NotificationInboxService(repository, () -> {
            User u = caller.get();
            return u == null ? null : new CurrentUser(u.id(), u.email(), u.fullName(), u.role());
        });
    }

    @Test
    @DisplayName("hộp thư chỉ có thông báo cá nhân của người đang đăng nhập; yêu cầu cấp lại mật khẩu không lẫn vào")
    void inboxIsPersonal() {
        send(tenant, "Hóa đơn sắp đến hạn", "/invoices/1/pay");
        send(owner, "Biên bản chờ duyệt", null);
        repository.save(Notification.createPasswordResetRequest(tenant));

        InboxPage page = service.inbox(false, 1, 20);
        assertEquals(1, page.total());
        assertEquals(1, page.unread());
        assertEquals("Hóa đơn sắp đến hạn", page.items().getFirst().title());

        caller.set(owner);
        assertEquals("Biên bản chờ duyệt", service.inbox(false, 1, 20).items().getFirst().title());
    }

    @Test
    @DisplayName("phân trang mới nhất trước, trang/kích thước ngoài khoảng được kẹp lại; lọc chưa đọc")
    void pagingAndUnreadFilter() {
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        for (int i = 1; i <= 3; i++) {
            repository.save(new Notification(UUID.randomUUID(), NotificationType.GENERAL, "TB " + i, "m", tenant.id(),
                tenant.username(), tenant.fullName(), tenant.role(), base.plusSeconds(i), false, null, null, null, null));
        }
        InboxPage first = service.inbox(false, 1, 2);
        assertEquals(2, first.totalPages());
        assertEquals("TB 3", first.items().getFirst().title(), "mới nhất trước");
        assertEquals(2, service.inbox(false, 99, 2).page(), "trang quá lớn về trang cuối");
        assertEquals(1, service.inbox(false, 0, 2).page());
        assertEquals(50, service.inbox(false, 1, 1000).size());

        service.markRead(first.items().getFirst().id());
        InboxPage unread = service.inbox(true, 1, 20);
        assertEquals(2, unread.total());
        assertEquals(2, unread.unread());
        assertTrue(unread.items().stream().noneMatch(Notification::isRead));
    }

    @Test
    @DisplayName("đánh dấu đã đọc: của mình được, của người khác 403, không tồn tại 404, việc của quản trị viên 403")
    void markReadOwnership() {
        Notification mine = send(tenant, "Của tôi", null);
        Notification others = send(owner, "Của chủ trọ", null);
        Notification reset = repository.save(Notification.createPasswordResetRequest(tenant));

        Notification read = service.markRead(mine.id());
        assertTrue(read.isRead());
        Instant firstRead = read.readAt();
        assertEquals(firstRead, service.markRead(mine.id()).readAt(), "đọc lại không đổi thời điểm đọc");

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.markRead(others.id()));
        assertFalse(repository.findById(others.id()).orElseThrow().isRead());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.markRead(reset.id()));
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(UUID.randomUUID()));
    }

    @Test
    @DisplayName("đánh dấu tất cả chỉ đụng thông báo của mình; mở thông báo trả về đường dẫn và đánh dấu đã đọc")
    void markAllAndOpen() {
        send(tenant, "A", null);
        Notification withLink = send(tenant, "B", "/contracts/abc/handover");
        Notification others = send(owner, "C", null);

        assertEquals(Optional.of("/contracts/abc/handover"), service.open(withLink.id()));
        assertTrue(repository.findById(withLink.id()).orElseThrow().isRead());
        assertEquals(1, service.markAllRead());
        assertEquals(0, service.markAllRead());
        assertFalse(repository.findById(others.id()).orElseThrow().isRead());
        assertEquals(0, service.inbox(false, 1, 20).unread());
    }

    @Test
    @DisplayName("chưa đăng nhập bị từ chối; liên kết thông báo chỉ là đường dẫn nội bộ (chống open redirect)")
    void guards() {
        caller.set(null);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.inbox(false, 1, 20));

        for (String bad : new String[]{"//evil.example", "https://evil.example", "javascript:alert(1)", "/\\evil"}) {
            assertThrows(IllegalArgumentException.class, () -> Notification.createForUser("t", "m", tenant, bad), bad);
        }
        assertNull(Notification.createForUser("t", "m", tenant, " ").link());
        assertNotNull(Notification.createForUser("t", "m", tenant, "/notifications").link());
    }
}
