package com.phonghub.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache ngắn hạn cho {@link UserRepositoryPort#findById} và {@link UserRepositoryPort#findAllIdRoles}
 * để tránh lặp query + giải mã AES-GCM trên mỗi HTTP request, tự xóa ngay khi lưu/xóa tài khoản.
 */
public class CachingUserRepositoryPort implements UserRepositoryPort {

    private static final String ALL_ROLES_KEY = "all_id_roles";

    private final UserRepositoryPort delegate;
    private final boolean enabled;
    private final Cache<Object, Object> cache;
    private final AtomicLong generation = new AtomicLong();

    public CachingUserRepositoryPort(UserRepositoryPort delegate, boolean enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(2000)
            .build();
    }

    @Override
    public User save(User user) {
        User saved = delegate.save(user);
        invalidate();
        return saved;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<User> findById(UUID id) {
        if (!enabled || id == null || insideWriteTransaction()) {
            return delegate.findById(id);
        }
        Object cached = cache.getIfPresent(id);
        if (cached != null) {
            return (Optional<User>) cached;
        }
        long before = generation.get();
        Optional<User> loaded = delegate.findById(id);
        if (loaded.isPresent()) {
            cache.put(id, loaded);
            if (generation.get() != before) {
                cache.invalidate(id);
            }
        }
        return loaded;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return delegate.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return delegate.findByEmail(email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return delegate.findByPhone(phone);
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return delegate.findByRole(role);
    }

    @Override
    public List<User> findAll() {
        return delegate.findAll();
    }

    @Override
    public void deleteById(UUID id) {
        delegate.deleteById(id);
        invalidate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, UserRole> findAllIdRoles() {
        if (!enabled || insideWriteTransaction()) {
            return delegate.findAllIdRoles();
        }
        Object cached = cache.getIfPresent(ALL_ROLES_KEY);
        if (cached != null) {
            return (Map<UUID, UserRole>) cached;
        }
        long before = generation.get();
        Map<UUID, UserRole> loaded = Map.copyOf(delegate.findAllIdRoles());
        cache.put(ALL_ROLES_KEY, loaded);
        if (generation.get() != before) {
            cache.invalidate(ALL_ROLES_KEY);
        }
        return loaded;
    }

    private void invalidate() {
        generation.incrementAndGet();
        cache.invalidateAll();
    }

    private static boolean insideWriteTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}
