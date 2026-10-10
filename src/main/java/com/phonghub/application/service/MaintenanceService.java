package com.phonghub.application.service;

import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MaintenanceService implements MaintenanceUseCase {

    private final MaintenanceTicketRepositoryPort maintenanceTicketRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final ContractRepositoryPort contractRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final AuditPort auditPort;
    private final UserRepositoryPort userRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final NotificationRepositoryPort notificationRepository;
    private final MaintenanceSlaPolicy slaPolicy;

    /** Không hỗ trợ thu phí sửa chữa: phiếu có chi phí do người thuê chịu sẽ bị từ chối khi xử lý. */
    public MaintenanceService(
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(maintenanceTicketRepository, roomRepository, tenantRepository, null, null, null,
            currentUserPort, authorizationService);
    }

    public MaintenanceService(
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        AuditPort auditPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this(maintenanceTicketRepository, roomRepository, tenantRepository, contractRepository, invoiceRepository,
            auditPort, currentUserPort, authorizationService, null, null, null);
    }

    public MaintenanceService(
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        AuditPort auditPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        NotificationRepositoryPort notificationRepository
    ) {
        this(maintenanceTicketRepository, roomRepository, tenantRepository, contractRepository, invoiceRepository,
            auditPort, currentUserPort, authorizationService, userRepository, assignmentPort, notificationRepository,
            MaintenanceSlaPolicy.defaults());
    }

    public MaintenanceService(
        MaintenanceTicketRepositoryPort maintenanceTicketRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        AuditPort auditPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentPort,
        NotificationRepositoryPort notificationRepository,
        MaintenanceSlaPolicy slaPolicy
    ) {
        this.slaPolicy = slaPolicy;
        this.userRepository = userRepository;
        this.assignmentPort = assignmentPort;
        this.notificationRepository = notificationRepository;
        this.maintenanceTicketRepository = maintenanceTicketRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditPort = auditPort;
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
            command.priority(),
            command.cause(),
            slaPolicy.forPriority(command.priority())
        );
        ticket.attributeEventsTo(currentUser.id());

        return maintenanceTicketRepository.save(ticket);
    }

    @Override
    public MaintenanceTicket acceptTicket(UUID ticketId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));

        // TECHNICIAN can accept tickets only within assigned property scope
        authorizationService.assertTechnicianCanWorkOnProperty(currentUser, ticket.getPropertyId());
        assertAssignedToTechnician(currentUser, ticket);

        ticket.accept(currentUser.id());
        ticket.attributeEventsTo(currentUser.id());
        return maintenanceTicketRepository.save(ticket);
    }

    @Override
    public MaintenanceTicket assignTechnician(UUID ticketId, UUID technicianId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));
        authorizationService.assertCanManageProperty(currentUser, ticket.getPropertyId());
        if (technicianId == null) {
            throw new IllegalArgumentException("Cần chọn kỹ thuật viên");
        }
        if (userRepository == null || assignmentPort == null) {
            throw new DomainException("Hệ thống chưa cấu hình giao việc cho kỹ thuật viên");
        }
        User technician = userRepository.findById(technicianId)
            .filter(user -> user.role() == UserRole.TECHNICIAN)
            .orElseThrow(() -> new IllegalArgumentException("Người được giao phải là tài khoản kỹ thuật viên"));
        if (!assignmentPort.isUserAssignedToProperty(technician.id(), ticket.getPropertyId())) {
            throw new IllegalArgumentException("Kỹ thuật viên chưa được phân công vào nhà trọ của phiếu này");
        }
        UUID previous = ticket.getAssignedTechnicianId();
        ticket.assign(technician.id());
        ticket.attributeEventsTo(actorIdOf(currentUser));
        MaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        audit("MAINTENANCE_TECHNICIAN_ASSIGNED", currentUser, saved, Map.of(
            "technicianId", technician.id().toString(), "previousTechnicianId", previous == null ? "NONE" : previous.toString()
        ));
        return saved;
    }

    @Override
    public MaintenanceTicket rejectTicket(UUID ticketId, String reason) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));
        authorizationService.assertCanManageProperty(currentUser, ticket.getPropertyId());
        ticket.reject(reason);
        ticket.attributeEventsTo(currentUser.id());
        MaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        audit("MAINTENANCE_TICKET_REJECTED", currentUser, saved, Map.of("reason", saved.getRejectionReason()));
        notifyRequester(saved);
        return saved;
    }

    private static UUID actorIdOf(CurrentUser user) {
        return user.id();
    }

    @Override
    public List<MaintenanceTicketEvent> getTimeline(UUID ticketId) {
        // Cùng quy tắc xem phiếu: tenant của phiếu, owner/staff, technician được giao.
        MaintenanceTicket ticket = getTicket(ticketId);
        return maintenanceTicketRepository.findEvents(ticket.getId());
    }

    private void notifyRequester(MaintenanceTicket ticket) {
        if (notificationRepository == null || userRepository == null || ticket.getRequestedByTenantId() == null) {
            return;
        }
        tenantRepository.findById(ticket.getRequestedByTenantId())
            .filter(tenant -> tenant.userId() != null)
            .flatMap(tenant -> userRepository.findById(tenant.userId()))
            .ifPresent(user -> notificationRepository.save(Notification.createGeneral(
                "Yêu cầu bảo trì bị từ chối",
                "Yêu cầu \"" + ticket.getTitle() + "\" đã bị từ chối. Lý do: " + ticket.getRejectionReason(),
                user.id(), user.username(), user.fullName(), user.role())));
    }

    /** Kỹ thuật viên chỉ làm phiếu được giao cho chính mình (admin không bị giới hạn). */
    private static void assertAssignedToTechnician(CurrentUser user, MaintenanceTicket ticket) {
        if (user.role() == UserRole.TECHNICIAN && !user.id().equals(ticket.getAssignedTechnicianId())) {
            throw new UnauthorizedPropertyAccessException("Phiếu bảo trì này chưa được giao cho bạn");
        }
    }

    private static boolean visibleTo(CurrentUser user, MaintenanceTicket ticket) {
        return user.role() != UserRole.TECHNICIAN || user.id().equals(ticket.getAssignedTechnicianId());
    }

    @Override
    public MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(command.ticketId())
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + command.ticketId()));

        authorizationService.assertTechnicianCanWorkOnProperty(currentUser, ticket.getPropertyId());
        assertAssignedToTechnician(currentUser, ticket);

        // Kiểm tra điều kiện thu phí TRƯỚC khi thay đổi bất kỳ trạng thái nào
        LiableParty liable = command.liableParty() != null ? command.liableParty() : ticket.getLiableParty();
        BigDecimal cost = command.repairCost() != null ? command.repairCost() : ticket.getRepairCost();
        if (liable == LiableParty.UNDETERMINED && cost.signum() > 0) {
            throw new MaintenanceTicketException("Cần xác định bên chịu phí (chủ trọ hoặc người thuê) trước khi ghi nhận chi phí sửa chữa");
        }
        Contract feeContract = null;
        if (liable == LiableParty.TENANT && cost.signum() > 0) {
            feeContract = requireFeeContract(ticket);
        }

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
                 .filter(t -> !t.isWorkFinished())
                 .toList();

                if (!otherOpenTickets.isEmpty()) {
                    throw new InvalidRoomStateException(String.format(
                        "Cannot release room '%s' to AVAILABLE: %d other unresolved ticket(s) exist",
                        room.getRoomNumber(), otherOpenTickets.size()
                    ));
                }
            }
        }

        LiableParty previousLiable = ticket.getLiableParty();
        if (liable != previousLiable) {
            ticket.changeLiableParty(liable);
            audit("MAINTENANCE_LIABLE_PARTY_CHANGED", currentUser, ticket, Map.of(
                "from", previousLiable.name(), "to", liable.name(),
                "reportedCause", ticket.getCauseCategory() != null ? ticket.getCauseCategory().name() : "NONE"
            ));
        }
        ticket.resolve(command.resolutionNotes(), command.repairCost());
        ticket.attributeEventsTo(currentUser.id());
        MaintenanceTicket savedTicket = maintenanceTicketRepository.save(ticket);

        if (feeContract != null && savedTicket.getStatus() == MaintenanceStatus.AWAITING_PAYMENT) {
            Invoice fee = Invoice.issueMaintenanceFee(
                feeContract, savedTicket.getId(), savedTicket.getRepairCost(),
                invoiceRepository.newUniquePaymentCode(), LocalDate.now()
            );
            invoiceRepository.save(fee);
            audit("MAINTENANCE_FEE_ISSUED", currentUser, savedTicket, Map.of(
                "invoiceId", fee.getId().toString(), "amount", fee.getTotalAmount().toPlainString()
            ));
        }

        if (command.releaseRoomToAvailable() && room != null && room.getStatus() == RoomStatus.MAINTENANCE) {
            room.releaseFromMaintenance();
            roomRepository.save(room);
        }

        return savedTicket;
    }

    @Override
    public MaintenanceTicket waiveRepairFee(UUID ticketId, String reason) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));

        authorizationService.assertCanManageProperty(currentUser, ticket.getPropertyId());
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do miễn phí không được để trống");
        }
        if (ticket.getStatus() != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Chỉ có thể miễn phí cho phiếu đang chờ thanh toán: " + ticket.getStatus());
        }
        if (invoiceRepository == null) {
            throw new DomainException("Hệ thống chưa cấu hình thu phí sửa chữa");
        }

        invoiceRepository.findActiveByTicketId(ticketId).ifPresent(invoice -> {
            if (invoice.isConsolidated()) {
                throw new DomainException("Khoản phí đã được gộp vào hóa đơn tháng, không thể miễn từ phiếu này.");
            }
            invoice.voidInvoice();
            invoiceRepository.save(invoice);
        });
        ticket.waiveFee();
        ticket.attributeEventsTo(currentUser.id());
        MaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        audit("MAINTENANCE_FEE_WAIVED", currentUser, saved, Map.of("reason", reason.trim()));
        return saved;
    }

    private Contract requireFeeContract(MaintenanceTicket ticket) {
        if (contractRepository == null || invoiceRepository == null) {
            throw new DomainException("Hệ thống chưa cấu hình thu phí sửa chữa");
        }
        return contractRepository.findActiveByRoomId(ticket.getRoomId())
            .orElseThrow(() -> new DomainException(
                "Phòng không có hợp đồng đang hiệu lực nên không thể thu phí từ người thuê. Hãy chọn chủ trọ chịu phí."
            ));
    }

    private void audit(String action, CurrentUser actor, MaintenanceTicket ticket, Map<String, Object> details) {
        if (auditPort != null) {
            auditPort.recordEvent(AuditPort.AuditEvent.of(
                action, actor.id(), "MAINTENANCE_TICKET", ticket.getId().toString(), details
            ));
        }
    }

    @Override
    public MaintenanceTicket getTicket(UUID ticketId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));

        authorizationService.assertCanAccessProperty(currentUser, ticket.getPropertyId());
        if (currentUser.role() == UserRole.TENANT
            && !authorizationService.canTenantViewTicket(currentUser, ticket.getRoomId(), ticket.getRequestedByTenantId())) {
            throw new UnauthorizedPropertyAccessException("Tenant can only view tickets of their own room");
        }
        if (!visibleTo(currentUser, ticket)) {
            throw new UnauthorizedPropertyAccessException("Phiếu bảo trì này chưa được giao cho bạn");
        }
        return ticket;
    }

    @Override
    public List<MaintenanceTicket> listTicketsForProperty(UUID propertyId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessProperty(currentUser, propertyId);
        List<MaintenanceTicket> tickets = maintenanceTicketRepository.findByPropertyId(propertyId);
        if (currentUser.role() == UserRole.TENANT) {
            return tickets.stream()
                .filter(t -> authorizationService.canTenantViewTicket(currentUser, t.getRoomId(), t.getRequestedByTenantId()))
                .toList();
        }
        return tickets.stream().filter(t -> visibleTo(currentUser, t)).toList();
    }

    @Override
    public List<MaintenanceTicket> listAssignedTicketsForTechnician(UUID technicianId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() != UserRole.ADMIN && !currentUser.id().equals(technicianId)) {
            throw new UnauthorizedPropertyAccessException("Cannot view tickets assigned to another technician");
        }
        List<MaintenanceTicket> assigned = maintenanceTicketRepository.findByAssignedTechnicianId(technicianId);
        if (currentUser.role() == UserRole.TECHNICIAN) {
            var propertyIds = authorizationService.getAccessiblePropertyIds(currentUser);
            return assigned.stream().filter(ticket -> propertyIds.contains(ticket.getPropertyId())).toList();
        }
        return assigned;
    }

    @Override
    public List<MaintenanceTicket> listTicketsForRoom(UUID roomId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));

        authorizationService.assertCanAccessProperty(currentUser, room.getPropertyId());
        if (currentUser.role() == UserRole.TENANT) {
            return maintenanceTicketRepository.findByRoomId(roomId).stream()
                .filter(t -> authorizationService.canTenantViewTicket(currentUser, t.getRoomId(), t.getRequestedByTenantId()))
                .toList();
        }
        return maintenanceTicketRepository.findByRoomId(roomId).stream().filter(t -> visibleTo(currentUser, t)).toList();
    }
}
