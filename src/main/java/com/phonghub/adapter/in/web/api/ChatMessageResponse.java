package com.phonghub.adapter.in.web.api;

import com.phonghub.domain.model.ChatAttachment;
import com.phonghub.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tin nhắn chat trả qua REST và SSE (#251). Không đưa khóa đối tượng trong kho ra ngoài: tệp đính kèm chỉ có đường dẫn
 * tới endpoint của phòng chat, endpoint đó kiểm tra quyền đọc phòng rồi mới trả tệp. Tin cũ gửi trước #201 chỉ báo
 * {@code legacyMedia}, không trả lại URL bucket công khai.
 */
public record ChatMessageResponse(
    UUID id,
    UUID chatRoomId,
    UUID senderId,
    String senderName,
    String content,
    boolean legacyMedia,
    List<Attachment> attachments,
    Instant createdAt,
    Instant deletedAt
) {
    public record Attachment(UUID id, String mediaType, String contentType, long sizeBytes, String url) {}

    public static ChatMessageResponse from(ChatMessage message) {
        List<Attachment> attachments = message.attachments().stream()
            .map(attachment -> toAttachment(message, attachment))
            .toList();
        return new ChatMessageResponse(message.id(), message.chatRoomId(), message.senderId(), message.senderName(),
            message.content(), message.hasMedia(), attachments, message.createdAt(), message.deletedAt());
    }

    private static Attachment toAttachment(ChatMessage message, ChatAttachment attachment) {
        return new Attachment(attachment.id(), attachment.mediaType(), attachment.contentType(), attachment.sizeBytes(),
            "/api/chat/rooms/" + message.chatRoomId() + "/messages/" + message.id() + "/attachments/" + attachment.id());
    }
}
