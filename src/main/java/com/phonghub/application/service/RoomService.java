package com.phonghub.application.service;

import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RoomService implements RoomUseCase {

    private final RoomRepositoryPort roomRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final MaintenanceTicketRepositoryPort maintenanceTicketRepository;
    private final ContractRepositoryPort contractRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public RoomService(
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        ContractRepositoryPort contractRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
        this.maintenanceTicketRepository = maintenanceTicketRepository;
        this.contractRepository = contractRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public Room createRoom(CreateRoomCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, command.propertyId());

        if (!propertyRepository.existsById(command.propertyId())) {
            throw new PropertyNotFoundException("Property not found with ID: " + command.propertyId());
        }

        if (roomRepository.findByPropertyIdAndRoomNumber(command.propertyId(), command.roomNumber().trim()).isPresent()) {
            throw new DomainException(String.format(
                "Room number '%s' already exists in this property", command.roomNumber().trim()
            ));
        }

        Room room = Room.create(
            command.propertyId(),
            command.roomNumber().trim(),
            command.floor(),
            command.areaSqm(),
            command.basePrice(),
            command.maxOccupants()
        );

        return roomRepository.save(room);
    }

    @Override
    public List<Room> listRoomsForProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);

        if (currentUser.role() == UserRole.TENANT) {
            // Tenant can only see their own room (primary or additional occupant)
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                return Collections.emptyList();
            }
            Optional<Contract> activeContract = contractRepository.findByPrimaryTenantId(tenantOpt.get().id()).stream()
                .filter(Contract::isActive)
                .filter(c -> c.getPropertyId().equals(propertyId))
                .findFirst();

            if (activeContract.isEmpty()) {
                activeContract = contractRepository.findByOccupantTenantId(tenantOpt.get().id()).stream()
                    .filter(Contract::isActive)
                    .filter(c -> c.getPropertyId().equals(propertyId))
                    .findFirst();
            }

            return activeContract.flatMap(c -> roomRepository.findById(c.getRoomId()))
                .map(List::of)
                .orElse(Collections.emptyList());
        }

        return roomRepository.findByPropertyId(propertyId);
    }

    @Override
    public Room getRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));

        authorizationService.assertCanAccessProperty(currentUser, room.getPropertyId());

        if (currentUser.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                throw new UnauthorizedPropertyAccessException("Tenant profile not found");
            }
            Optional<Contract> activeContract = contractRepository.findActiveByRoomId(roomId);
            boolean isOccupant = activeContract.isPresent() &&
                (activeContract.get().getPrimaryTenantId().equals(tenantOpt.get().id()) ||
                 activeContract.get().getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantOpt.get().id())));
            if (!isOccupant) {
                throw new UnauthorizedPropertyAccessException("Tenant cannot access rooms other than their assigned room");
            }
        }

        return room;
    }

    @Override
    public Room changeRoomStatus(UUID roomId, RoomStatus targetStatus) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));

        authorizationService.assertCanManageProperty(currentUser, room.getPropertyId());

        // Invariant F2: Cannot manually set an OCCUPIED room to AVAILABLE if an ACTIVE contract exists
        if (room.getStatus() == RoomStatus.OCCUPIED && targetStatus == RoomStatus.AVAILABLE) {
            Optional<Contract> activeContract = contractRepository.findActiveByRoomId(roomId);
            if (activeContract.isPresent()) {
                throw new InvalidRoomStateException(String.format(
                    "Cannot manually change status of room '%s' to AVAILABLE: an ACTIVE contract exists (ID: %s). Contract must be terminated via contract lifecycle.",
                    room.getRoomNumber(), activeContract.get().getId()
                ));
            }
        }

        // Invariant: Leaving MAINTENANCE requires that all maintenance tickets are resolved
        if (room.getStatus() == RoomStatus.MAINTENANCE && targetStatus == RoomStatus.AVAILABLE) {
            List<MaintenanceTicket> openTickets = maintenanceTicketRepository.findByRoomIdAndStatusNot(
                roomId, MaintenanceStatus.RESOLVED
            ).stream().filter(t -> t.getStatus() != MaintenanceStatus.VERIFIED && t.getStatus() != MaintenanceStatus.REJECTED).toList();

            if (!openTickets.isEmpty()) {
                throw new InvalidRoomStateException(String.format(
                    "Cannot release room '%s' from MAINTENANCE: %d unresolved maintenance ticket(s) exist",
                    room.getRoomNumber(), openTickets.size()
                ));
            }
        }

        room.transitionTo(targetStatus);
        return roomRepository.save(room);
    }
}
