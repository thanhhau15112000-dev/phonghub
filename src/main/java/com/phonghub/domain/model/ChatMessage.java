package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tin nhắn chat. {@code mediaUrl}/{@code mediaType} chỉ còn ở tin cũ gửi trước #201 (bucket công khai, không mở lại);
 * ảnh và video gửi từ #251 nằm trong {@code attachments}, lưu ở kho riêng tư.
 */
public record ChatMessage(
    UUID id,
    UUID chatRoomId,
    UUID senderId,
    String senderName,
    String content,
    String mediaUrl,
    String mediaType,
    Instant createdAt,
    Instant deletedAt,
    List<ChatAttachment> attachments
) {
    public ChatMessage(
        UUID id,
        UUID chatRoomId,
        UUID senderId,
        String senderName,
        String content,
        Instant createdAt,
        Instant deletedAt
    ) {
        this(id, chatRoomId, senderId, senderName, content, null, null, createdAt, deletedAt);
    }

    public ChatMessage(
        UUID id,
        UUID chatRoomId,
        UUID senderId,
        String senderName,
        String content,
        String mediaUrl,
        String mediaType,
        Instant createdAt,
        Instant deletedAt
    ) {
        this(id, chatRoomId, senderId, senderName, content, mediaUrl, mediaType, createdAt, deletedAt, List.of());
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(chatRoomId, "chatRoomId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(senderName, "senderName must not be null");
        attachments = attachments == null ? List.of() : attachments.stream()
            .sorted(Comparator.comparingInt(ChatAttachment::position)).toList();
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasMedia = mediaUrl != null && !mediaUrl.trim().isEmpty();
        if (!hasContent && !hasMedia && attachments.isEmpty()) {
            throw new IllegalArgumentException("Tin nhắn phải có nội dung hoặc tệp đính kèm.");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static ChatMessage create(UUID chatRoomId, UUID senderId, String senderName, String content, Instant createdAt) {
        return new ChatMessage(
            UUID.randomUUID(),
            chatRoomId,
            senderId,
            senderName,
            content.trim(),
            null,
            null,
            createdAt,
            null
        );
    }

    /** Tin có ảnh/video gửi kèm (#251), lưu ở kho riêng tư. */
    public static ChatMessage createWithAttachments(UUID chatRoomId, UUID senderId, String senderName, String caption,
                                                    List<ChatAttachment> attachments, Instant createdAt) {
        return new ChatMessage(UUID.randomUUID(), chatRoomId, senderId, senderName,
            caption == null ? "" : caption.trim(), null, null, createdAt, null, attachments);
    }

    /** Tin cũ gửi trước #201 có tệp ở bucket công khai; tệp đó không mở lại nữa, chỉ hiện ghi chú. */
    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isBlank();
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public boolean isImage() {
        return "IMAGE".equalsIgnoreCase(mediaType);
    }

    public boolean isVideo() {
        return "VIDEO".equalsIgnoreCase(mediaType);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
