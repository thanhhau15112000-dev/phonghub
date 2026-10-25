package com.phonghub.adapter.in.scheduling;

import com.phonghub.adapter.out.event.SseChatEventPublisher;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Giữ các luồng SSE của chat không bị proxy cắt vì im lặng (#201). Cloudflare đóng kết nối không có dữ liệu
 * sau khoảng 100 giây và trả trang lỗi, khi đó trình duyệt bỏ hẳn luồng SSE thay vì tự kết nối lại.
 * Chạy bằng luồng riêng thay vì {@code @Scheduled}: {@code SchedulingConfig} chỉ bật khi tự sinh hóa đơn được bật,
 * còn heartbeat phải luôn chạy.
 */
@Component
public class ChatSseHeartbeatScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ChatSseHeartbeatScheduler.class);
    private static final long INTERVAL_SECONDS = 25;

    private final SseChatEventPublisher publisher;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ChatSseHeartbeatScheduler(SseChatEventPublisher publisher) {
        this.publisher = publisher;
        executor.scheduleWithFixedDelay(this::heartbeat, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void heartbeat() {
        try {
            publisher.sendHeartbeat();
        } catch (RuntimeException ex) {
            // Lỗi lọt ra ngoài sẽ dừng hẳn lịch chạy của executor
            log.warn("Gửi heartbeat SSE chat bị lỗi", ex);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
