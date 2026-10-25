package com.phonghub.adapter.out.event;

import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseChatEventPublisher implements ChatEventPublisherPort {

    /** Trình duyệt chờ chừng này trước khi tự kết nối lại khi luồng SSE bị ngắt. */
    private static final long RECONNECT_DELAY_MS = 3_000;

    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        // Timeout 30 phút, client sẽ tự reconnect
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").reconnectTime(RECONNECT_DELAY_MS).data("connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> list = userEmitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    @Override
    public void publishNewMessage(ChatRoom room, ChatMessage message) {
        sendToReaders(room, () -> SseEmitter.event().name("new_message").data(message));
    }

    @Override
    public void publishReadReceipt(ChatRoom room, UUID userId, Instant readAt) {
        Map<String, String> receipt = Map.of(
            "roomId", room.id().toString(),
            "userId", userId.toString(),
            "readAt", readAt.toString()
        );
        sendToReaders(room, () -> SseEmitter.event().name("message_seen").data(receipt));
    }

    /**
     * Gửi dòng chú thích cho mọi kết nối đang mở. Proxy (Cloudflare, Render) cắt kết nối im lặng quá lâu,
     * và trình duyệt không tự kết nối lại khi proxy trả trang lỗi thay cho luồng SSE.
     */
    public void sendHeartbeat() {
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                send(userId, emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    /** Mỗi lần gửi cần builder mới: {@code build()} ghi thêm vào chính builder. */
    private void sendToReaders(ChatRoom room, Supplier<SseEmitter.SseEventBuilder> event) {
        userEmitters.forEach((userId, emitters) -> {
            if (!room.hasMember(userId)) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                send(userId, emitter, event.get());
            }
        });
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            removeEmitter(userId, emitter);
        }
    }
}
