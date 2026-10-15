package com.phonghub.application.port.out;

import com.phonghub.domain.model.ChatMessage;
import java.util.UUID;

public interface ChatEventPublisherPort {
    void publishNewMessage(UUID roomId, ChatMessage message);
    default void publishReadReceipt(UUID roomId, UUID userId, java.time.Instant readAt) {}
}
