package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.domain.model.Notification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNotificationRepository implements NotificationRepositoryPort {

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
    public long countUnresolved() {
        return store.values().stream().filter(n -> !n.isResolved()).count();
    }

    public void clear() {
        store.clear();
    }
}
