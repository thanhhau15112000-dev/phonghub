package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
    private boolean resolved;
    private Instant resolvedAt;
    private String resolutionNote;

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

    public void markResolved(String resolutionNote) {
        this.resolved = true;
        this.resolvedAt = Instant.now();
        this.resolutionNote = resolutionNote;
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
}
