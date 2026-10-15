package com.phonghub.adapter.out.event;

import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.domain.model.ChatMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseChatEventPublisher implements ChatEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SseChatEventPublisher.class);
    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        // Timeout 30 phút, client sẽ tự reconnect
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
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
    public void publishNewMessage(UUID roomId, ChatMessage message) {
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("new_message")
                        .data(message));
                } catch (Exception e) {
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    @Override
    public void publishReadReceipt(UUID roomId, UUID userId, java.time.Instant readAt) {
        userEmitters.forEach((subUserId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("message_seen")
                        .data(Map.of(
                            "roomId", roomId.toString(),
                            "userId", userId.toString(),
                            "readAt", readAt.toString()
                        )));
                } catch (Exception e) {
                    removeEmitter(subUserId, emitter);
                }
            }
        });
    }
}
