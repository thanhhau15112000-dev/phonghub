package com.phonghub.application.port.out;

import com.phonghub.domain.model.ChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepositoryPort {
    Optional<ChatRoom> findById(UUID id);
    Optional<ChatRoom> findDirectRoomBetween(UUID user1, UUID user2);
    List<ChatRoom> findRoomsByUserId(UUID userId);
    ChatRoom save(ChatRoom room);
    void updateLastRead(UUID roomId, UUID userId, Instant lastReadAt);
    Instant getLastRead(UUID roomId, UUID userId);
}
