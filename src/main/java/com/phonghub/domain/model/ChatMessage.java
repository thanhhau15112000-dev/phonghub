package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChatMessage(
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

    public ChatMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(chatRoomId, "chatRoomId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(senderName, "senderName must not be null");
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasMedia = mediaUrl != null && !mediaUrl.trim().isEmpty();
        if (!hasContent && !hasMedia) {
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

    /** Chat chỉ còn văn bản (#201); media chỉ còn ở tin cũ gửi trước khi gỡ chức năng. */
    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isBlank();
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
