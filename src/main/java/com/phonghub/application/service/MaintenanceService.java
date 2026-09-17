package com.phonghub.application.service;

import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MaintenanceService implements MaintenanceUseCase {

    private final MaintenanceTicketRepositoryPort maintenanceTicketRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public MaintenanceService(
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public MaintenanceTicket createTicket(CreateMaintenanceTicketCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(command.roomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + command.roomId()));

        UUID requestedByTenantId = null;
        if (currentUser.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                throw new UnauthorizedPropertyAccessException("Tenant profile not found");
            }
            requestedByTenantId = tenantOpt.get().id();
            // Tenant can only file tickets for their own property
            authorizationService.assertCanAccessProperty(currentUser, room.getPropertyId());
        } else {
            authorizationService.assertCanManageProperty(currentUser, room.getPropertyId());
        }

        // Fix F3: Validate room transition BEFORE creating/saving ticket to prevent phantom tickets
        if (command.setRoomMaintenance() && room.getStatus() != RoomStatus.MAINTENANCE) {
            if (!room.canTransitionTo(RoomStatus.MAINTENANCE)) {
                throw new InvalidRoomStateException(String.format(
                    "Cannot put room '%s' in status %s under MAINTENANCE",
                    room.getRoomNumber(), room.getStatus()
                ));
            }
            room.putUnderMaintenance();
            roomRepository.save(room);
        }

        MaintenanceTicket ticket = MaintenanceTicket.create(
            room.getId(),
            room.getPropertyId(),
            requestedByTenantId,
            command.title(),
            command.description(),
            command.priority()
        );

        return maintenanceTicketRepository.save(ticket);
    }

    @Override
    public MaintenanceTicket acceptTicket(UUID ticketId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));

        // TECHNICIAN can accept tickets only within assigned property scope
        authorizationService.assertTechnicianCanWorkOnProperty(currentUser, ticket.getPropertyId());

        ticket.accept(currentUser.id());
        return maintenanceTicketRepository.save(ticket);
    }

    @Override
    public MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(command.ticketId())
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + command.ticketId()));

        authorizationService.assertTechnicianCanWorkOnProperty(currentUser, ticket.getPropertyId());

        Room room = null;
        if (command.releaseRoomToAvailable()) {
            room = roomRepository.findById(ticket.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + ticket.getRoomId()));

            if (room.getStatus() == RoomStatus.MAINTENANCE) {
                // Fix F3: Check if any other unresolved tickets exist before modifying/saving ticket
                List<MaintenanceTicket> otherOpenTickets = maintenanceTicketRepository.findByRoomIdAndStatusNot(
                    room.getId(), MaintenanceStatus.RESOLVED
                ).stream()
                 .filter(t -> !t.getId().equals(ticket.getId()))
                 .filter(t -> t.getStatus() != MaintenanceStatus.VERIFIED && t.getStatus() != MaintenanceStatus.REJECTED)
                 .toList();

                if (!otherOpenTickets.isEmpty()) {
                    throw new InvalidRoomStateException(String.format(
                        "Cannot release room '%s' to AVAILABLE: %d other unresolved ticket(s) exist",
                        room.getRoomNumber(), otherOpenTickets.size()
                    ));
                }
            }
        }

        ticket.resolve(command.resolutionNotes(), command.repairCost());
        MaintenanceTicket savedTicket = maintenanceTicketRepository.save(ticket);

        if (command.releaseRoomToAvailable() && room != null && room.getStatus() == RoomStatus.MAINTENANCE) {
            room.releaseFromMaintenance();
            roomRepository.save(room);
        }

        return savedTicket;
    }

    @Override
    public MaintenanceTicket getTicket(UUID ticketId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));

        authorizationService.assertCanAccessProperty(currentUser, ticket.getPropertyId());
        return ticket;
    }

    @Override
    public List<MaintenanceTicket> listTicketsForProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);
        return maintenanceTicketRepository.findByPropertyId(propertyId);
    }

    @Override
    public List<MaintenanceTicket> listAssignedTicketsForTechnician(UUID technicianId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(technicianId)) {
            throw new UnauthorizedPropertyAccessException("Cannot view tickets assigned to another technician");
        }
        return maintenanceTicketRepository.findByAssignedTechnicianId(technicianId);
    }

    @Override
    public List<MaintenanceTicket> listTicketsForRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));

        authorizationService.assertCanAccessProperty(currentUser, room.getPropertyId());
        return maintenanceTicketRepository.findByRoomId(roomId);
    }
}
