package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Một mốc trong timeline phiếu bảo trì: chuyển trạng thái, người thực hiện (null nếu hệ thống) và ghi chú ngắn. */
public record MaintenanceTicketEvent(
    UUID ticketId,
    MaintenanceStatus fromStatus,
    MaintenanceStatus toStatus,
    UUID actorId,
    String note,
    Instant at
) {
    public MaintenanceTicketEvent {
        if (ticketId == null || toStatus == null || at == null) {
            throw new IllegalArgumentException("Event requires ticket, target status and time");
        }
    }

    public MaintenanceTicketEvent withActor(UUID actor) {
        return new MaintenanceTicketEvent(ticketId, fromStatus, toStatus, actor, note, at);
    }
}
