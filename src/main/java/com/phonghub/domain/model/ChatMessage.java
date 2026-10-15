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
            throw new IllegalArgumentException("Message must have either text content or media");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static ChatMessage create(UUID chatRoomId, UUID senderId, String senderName, String content) {
        return new ChatMessage(
            UUID.randomUUID(),
            chatRoomId,
            senderId,
            senderName,
            content.trim(),
            null,
            null,
            Instant.now(),
            null
        );
    }

    public static ChatMessage createMedia(UUID chatRoomId, UUID senderId, String senderName, String caption, String mediaUrl, String mediaType) {
        return new ChatMessage(
            UUID.randomUUID(),
            chatRoomId,
            senderId,
            senderName,
            caption != null ? caption.trim() : "",
            mediaUrl,
            mediaType,
            Instant.now(),
            null
        );
    }

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
