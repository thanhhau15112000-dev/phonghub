package com.phonghub.application.port.out;

import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
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
}
