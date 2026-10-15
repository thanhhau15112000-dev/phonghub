package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ChatRoomRepositoryPort;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomMember;
import com.phonghub.domain.model.ChatRoomType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChatRoomRepository implements ChatRoomRepositoryPort {

    private final Map<UUID, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastReadMap = new ConcurrentHashMap<>();

    public InMemoryChatRoomRepository() {
        // Seed default Global room
        rooms.put(ChatRoom.GLOBAL_ROOM_ID, ChatRoom.createGlobal());
    }

    private String lastReadKey(UUID roomId, UUID userId) {
        return roomId.toString() + ":" + userId.toString();
    }

    @Override
    public Optional<ChatRoom> findById(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    @Override
    public Optional<ChatRoom> findDirectRoomBetween(UUID user1, UUID user2) {
        return rooms.values().stream()
            .filter(ChatRoom::isDirect)
            .filter(r -> {
                boolean hasU1 = r.members().stream().anyMatch(m -> m.userId().equals(user1));
                boolean hasU2 = r.members().stream().anyMatch(m -> m.userId().equals(user2));
                return hasU1 && hasU2;
            })
            .findFirst();
    }

    @Override
    public List<ChatRoom> findRoomsByUserId(UUID userId) {
        List<ChatRoom> result = new ArrayList<>();
        // Always include Global room
        ChatRoom global = rooms.get(ChatRoom.GLOBAL_ROOM_ID);
        if (global != null) {
            result.add(global);
        }

        // Include direct rooms where user is an active member
        rooms.values().stream()
            .filter(r -> r.isDirect() && r.hasMember(userId))
            .forEach(result::add);

        return result;
    }

    @Override
    public ChatRoom save(ChatRoom room) {
        rooms.put(room.id(), room);
        return room;
    }

    @Override
    public void updateLastRead(UUID roomId, UUID userId, Instant lastReadAt) {
        lastReadMap.put(lastReadKey(roomId, userId), lastReadAt);
    }

    @Override
    public Instant getLastRead(UUID roomId, UUID userId) {
        return lastReadMap.get(lastReadKey(roomId, userId));
    }

    public void clear() {
        rooms.clear();
        lastReadMap.clear();
        rooms.put(ChatRoom.GLOBAL_ROOM_ID, ChatRoom.createGlobal());
    }
}
