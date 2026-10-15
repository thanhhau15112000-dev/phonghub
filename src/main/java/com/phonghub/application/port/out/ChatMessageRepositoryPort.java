package com.phonghub.application.port.out;

import com.phonghub.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepositoryPort {
    ChatMessage save(ChatMessage message);
    List<ChatMessage> findByRoomId(UUID roomId, Instant before, int limit);
    Optional<ChatMessage> findLatestMessageByRoomId(UUID roomId);
    int countUnreadMessages(UUID roomId, UUID userId, Instant since);
}
