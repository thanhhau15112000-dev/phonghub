package com.phonghub.application.port.out;

import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import java.util.UUID;

/** Đẩy sự kiện chat tới người được đọc phòng ({@link ChatRoom#hasMember}): phòng 1-1 chỉ hai thành viên. */
public interface ChatEventPublisherPort {
    void publishNewMessage(ChatRoom room, ChatMessage message);
    default void publishReadReceipt(ChatRoom room, UUID userId, java.time.Instant readAt) {}
}
