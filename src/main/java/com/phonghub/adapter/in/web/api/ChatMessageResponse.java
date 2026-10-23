package com.phonghub.adapter.in.web.api;

import com.phonghub.domain.model.ChatMessage;
import java.time.Instant;
import java.util.UUID;

/** Không trả khóa tệp trong kho; media tải qua endpoint của phòng chat, endpoint này chuyển hướng sang URL có chữ ký. */
public record ChatMessageResponse(
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
    public static ChatMessageResponse from(ChatMessage message) {
        String mediaUrl = message.hasMedia()
            ? "/api/chat/rooms/" + message.chatRoomId() + "/messages/" + message.id() + "/media"
            : null;
        return new ChatMessageResponse(message.id(), message.chatRoomId(), message.senderId(), message.senderName(),
            message.content(), mediaUrl, message.mediaType(), message.createdAt(), message.deletedAt());
    }
}
