package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChatRoomMember(
    UUID id,
    UUID chatRoomId,
    UUID userId,
    String displayName,
    UserRole role,
    Instant joinedAt,
    Instant leftAt,
    Instant lastReadAt
) {
    public ChatRoomMember {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(chatRoomId, "chatRoomId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public static ChatRoomMember create(UUID chatRoomId, UUID userId, String displayName, UserRole role) {
        return new ChatRoomMember(
            UUID.randomUUID(),
            chatRoomId,
            userId,
            displayName,
            role,
            Instant.now(),
            null,
            Instant.now()
        );
    }

    public ChatRoomMember withLastReadAt(Instant lastReadAt) {
        return new ChatRoomMember(id, chatRoomId, userId, displayName, role, joinedAt, leftAt, lastReadAt);
    }
}
