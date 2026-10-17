package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.domain.model.Notification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryNotificationRepository implements NotificationRepositoryPort {

    private static final Comparator<Notification> NEWEST_FIRST =
        Comparator.comparing(Notification::createdAt).reversed();

    private final Map<UUID, Notification> store = new ConcurrentHashMap<>();

    @Override
    public Notification save(Notification notification) {
        store.put(notification.id(), notification);
        return notification;
    }

    @Override
    public List<Notification> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    @Override
    public List<Notification> findForAdmins() {
        return store.values().stream().filter(Notification::isForAdmins).sorted(NEWEST_FIRST).toList();
    }

    @Override
    public long countUnresolvedForAdmins() {
        return store.values().stream().filter(n -> n.isForAdmins() && !n.isResolved()).count();
    }

    @Override
    public List<Notification> findByRecipient(UUID userId, boolean unreadOnly, int offset, int limit) {
        return inbox(userId, unreadOnly).sorted(NEWEST_FIRST).skip(offset).limit(limit).toList();
    }

    @Override
    public long countByRecipient(UUID userId, boolean unreadOnly) {
        return inbox(userId, unreadOnly).count();
    }

    @Override
    public int markAllRead(UUID userId, Instant at) {
        List<Notification> unread = inbox(userId, true).toList();
        unread.forEach(n -> n.markRead(at));
        return unread.size();
    }

    private Stream<Notification> inbox(UUID userId, boolean unreadOnly) {
        return store.values().stream().filter(n -> n.isAddressedTo(userId) && (!unreadOnly || !n.isRead()));
    }

    public void clear() {
        store.clear();
    }
}
