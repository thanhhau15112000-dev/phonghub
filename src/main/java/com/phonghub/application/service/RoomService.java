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
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.time.LocalDate;
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

        Property property = propertyRepository.findById(command.propertyId())
            .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + command.propertyId()));

        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            throw new InvalidPropertyStatusException(String.format(
                "Chỉ có thể tạo phòng cho nhà trọ đã được duyệt (VERIFIED). Trạng thái hiện tại: '%s'.",
                property.approvalStatus()
            ));
        }

        if (roomRepository.findByPropertyIdAndRoomNumber(command.propertyId(), command.roomNumber().trim()).isPresent()) {
            throw new DuplicateRoomNumberException(String.format(
                "Số phòng '%s' đã tồn tại trong nhà trọ này.", command.roomNumber().trim()
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

        Room saved = roomRepository.save(room);

        // Tự động đồng bộ total_rooms nếu số phòng thực tế vượt quá ước lượng ban đầu
        int currentRoomCount = roomRepository.findByPropertyId(command.propertyId()).size();
        if (currentRoomCount > property.totalRooms()) {
            Property updatedProperty = new Property(
                property.id(),
                property.name(),
                property.address(),
                property.description(),
                currentRoomCount,
                property.ownerId(),
                property.approvalStatus(),
                property.rejectionReason(),
                property.createdAt()
            );
            propertyRepository.save(updatedProperty);
        }

        return saved;
    }

    @Override
    public Room updateRoom(UpdateRoomCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(command.roomId())
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + command.roomId()));

        authorizationService.assertOwnerOrAdmin(currentUser, room.getPropertyId());

        Optional<Room> duplicate = roomRepository.findByPropertyIdAndRoomNumber(room.getPropertyId(), command.roomNumber().trim());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(room.getId())) {
            throw new DuplicateRoomNumberException(String.format(
                "Số phòng '%s' đã tồn tại trong nhà trọ này.", command.roomNumber().trim()
            ));
        }

        Optional<Contract> activeContract = contractRepository.findActiveByRoomId(room.getId());
        if (activeContract.isPresent()) {
            LocalDate today = LocalDate.now();
            long residingCount = (activeContract.get().getOccupants() == null || activeContract.get().getOccupants().isEmpty())
                ? 1
                : Math.max(1, activeContract.get().getOccupants().stream()
                    .filter(o -> o.checkOutDate() == null || o.checkOutDate().isAfter(today))
                    .count());

            if (command.maxOccupants() < residingCount) {
                throw new InvalidRoomCapacityException(String.format(
                    "Không thể giảm số người tối đa (%d) xuống dưới số người đang ở theo hợp đồng hiệu lực (%d).",
                    command.maxOccupants(), residingCount
                ));
            }
        }

        room.updateInfo(
            command.roomNumber(),
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

        // Invariant F2: Cannot manually set an OCCUPIED room to AVAILABLE
        if (room.getStatus() == RoomStatus.OCCUPIED && targetStatus == RoomStatus.AVAILABLE) {
            Optional<Contract> activeContract = contractRepository.findActiveByRoomId(roomId);
            if (activeContract.isPresent()) {
                throw new InvalidRoomStateException(String.format(
                    "Cannot manually change status of room '%s' to AVAILABLE: an ACTIVE contract exists (ID: %s). Contract must be terminated via contract lifecycle.",
                    room.getRoomNumber(), activeContract.get().getId()
                ));
            }
            throw new InvalidRoomStateException(String.format(
                "Cannot manually change status of room '%s' to AVAILABLE: room is OCCUPIED. Room must be vacated via contract lifecycle or transitioned to MAINTENANCE.",
                room.getRoomNumber()
            ));
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
