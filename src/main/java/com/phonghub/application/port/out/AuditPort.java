package com.phonghub.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditPort {

    void recordEvent(AuditEvent event);

    /**
     * @param propertyId nhà trọ liên quan (nullable); nếu để trống, adapter lưu trữ tự suy ra từ đối tượng bị tác động
     */
    record AuditEvent(
        UUID id,
        String action,
        UUID actorId,
        String targetType,
        String targetId,
        Map<String, Object> details,
        Instant timestamp,
        UUID propertyId
    ) {
        public AuditEvent {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }

        public AuditEvent(
            UUID id,
            String action,
            UUID actorId,
            String targetType,
            String targetId,
            Map<String, Object> details,
            Instant timestamp
        ) {
            this(id, action, actorId, targetType, targetId, details, timestamp, null);
        }

        public static AuditEvent of(String action, UUID actorId, String targetType, String targetId, Map<String, Object> details) {
            return new AuditEvent(UUID.randomUUID(), action, actorId, targetType, targetId, details, Instant.now(), null);
        }

        public static AuditEvent of(
            String action, UUID actorId, String targetType, String targetId, UUID propertyId, Map<String, Object> details
        ) {
            return new AuditEvent(UUID.randomUUID(), action, actorId, targetType, targetId, details, Instant.now(), propertyId);
        }
    }
}
