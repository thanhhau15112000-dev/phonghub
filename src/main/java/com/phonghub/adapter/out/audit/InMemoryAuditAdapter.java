package com.phonghub.adapter.out.audit;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.service.AuditSanitizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InMemoryAuditAdapter implements AuditPort, AuditQueryPort {

    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recordEvent(AuditEvent event) {
        if (event != null) {
            events.add(new AuditEvent(event.id(), event.action(), event.actorId(), event.targetType(),
                event.targetId(), AuditSanitizer.sanitize(event.details()), event.timestamp(), event.propertyId()));
        }
    }

    public List<AuditEvent> getRecordedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }

    @Override
    public List<Entry> search(Filter filter) {
        if (filter.propertyIds() != null && filter.propertyIds().isEmpty()) {
            return List.of();
        }
        return getRecordedEvents().stream()
            .filter(e -> filter.propertyIds() == null || filter.propertyIds().contains(propertyOf(e)))
            .filter(e -> inScope(filter.scope(), e))
            .filter(e -> filter.actorId() == null || filter.actorId().equals(e.actorId()))
            .filter(e -> blank(filter.targetType()) || filter.targetType().equals(e.targetType()))
            .filter(e -> blank(filter.action()) || filter.action().equals(e.action()))
            .filter(e -> filter.from() == null || !e.timestamp().isBefore(filter.from()))
            .filter(e -> filter.to() == null || e.timestamp().isBefore(filter.to()))
            .sorted(Comparator.comparing((AuditEvent e) -> e.timestamp()).reversed())
            .skip(filter.offset())
            .limit(filter.limit())
            .map(e -> new Entry(e.id(), e.action(), e.actorId(), null, e.targetType(), e.targetId(), propertyOf(e),
                e.details() == null ? null : e.details().toString(), e.timestamp()))
            .toList();
    }

    /** Bản trong bộ nhớ không có dữ liệu quan hệ: chỉ hiểu phạm vi nhà trọ và sự kiện của chính người xem. */
    private static boolean inScope(Scope scope, AuditEvent e) {
        if (scope == null) {
            return true;
        }
        UUID self = scope.selfUserId() != null ? scope.selfUserId() : scope.tenantUserId();
        return (scope.propertyIds() != null && scope.propertyIds().contains(propertyOf(e)))
            || (self != null && self.equals(e.actorId()));
    }

    private static UUID propertyOf(AuditEvent e) {
        if (e.propertyId() != null) {
            return e.propertyId();
        }
        if ("PROPERTY".equals(e.targetType()) && e.targetId() != null) {
            try {
                return UUID.fromString(e.targetId());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
