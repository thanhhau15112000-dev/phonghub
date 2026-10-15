package com.phonghub.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChatRoom(
    UUID id,
    ChatRoomType type,
    String name,
    Instant createdAt,
    List<ChatRoomMember> members
) {
    public static final UUID GLOBAL_ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public ChatRoom {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        members = members == null ? Collections.emptyList() : List.copyOf(members);
    }

    public static ChatRoom createGlobal() {
        return new ChatRoom(
            GLOBAL_ROOM_ID,
            ChatRoomType.GLOBAL,
            "Kênh Chung Ban Quản Lý",
            Instant.now(),
            Collections.emptyList()
        );
    }

    public static ChatRoom createDirect(String name, List<ChatRoomMember> members) {
        return new ChatRoom(
            UUID.randomUUID(),
            ChatRoomType.DIRECT,
            name,
            Instant.now(),
            members
        );
    }

    public boolean isGlobal() {
        return type == ChatRoomType.GLOBAL;
    }

    public boolean isDirect() {
        return type == ChatRoomType.DIRECT;
    }

    public boolean hasMember(UUID userId) {
        if (isGlobal()) {
            return true;
        }
        return members.stream().anyMatch(m -> m.userId().equals(userId) && m.leftAt() == null);
    }

    public java.util.Optional<ChatRoomMember> getPeerMember(UUID userId) {
        if (!isDirect()) {
            return java.util.Optional.empty();
        }
        return members.stream()
            .filter(m -> !m.userId().equals(userId) && m.leftAt() == null)
            .findFirst();
    }
}
