package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepositoryPort {

    private final Map<UUID, User> store = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        store.put(user.id(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return store.values().stream()
            .filter(u -> u.username() != null && username.trim().equalsIgnoreCase(u.username().trim()))
            .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return store.values().stream()
            .filter(u -> email.trim().equalsIgnoreCase(u.email().trim()))
            .findFirst();
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return store.values().stream()
            .filter(u -> u.role() == role)
            .toList();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }
}
