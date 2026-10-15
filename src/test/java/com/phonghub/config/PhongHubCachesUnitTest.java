package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** #88: cache theo người dùng, xóa khi dữ liệu đổi, TTL/kích thước cấu hình được. */
class PhongHubCachesUnitTest {

    private final AtomicLong nanos = new AtomicLong();
    private final PhongHubCaches caches = new PhongHubCaches(true, Duration.ofMinutes(5), 100, nanos::get);
    private final AtomicInteger loads = new AtomicInteger();
    private final UUID owner1 = UUID.randomUUID();
    private final UUID owner2 = UUID.randomUUID();

    private PhongHubCaches.Key key(UUID user) {
        return new PhongHubCaches.Key("properties", user, null);
    }

    private List<String> load(String value) {
        loads.incrementAndGet();
        return new ArrayList<>(List.of(value));
    }

    @Test
    @DisplayName("cùng người dùng đọc lại từ cache; hai chủ trọ không dùng chung entry")
    void perUserEntries() {
        assertEquals(List.of("nhà A"), caches.get(key(owner1), () -> load("nhà A")));
        assertEquals(List.of("nhà A"), caches.get(key(owner1), () -> load("nhà khác")));
        assertEquals(1, loads.get());

        assertEquals(List.of("nhà B"), caches.get(key(owner2), () -> load("nhà B")));
        assertEquals(2, loads.get());
        assertEquals(2, caches.size());
        assertThrows(UnsupportedOperationException.class,
            () -> caches.<List<String>>get(key(owner1), () -> load("x")).add("sửa"), "danh sách cache không sửa được");
    }

    @Test
    @DisplayName("xóa cache thì đọc lại dữ liệu mới; hết TTL cũng đọc lại")
    void evictAndTtl() {
        caches.get(key(owner1), () -> load("cũ"));
        caches.evictAll();
        assertEquals(List.of("mới"), caches.get(key(owner1), () -> load("mới")));

        nanos.addAndGet(Duration.ofMinutes(5).plusSeconds(1).toNanos());
        assertEquals(List.of("sau TTL"), caches.get(key(owner1), () -> load("sau TTL")));
        assertEquals(3, loads.get());
    }

    @Test
    @DisplayName("dữ liệu đổi trong lúc đang tải: bản vừa tải không được giữ lại")
    void evictDuringLoadDiscardsResult() {
        caches.get(key(owner1), () -> {
            caches.evictAll();
            return load("tải trước khi ghi xong");
        });
        assertEquals(List.of("mới"), caches.get(key(owner1), () -> load("mới")));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("lỗi (ví dụ 403) không được cache; trong transaction ghi không đọc/ghi cache; tắt cache thì luôn tải")
    void errorsTransactionsAndDisabled() {
        assertThrows(IllegalStateException.class, () -> caches.get(key(owner1), () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("403");
        }));
        caches.get(key(owner1), () -> load("ok"));
        assertEquals(2, loads.get());

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(List.of("chưa commit"), caches.get(key(owner1), () -> load("chưa commit")));
            caches.get(key(owner2), () -> load("trong transaction"));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertEquals(List.of("ok"), caches.get(key(owner1), () -> load("khác")));
        assertEquals(1, caches.size(), "không cache gì trong transaction ghi");

        PhongHubCaches disabled = new PhongHubCaches(false, Duration.ofMinutes(5), 100);
        disabled.get(key(owner1), () -> load("a"));
        disabled.get(key(owner1), () -> load("a"));
        assertEquals(6, loads.get());
        assertThrows(IllegalArgumentException.class, () -> new PhongHubCaches(true, Duration.ZERO, 100));
        assertThrows(IllegalArgumentException.class, () -> new PhongHubCaches(true, Duration.ofSeconds(1), 0));
    }
}
