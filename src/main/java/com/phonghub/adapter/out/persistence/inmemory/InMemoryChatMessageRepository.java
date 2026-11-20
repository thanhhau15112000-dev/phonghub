package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ChatMessageRepositoryPort;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class InMemoryChatMessageRepository implements ChatMessageRepositoryPort {

    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final InMemoryChatRoomRepository chatRoomRepo;

    public InMemoryChatMessageRepository() {
        this(null);
    }

    public InMemoryChatMessageRepository(InMemoryChatRoomRepository chatRoomRepo) {
        this.chatRoomRepo = chatRoomRepo;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        messages.removeIf(m -> m.id().equals(message.id()));
        messages.add(message);
        return message;
    }

    @Override
    public Optional<ChatMessage> findById(UUID id) {
        return messages.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    @Override
    public List<ChatMessage> findByRoomId(UUID roomId, Instant before, int limit) {
        return messages.stream()
            .filter(m -> m.chatRoomId().equals(roomId))
            .filter(m -> before == null || m.createdAt().isBefore(before))
            .sorted(Comparator.comparing(ChatMessage::createdAt).reversed())
            .limit(limit)
            .sorted(Comparator.comparing(ChatMessage::createdAt))
            .toList();
    }

    @Override
    public Optional<ChatMessage> findLatestMessageByRoomId(UUID roomId) {
        return messages.stream()
            .filter(m -> m.chatRoomId().equals(roomId))
            .max(Comparator.comparing(ChatMessage::createdAt));
    }

    @Override
    public int countUnreadMessages(UUID roomId, UUID userId, Instant since) {
        return (int) messages.stream()
            .filter(m -> m.chatRoomId().equals(roomId))
            .filter(m -> !m.senderId().equals(userId))
            .filter(m -> since == null || m.createdAt().isAfter(since))
            .count();
    }

    @Override
    public int countTotalUnread(UUID userId, Set<UUID> visibleGlobalSenderIds) {
        if (chatRoomRepo == null) {
            return (int) messages.stream()
                .filter(m -> !m.senderId().equals(userId))
                .filter(m -> !m.chatRoomId().equals(ChatRoom.GLOBAL_ROOM_ID)
                    || visibleGlobalSenderIds == null
                    || visibleGlobalSenderIds.contains(m.senderId()))
                .count();
        }
        var rooms = chatRoomRepo.findRoomsByUserId(userId);
        int total = 0;
        for (var room : rooms) {
            Instant since = chatRoomRepo.getLastRead(room.id(), userId);
            if (room.isGlobal() && visibleGlobalSenderIds != null) {
                total += (int) messages.stream()
                    .filter(m -> m.chatRoomId().equals(room.id()))
                    .filter(m -> !m.senderId().equals(userId))
                    .filter(m -> visibleGlobalSenderIds.contains(m.senderId()))
                    .filter(m -> since == null || m.createdAt().isAfter(since))
                    .count();
            } else {
                total += countUnreadMessages(room.id(), userId, since);
            }
        }
        return total;
    }

    @Override
    public Map<UUID, ChatMessage> findLatestMessagesByRoomIds(List<UUID> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> set = new HashSet<>(roomIds);
        return messages.stream()
            .filter(m -> set.contains(m.chatRoomId()))
            .collect(Collectors.groupingBy(
                ChatMessage::chatRoomId,
                Collectors.collectingAndThen(
                    Collectors.maxBy(Comparator.comparing(ChatMessage::createdAt).thenComparing(ChatMessage::id)),
                    opt -> opt.orElse(null)
                )
            ));
    }

    @Override
    public Map<UUID, Integer> countUnreadByRoomIds(UUID userId, List<UUID> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (UUID roomId : roomIds) {
            Instant since = chatRoomRepo != null ? chatRoomRepo.getLastRead(roomId, userId) : null;
            int count = countUnreadMessages(roomId, userId, since);
            if (count > 0) {
                result.put(roomId, count);
            }
        }
        return result;
    }

    public void clear() {
        messages.clear();
    }
}
