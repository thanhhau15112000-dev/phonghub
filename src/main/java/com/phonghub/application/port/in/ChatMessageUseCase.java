package com.phonghub.application.port.in;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatMessage;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageUseCase {
    ChatMessage sendMessage(CurrentUser currentUser, UUID roomId, String content);

    /**
     * Gửi ảnh/video kèm chữ (#251) cho người được gửi tin trong phòng: tối đa 5 tệp, trong đó tối đa 1 video. Ảnh
     * jpeg/png/webp được kiểm tra chữ ký và bỏ EXIF; video mp4/webm/mov chỉ kiểm tra chữ ký. Tệp lưu ở kho riêng tư.
     */
    ChatMessage sendMediaMessage(CurrentUser currentUser, UUID roomId, String caption, List<MediaUpload> uploads);

    /** URL ký ngắn hạn của tệp đính kèm, sau khi kiểm tra quyền đọc phòng chat. */
    URI attachmentSignedUrl(CurrentUser currentUser, UUID roomId, UUID messageId, UUID attachmentId);

    /** Nội dung tệp đính kèm khi kho không cho trình duyệt mở trực tiếp (profile docker/test), cùng kiểm tra quyền. */
    AttachmentFile attachmentFile(CurrentUser currentUser, UUID roomId, UUID messageId, UUID attachmentId);

    List<ChatMessage> getMessages(CurrentUser currentUser, UUID roomId, Instant before, int limit);
    void markAsRead(CurrentUser currentUser, UUID roomId);
    int getTotalUnreadCount(CurrentUser currentUser);

    record MediaUpload(String contentType, byte[] content) {}

    record AttachmentFile(String contentType, byte[] content) {}
}
