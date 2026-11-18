package com.phonghub.application.port.out;

import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import java.util.UUID;

/** Đẩy sự kiện chat tới người được đọc phòng ({@link ChatRoom#hasMember}): phòng 1-1 chỉ hai thành viên. */
public interface ChatEventPublisherPort {
    void publishNewMessage(ChatRoom room, ChatMessage message);

    /**
     * Kênh chung chỉ đẩy tới {@code audience} (người cùng nhà trọ với người gửi, #345). Bản mặc định bỏ qua giới hạn,
     * nên nơi đẩy tin thật phải ghi đè.
     */
    default void publishNewMessage(ChatRoom room, ChatMessage message, java.util.Set<UUID> audience) {
        publishNewMessage(room, message);
    }
    default void publishReadReceipt(ChatRoom room, UUID userId, java.time.Instant readAt) {}
}
