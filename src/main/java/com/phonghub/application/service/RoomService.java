package com.phonghub.application.service;

import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.OptimisticLockConflictException;
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
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AuditPort auditPort;
    private final InvoiceRepositoryPort invoiceRepository;

    public RoomService(
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        ContractRepositoryPort contractRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(roomRepository, propertyRepository, maintenanceTicketRepository, contractRepository, tenantRepository,
            currentUserPort, authorizationService, null);
    }

    public RoomService(
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        ContractRepositoryPort contractRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this(roomRepository, propertyRepository, maintenanceTicketRepository, contractRepository, tenantRepository,
            currentUserPort, authorizationService, auditPort, null);
    }

    public RoomService(
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        ContractRepositoryPort contractRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        InvoiceRepositoryPort invoiceRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.auditPort = auditPort;
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
            .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + command.propertyId()));

        if (property.isArchived()) {
            throw new com.phonghub.domain.exception.ConflictException(
                "Nhà trọ '" + property.name() + "' đã bị ẩn, không thể tạo thêm phòng. Hãy khôi phục nhà trọ trước.");
        }
        if (property.approvalStatus() != PropertyApprovalStatus.VERIFIED) {
            throw new InvalidPropertyStatusException(String.format(
                "Chỉ có thể tạo phòng cho nhà trọ đã được duyệt (VERIFIED). Trạng thái hiện tại: '%s'.",
                property.approvalStatus()
            ));
        }

        Optional<Room> sameNumber = roomRepository.findByPropertyIdAndRoomNumber(
            command.propertyId(), command.roomNumber().trim());
        if (sameNumber.isPresent()) {
            throw new DuplicateRoomNumberException(String.format(sameNumber.get().isArchived()
                    ? "Số phòng '%s' thuộc một phòng đã ngừng sử dụng; hãy khôi phục phòng đó thay vì tạo mới."
                    : "Số phòng '%s' đã tồn tại trong nhà trọ này.",
                command.roomNumber().trim()));
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
        audit("ROOM_CREATED", currentUser, saved, Map.of(
            "roomNumber", saved.getRoomNumber(), "floor", saved.getFloor(),
            "basePrice", saved.getBasePrice().toPlainString(), "maxOccupants", saved.getMaxOccupants()));

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
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + command.roomId()));

        authorizationService.assertOwnerOrAdmin(currentUser, room.getPropertyId());
        assertInUse(room, "sửa thông tin");

        if (command.expectedVersion() != null && command.expectedVersion() != room.getVersion()) {
            throw new OptimisticLockConflictException("Phòng");
        }

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

        Map<String, Object> before = describe(room);
        room.updateInfo(
            command.roomNumber(),
            command.floor(),
            command.areaSqm(),
            command.basePrice(),
            command.maxOccupants()
        );

        Room saved = roomRepository.save(room);
        audit("ROOM_UPDATED", currentUser, saved, Map.of("before", before, "after", describe(saved)));
        return saved;
    }

    @Override
    public List<Room> listRoomsForProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);

        if (currentUser.role() == UserRole.TENANT) {
            return tenantRoom(currentUser, propertyId).map(List::of).orElse(Collections.emptyList());
        }

        return roomRepository.findByPropertyId(propertyId);
    }

    @Override
    public RoomPage searchRooms(RoomSearchCriteria criteria) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, criteria.propertyId());

        if (currentUser.role() == UserRole.TENANT) {
            List<UUID> ownRoomIds = tenantRoom(currentUser, criteria.propertyId()).map(r -> List.of(r.getId()))
                .orElse(List.of());
            return roomRepository.search(criteria.restrictedTo(ownRoomIds));
        }
        return roomRepository.search(criteria);
    }

    /**
     * Người thuê chỉ thấy phòng của mình (chủ hợp đồng hoặc người ở cùng) trong nhà trọ này, kể cả khi hợp đồng đã hết
     * hạn nhưng chưa trả phòng (#87).
     */
    private Optional<Room> tenantRoom(CurrentUser tenantUser, UUID propertyId) {
        Optional<Tenant> tenantOpt = tenantRepository.findByUserId(tenantUser.id());
        if (tenantOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Contract> activeContract = contractRepository.findByPrimaryTenantId(tenantOpt.get().id()).stream()
            .filter(Contract::isResiding)
            .filter(c -> c.getPropertyId().equals(propertyId))
            .findFirst();

        if (activeContract.isEmpty()) {
            activeContract = contractRepository.findByOccupantTenantId(tenantOpt.get().id()).stream()
                .filter(Contract::isResiding)
                .filter(c -> c.getPropertyId().equals(propertyId))
                .findFirst();
        }

        return activeContract.flatMap(c -> roomRepository.findById(c.getRoomId()));
    }

    @Override
    public Room getRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));

        authorizationService.assertCanAccessProperty(currentUser, room.getPropertyId());

        if (currentUser.role() == UserRole.TENANT) {
            Optional<Tenant> tenantOpt = tenantRepository.findByUserId(currentUser.id());
            if (tenantOpt.isEmpty()) {
                throw new UnauthorizedPropertyAccessException("Không tìm thấy hồ sơ người thuê của tài khoản này.");
            }
            UUID tenantId = tenantOpt.get().id();
            boolean isOccupant = contractRepository.findByRoomId(roomId).stream()
                .filter(Contract::isResiding)
                .anyMatch(c -> c.getPrimaryTenantId().equals(tenantId)
                    || c.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantId)));
            if (!isOccupant) {
                throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được phòng mình đang ở.");
            }
        }

        return room;
    }

    @Override
    public Room changeRoomStatus(UUID roomId, RoomStatus targetStatus) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));

        authorizationService.assertCanManageProperty(currentUser, room.getPropertyId());
        assertInUse(room, "đổi trạng thái");

        if (targetStatus == null) {
            throw new IllegalArgumentException("Cần chọn trạng thái phòng.");
        }
        // Phòng còn người ở theo hợp đồng chỉ đổi trạng thái qua kích hoạt/kết thúc hợp đồng; "Đang thuê" chỉ có được
        // khi kích hoạt hợp đồng. Nếu không, phòng có người ở có thể bị đánh dấu Trống hoặc Bảo trì.
        if (targetStatus != room.getStatus()) {
            boolean residing = contractRepository.findByRoomId(roomId).stream().anyMatch(Contract::isResiding);
            if (!room.manualTargets(residing).contains(targetStatus)) {
                throw new InvalidRoomStateException(manualChangeRefusal(room, targetStatus, residing));
            }
        }

        // Rời BẢO TRÌ chỉ khi các phiếu bảo trì của phòng đã xong
        if (room.getStatus() == RoomStatus.MAINTENANCE && targetStatus == RoomStatus.AVAILABLE) {
            List<MaintenanceTicket> openTickets = maintenanceTicketRepository.findByRoomIdAndStatusNot(
                roomId, MaintenanceStatus.RESOLVED
            ).stream().filter(t -> !t.isWorkFinished()).toList();

            if (!openTickets.isEmpty()) {
                throw new InvalidRoomStateException(String.format(
                    "Chưa thể chuyển phòng '%s' sang Trống: còn %d phiếu bảo trì chưa xử lý xong.",
                    room.getRoomNumber(), openTickets.size()
                ));
            }
        }

        RoomStatus oldStatus = room.getStatus();
        room.transitionTo(targetStatus);
        Room saved = roomRepository.save(room);
        audit("ROOM_STATUS_CHANGED", currentUser, saved, Map.of(
            "oldStatus", oldStatus.name(), "newStatus", saved.getStatus().name()));
        return saved;
    }

    @Override
    public Room archiveRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));
        authorizationService.assertOwnerOrAdmin(currentUser, room.getPropertyId());
        if (room.isArchived()) {
            throw new ConflictException("Phòng '" + room.getRoomNumber() + "' đã ngừng sử dụng trước đó.");
        }

        List<Contract> contracts = contractRepository.findByRoomId(roomId);
        long openContracts = contracts.stream().filter(Contract::isOpen).count();
        if (openContracts > 0) {
            throw new ConflictException(String.format(
                "Không thể ngừng sử dụng phòng '%s': còn %d hợp đồng chưa kết thúc (nháp, đang hiệu lực hoặc chưa trả "
                    + "phòng).", room.getRoomNumber(), openContracts));
        }
        if (invoiceRepository != null) {
            long unpaid = contracts.stream()
                .flatMap(c -> invoiceRepository.findByContractId(c.getId()).stream())
                .filter(com.phonghub.domain.model.Invoice::isPayable)
                .count();
            if (unpaid > 0) {
                throw new ConflictException(String.format(
                    "Không thể ngừng sử dụng phòng '%s': còn %d hóa đơn chưa thu xong.", room.getRoomNumber(), unpaid));
            }
        }
        long openTickets = maintenanceTicketRepository.findByRoomIdAndStatusNot(roomId, MaintenanceStatus.RESOLVED)
            .stream().filter(t -> !t.isWorkFinished()).count();
        if (openTickets > 0) {
            throw new ConflictException(String.format(
                "Không thể ngừng sử dụng phòng '%s': còn %d phiếu bảo trì chưa xử lý xong.",
                room.getRoomNumber(), openTickets));
        }

        RoomStatus status = room.getStatus();
        room.archive(java.time.Instant.now());
        Room saved = roomRepository.save(room);
        audit("ROOM_ARCHIVED", currentUser, saved, Map.of("roomNumber", saved.getRoomNumber(), "status", status.name()));
        return saved;
    }

    @Override
    public Room restoreRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng: " + roomId));
        authorizationService.assertOwnerOrAdmin(currentUser, room.getPropertyId());
        if (!room.isArchived()) {
            throw new ConflictException("Phòng '" + room.getRoomNumber() + "' đang được sử dụng, không cần khôi phục.");
        }
        room.restore();
        Room saved = roomRepository.save(room);
        audit("ROOM_RESTORED", currentUser, saved, Map.of("roomNumber", saved.getRoomNumber()));
        return saved;
    }

    private static String manualChangeRefusal(Room room, RoomStatus target, boolean residing) {
        if (residing) {
            return String.format("Phòng '%s' đang có người ở theo hợp đồng; trạng thái phòng chỉ thay đổi khi kích hoạt "
                + "hoặc kết thúc hợp đồng (trả phòng). Sự cố cần sửa thì tạo phiếu bảo trì, không cần đổi trạng thái phòng.",
                room.getRoomNumber());
        }
        if (target == RoomStatus.OCCUPIED) {
            return String.format("Phòng '%s' chỉ chuyển sang Đang thuê khi kích hoạt hợp đồng.", room.getRoomNumber());
        }
        return String.format("Không thể chuyển phòng '%s' từ %s sang %s.", room.getRoomNumber(),
            room.getStatus().label(), target.label());
    }

    private static void assertInUse(Room room, String action) {
        if (room.isArchived()) {
            throw new ConflictException(String.format(
                "Phòng '%s' đã ngừng sử dụng, không thể %s. Hãy khôi phục phòng trước.", room.getRoomNumber(), action));
        }
    }

    private static Map<String, Object> describe(Room room) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roomNumber", room.getRoomNumber());
        state.put("floor", room.getFloor());
        state.put("areaSqm", room.getAreaSqm() == null ? null : room.getAreaSqm().toPlainString());
        state.put("basePrice", room.getBasePrice().toPlainString());
        state.put("maxOccupants", room.getMaxOccupants());
        return state;
    }

    private void audit(String action, CurrentUser actor, Room room, Map<String, Object> details) {
        if (auditPort != null) {
            auditPort.recordEvent(AuditPort.AuditEvent.of(action, actor == null ? null : actor.id(), "ROOM",
                room.getId().toString(), room.getPropertyId(), details));
        }
    }
}
