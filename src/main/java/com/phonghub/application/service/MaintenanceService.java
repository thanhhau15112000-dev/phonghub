package com.phonghub.application.service;

import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
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
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
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
            command.cause()
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
            invoice.voidInvoice();
            invoiceRepository.save(invoice);
        });
        ticket.waiveFee();
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
        return tickets;
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
        if (currentUser.role() == UserRole.TENANT) {
            return maintenanceTicketRepository.findByRoomId(roomId).stream()
                .filter(t -> authorizationService.canTenantViewTicket(currentUser, t.getRoomId(), t.getRequestedByTenantId()))
                .toList();
        }
        return maintenanceTicketRepository.findByRoomId(roomId);
    }
}
