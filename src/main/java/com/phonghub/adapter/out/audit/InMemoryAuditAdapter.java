package com.phonghub.adapter.out.audit;

import com.phonghub.application.port.out.AuditPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAuditAdapter implements AuditPort {

    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recordEvent(AuditEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public List<AuditEvent> getRecordedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }
}
