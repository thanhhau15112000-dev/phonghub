package com.phonghub.application.port.out;

import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTicketRepositoryPort {
    MaintenanceTicket save(MaintenanceTicket ticket);
    Optional<MaintenanceTicket> findById(UUID id);
    List<MaintenanceTicket> findByPropertyId(UUID propertyId);
    List<MaintenanceTicket> findByRoomId(UUID roomId);
    List<MaintenanceTicket> findByRoomIdAndStatusNot(UUID roomId, MaintenanceStatus status);
    List<MaintenanceTicket> findByAssignedTechnicianId(UUID technicianId);
    List<MaintenanceTicket> findAll();

    /** Lưu mới hoặc cập nhật trạng thái một phiên bản báo giá (#203). */
    default MaintenanceQuoteVersion saveQuoteVersion(MaintenanceQuoteVersion version) {
        throw new UnsupportedOperationException("Lưu phiên bản báo giá chưa được hỗ trợ");
    }

    /** Các phiên bản báo giá của phiếu, cũ trước. */
    default List<MaintenanceQuoteVersion> findQuoteVersions(UUID ticketId) {
        return List.of();
    }

    /** Timeline của phiếu, cũ trước. */
    default List<MaintenanceTicketEvent> findEvents(UUID ticketId) {
        return List.of();
    }
}
