package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hai loại thông báo (#86):
 * <ul>
 *   <li>Việc của quản trị viên ({@link #isForAdmins()}): yêu cầu cấp lại mật khẩu — {@code targetUserId} là người
 *       yêu cầu — và thông báo không gửi riêng ai ({@code targetUserId == null}, ví dụ tiền về không khớp hóa đơn).
 *       Xử lý xong thì {@code resolved}.</li>
 *   <li>Thông báo cá nhân: {@code targetUserId} là người nhận, hiện trong trung tâm thông báo của người đó và được
 *       đánh dấu đã đọc ({@code readAt}).</li>
 * </ul>
 */
public class Notification {

    private final UUID id;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final UUID targetUserId;
    private final String targetUsername;
    private final String targetFullName;
    private final UserRole targetRole;
    private final Instant createdAt;
    private final String link;
    private boolean resolved;
    private Instant resolvedAt;
    private String resolutionNote;
    private Instant readAt;

    public Notification(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID targetUserId,
        String targetUsername,
        String targetFullName,
        UserRole targetRole,
        Instant createdAt,
        boolean resolved,
        Instant resolvedAt,
        String resolutionNote
    ) {
        this(id, type, title, message, targetUserId, targetUsername, targetFullName, targetRole, createdAt, resolved,
            resolvedAt, resolutionNote, null, null);
    }

    public Notification(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID targetUserId,
        String targetUsername,
        String targetFullName,
        UserRole targetRole,
        Instant createdAt,
        boolean resolved,
        Instant resolvedAt,
        String resolutionNote,
        String link,
        Instant readAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.targetUserId = targetUserId;
        this.targetUsername = targetUsername;
        this.targetFullName = targetFullName;
        this.targetRole = targetRole;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.resolved = resolved;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
        this.link = requireInternalLink(link);
        this.readAt = readAt;
    }

    /** Liên kết chỉ được là đường dẫn trong ứng dụng ("/..."), không phải "//host" hay URL ngoài: chống open redirect. */
    private static String requireInternalLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        if (!link.startsWith("/") || link.startsWith("//") || link.contains("\\")) {
            throw new IllegalArgumentException("Liên kết thông báo phải là đường dẫn nội bộ bắt đầu bằng '/'");
        }
        return link;
    }

    /** Thông báo cá nhân gửi tới {@code recipient}, có thể kèm đường dẫn tới đối tượng liên quan. */
    public static Notification createForUser(String title, String message, User recipient, String link) {
        Objects.requireNonNull(recipient, "recipient must not be null");
        return new Notification(UUID.randomUUID(), NotificationType.GENERAL, title, message, recipient.id(),
            recipient.username(), recipient.fullName(), recipient.role(), Instant.now(), false, null, null, link, null);
    }

    public static Notification createPasswordResetRequest(User user) {
        return new Notification(
            UUID.randomUUID(),
            NotificationType.PASSWORD_RESET_REQUEST,
            "Yêu cầu cấp lại mật khẩu",
            "Tài khoản " + user.username() + " (" + user.fullName() + ") yêu cầu cấp lại mật khẩu khi quên.",
            user.id(),
            user.username(),
            user.fullName(),
            user.role(),
            Instant.now(),
            false,
            null,
            null
        );
    }

    public static Notification createPaymentReceived(
        String title,
        String message,
        UUID targetUserId,
        String targetUsername,
        String targetFullName,
        UserRole targetRole
    ) {
        return new Notification(
            UUID.randomUUID(),
            NotificationType.PAYMENT_RECEIVED,
            title,
            message,
            targetUserId,
            targetUsername,
            targetFullName,
            targetRole,
            Instant.now(),
            false,
            null,
            null
        );
    }

    public static Notification createGeneral(
        String title,
        String message,
        UUID targetUserId,
        String targetUsername,
        String targetFullName,
        UserRole targetRole
    ) {
        return new Notification(UUID.randomUUID(), NotificationType.GENERAL, title, message, targetUserId,
            targetUsername, targetFullName, targetRole, Instant.now(), false, null, null);
    }

    public void markResolved(String resolutionNote) {
        this.resolved = true;
        this.resolvedAt = Instant.now();
        this.resolutionNote = resolutionNote;
    }

    /** Đánh dấu đã đọc; đọc lại không đổi thời điểm đọc đầu tiên. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(at, "at must not be null");
        }
    }

    public boolean isForAdmins() {
        return type == NotificationType.PASSWORD_RESET_REQUEST || targetUserId == null;
    }

    /** Thông báo cá nhân của {@code userId} (không gồm việc của quản trị viên). */
    public boolean isAddressedTo(UUID userId) {
        return !isForAdmins() && targetUserId.equals(userId);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID id() { return id; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String message() { return message; }
    public UUID targetUserId() { return targetUserId; }
    public String targetUsername() { return targetUsername; }
    public String targetFullName() { return targetFullName; }
    public UserRole targetRole() { return targetRole; }
    public Instant createdAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolutionNote() { return resolutionNote; }
    public String link() { return link; }
    public Instant readAt() { return readAt; }
}
