package com.phonghub.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditPort {

    void recordEvent(AuditEvent event);

    record AuditEvent(
        UUID id,
        String action,
        UUID actorId,
        String targetType,
        String targetId,
        Map<String, Object> details,
        Instant timestamp
    ) {
        public AuditEvent {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public static AuditEvent of(String action, UUID actorId, String targetType, String targetId, Map<String, Object> details) {
            return new AuditEvent(UUID.randomUUID(), action, actorId, targetType, targetId, details, Instant.now());
        }
    }
}
