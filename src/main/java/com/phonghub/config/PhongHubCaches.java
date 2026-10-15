package com.phonghub.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache Caffeine cho danh sách nhà trọ/phòng và dashboard owner (#88).
 *
 * <ul>
 *   <li>Khóa luôn gồm id người dùng ({@link Key}): kết quả đã qua kiểm tra quyền của người này nên hai người (ví dụ hai
 *       chủ trọ) không bao giờ dùng chung một entry.</li>
 *   <li>Xóa toàn bộ ({@link #evictAll()}) mỗi khi dữ liệu đổi: sau mỗi transaction ghi commit thành công và sau các thao
 *       tác ghi nhà trọ/phòng (không chạy trong transaction). Thô nhưng đúng: ghi ít hơn đọc rất nhiều.</li>
 *   <li>TTL và kích thước tối đa cấu hình được; TTL là lưới an toàn cuối cùng.</li>
 * </ul>
 */
public class PhongHubCaches {

    /** {@code area} phân biệt loại dữ liệu, {@code args} là tham số truy vấn (record/UUID có equals). */
    public record Key(String area, UUID userId, Object args) {}

    private final boolean enabled;
    private final Cache<Key, Object> cache;
    /** Tăng mỗi lần xóa cache: kết quả tải trước lần xóa không được giữ lại (tránh đua giữa đọc và ghi). */
    private final AtomicLong generation = new AtomicLong();

    public PhongHubCaches(boolean enabled, Duration ttl, long maxSize) {
        this(enabled, ttl, maxSize, Ticker.systemTicker());
    }

    PhongHubCaches(boolean enabled, Duration ttl, long maxSize, Ticker ticker) {
        if (ttl.isNegative() || ttl.isZero() || maxSize <= 0) {
            throw new IllegalArgumentException("TTL và kích thước tối đa của cache phải lớn hơn 0");
        }
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).ticker(ticker).build();
    }

    /**
     * Trả giá trị đã cache hoặc gọi {@code loader} rồi cache lại. Không dùng compute của Caffeine để loader được phép
     * đọc cache khác; lỗi của loader không được cache nên lần sau vẫn kiểm tra quyền lại.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Key key, Supplier<T> loader) {
        if (!enabled || insideWriteTransaction()) {
            // Trong transaction ghi có thể đọc thấy dữ liệu chưa commit (và có thể bị rollback): không đọc/ghi cache.
            return loader.get();
        }
        Object cached = cache.getIfPresent(key);
        if (cached != null) {
            return (T) cached;
        }
        long before = generation.get();
        T value = loader.get();
        if (value == null) {
            return null;
        }
        T stored = value instanceof List<?> list ? (T) List.copyOf(list) : value;
        cache.put(key, stored);
        if (generation.get() != before) {
            // Dữ liệu đổi trong lúc đang tải: bản vừa tải có thể đã cũ.
            cache.invalidate(key);
        }
        return stored;
    }

    private static boolean insideWriteTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    public void evictAll() {
        generation.incrementAndGet();
        cache.invalidateAll();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
