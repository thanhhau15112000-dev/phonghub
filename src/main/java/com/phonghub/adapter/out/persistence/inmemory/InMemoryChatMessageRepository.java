package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ChatMessageRepositoryPort;
import com.phonghub.domain.model.ChatMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryChatMessageRepository implements ChatMessageRepositoryPort {

    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public ChatMessage save(ChatMessage message) {
        messages.removeIf(m -> m.id().equals(message.id()));
        messages.add(message);
        return message;
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

    public void clear() {
        messages.clear();
    }
}
