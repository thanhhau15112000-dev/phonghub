package com.phonghub.adapter.in.web;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ đếm cửa sổ cố định theo khóa. Khóa luôn gồm tên rule nên các rule không dùng chung bộ đếm.
 * Dữ liệu nằm trong bộ nhớ của một instance; chạy nhiều instance thì mỗi instance đếm riêng.
 */
public class FixedWindowRateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(long startMillis, long windowMillis, int count) {}

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision tryAcquire(String rule, String key, int maxRequests, int windowSeconds) {
        long now = clock.millis();
        long windowMillis = windowSeconds * 1000L;
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= e.getValue().windowMillis());
        }
        Window[] result = new Window[1];
        windows.compute(rule + '|' + key, (k, current) -> {
            Window next = current == null || now - current.startMillis() >= windowMillis
                ? new Window(now, windowMillis, 1)
                : new Window(current.startMillis(), windowMillis, current.count() + 1);
            result[0] = next;
            return next;
        });
        Window w = result[0];
        if (w.count() <= maxRequests) {
            return new Decision(true, 0);
        }
        long remainingMillis = windowMillis - (now - w.startMillis());
        return new Decision(false, Math.max(1, (remainingMillis + 999) / 1000));
    }
}
