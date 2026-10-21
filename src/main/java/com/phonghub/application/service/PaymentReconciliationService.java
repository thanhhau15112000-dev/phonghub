package com.phonghub.application.service;

import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.InvoiceNotFoundException;
import com.phonghub.domain.exception.ReceiptNotAvailableException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaymentReconciliationService implements PaymentReconciliationUseCase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepositoryPort paymentRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final ContractRepositoryPort contractRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public PaymentReconciliationService(
        PaymentRepositoryPort paymentRepository,
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        StaffPropertyAssignmentPort assignmentPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.assignmentPort = assignmentPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public ReconciliationReport listTransactions(ReconciliationFilter filter) {
        CurrentUser user = currentUserPort.getCurrentUser();
        List<UUID> scope = collectableProperties(user, filter.propertyId());
        // Admin không xem số liệu tiền của nhà trọ (#163): giữ mã giao dịch, trạng thái, nhà/phòng nhưng bỏ số tiền.
        boolean hideMoney = user.role() == UserRole.ADMIN;

        // Một truy vấn hợp đồng cho mỗi nhà trọ trong phạm vi, sau đó một truy vấn giao dịch cho cả phạm vi.
        Map<UUID, Contract> contracts;
        List<PaymentTransaction> transactions;
        if (scope == null) {
            contracts = contractRepository.findAll().stream()
                .collect(Collectors.toMap(Contract::getId, Function.identity()));
            transactions = paymentRepository.findAll();
        } else {
            contracts = scope.stream()
                .flatMap(propertyId -> contractRepository.findByPropertyId(propertyId).stream())
                .collect(Collectors.toMap(Contract::getId, Function.identity(), (a, b) -> a));
            transactions = paymentRepository.findByContractIds(contracts.keySet());
        }

        List<PaymentTransaction> inRange = transactions.stream()
            .filter(tx -> withinDates(tx.createdAt(), filter.from(), filter.to()))
            .toList();
        Map<ReconciliationStatus, Long> counts = new EnumMap<>(ReconciliationStatus.class);
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            counts.put(status, 0L);
        }
        inRange.forEach(tx -> counts.merge(ReconciliationStatus.of(tx), 1L, Long::sum));

        List<PaymentTransaction> selected = inRange.stream()
            .filter(tx -> filter.status() == null || ReconciliationStatus.of(tx) == filter.status())
            .sorted(Comparator.comparing(PaymentTransaction::createdAt).reversed())
            .toList();

        Map<UUID, Room> rooms = roomRepository.findAllById(selected.stream()
                .map(tx -> contracts.get(tx.contractId())).filter(Objects::nonNull).map(Contract::getRoomId)
                .distinct().toList())
            .stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<UUID, Property> properties = propertyRepository.findAllById(selected.stream()
                .map(tx -> contracts.get(tx.contractId())).filter(Objects::nonNull).map(Contract::getPropertyId)
                .distinct().toList())
            .stream().collect(Collectors.toMap(Property::id, Function.identity()));

        List<ReconciliationRow> rows = new ArrayList<>();
        for (PaymentTransaction tx : selected) {
            Contract contract = tx.contractId() == null ? null : contracts.get(tx.contractId());
            Property property = contract == null ? null : properties.get(contract.getPropertyId());
            Room room = contract == null ? null : rooms.get(contract.getRoomId());
            rows.add(new ReconciliationRow(tx.id(), tx.sepayId(), tx.createdAt(), tx.gateway(), tx.referenceCode(),
                hideMoney ? null : tx.transferAmount(), hideMoney ? null : tx.appliedAmount(),
                hideMoney ? null : tx.excessAmount(), ReconciliationStatus.of(tx),
                tx.reviewReason(), tx.contractId(), tx.invoiceId(), property == null ? null : property.id(),
                property == null ? null : property.name(), room == null ? null : room.getRoomNumber()));
        }
        if (hideMoney) {
            return new ReconciliationReport(rows, counts, null, null, null);
        }
        return new ReconciliationReport(rows, counts,
            sum(rows, ReconciliationRow::amount), sum(rows, ReconciliationRow::appliedAmount),
            sum(rows, ReconciliationRow::excessAmount));
    }

    @Override
    public Receipt getReceipt(UUID invoiceId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.TECHNICIAN) {
            throw new UnauthorizedPropertyAccessException("Kỹ thuật viên không xem biên lai thanh toán.");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hóa đơn."));
        authorizationService.assertCanViewInvoice(user, invoice);
        if (invoice.getPaidAmount().signum() == 0) {
            throw new ReceiptNotAvailableException("Hóa đơn chưa có khoản thanh toán nào nên chưa có biên lai.");
        }

        Contract contract = contractRepository.findById(invoice.getContractId()).orElse(null);
        Optional<Property> property = contract == null ? Optional.empty()
            : propertyRepository.findById(contract.getPropertyId());
        Optional<Room> room = roomRepository.findById(invoice.getRoomId());
        Optional<Tenant> tenant = tenantRepository.findById(invoice.getTenantId());
        List<ReceiptPayment> payments = paymentRepository.findByInvoiceId(invoiceId).stream()
            .filter(tx -> tx.appliedAmount() != null && tx.appliedAmount().signum() > 0)
            .map(tx -> new ReceiptPayment(tx.createdAt(), tx.gateway(), tx.referenceCode(), tx.appliedAmount()))
            .toList();
        Instant paidAt = invoice.getPaidAt() != null ? invoice.getPaidAt()
            : payments.stream().map(ReceiptPayment::receivedAt).max(Comparator.naturalOrder()).orElse(null);

        return new Receipt(
            "BL-" + (invoice.getPaymentCode() != null ? invoice.getPaymentCode()
                : invoice.getId().toString().substring(0, 8).toUpperCase()),
            invoice.getId(), invoice.getContractId(), invoice.getType(), invoice.getMonth(), invoice.getYear(),
            invoice.getPaymentCode(),
            property.map(Property::name).orElse(null), property.map(Property::address).orElse(null),
            room.map(Room::getRoomNumber).orElse(null), tenant.map(Tenant::fullName).orElse(null),
            invoice.getTotalAmount(), invoice.getPaidAmount(), invoice.remainingAmount(), invoice.getStatus(), paidAt,
            payments);
    }

    /**
     * Nhà trọ được xem giao dịch: null là toàn hệ thống (admin). Lọc một nhà thì cần quyền thu tiền ở nhà đó; không
     * lọc thì owner lấy nhà của mình, staff lấy nhà được phân công có quyền thu tiền.
     */
    private List<UUID> collectableProperties(CurrentUser user, UUID propertyId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            // Admin vẫn cần thấy giao dịch để hỗ trợ đối soát (kể cả giao dịch chưa khớp hợp đồng nào), nhưng số tiền
            // được che ở listTransactions (#163).
            return propertyId == null ? null : List.of(propertyId);
        }
        if (propertyId != null) {
            authorizationService.assertCanCollectPayment(user, propertyId);
            return List.of(propertyId);
        }
        return switch (user.role()) {
            case OWNER -> propertyRepository.findByOwnerId(user.id()).stream().map(Property::id).toList();
            case STAFF -> assignmentPort.findByUserId(user.id()).stream()
                .filter(StaffPropertyAssignment::canCollectPayment)
                .map(StaffPropertyAssignment::propertyId)
                .toList();
            default -> throw new UnauthorizedPropertyAccessException(
                "Chỉ quản trị viên, chủ nhà trọ hoặc nhân viên có quyền thu tiền được xem giao dịch.");
        };
    }

    private static boolean withinDates(Instant at, LocalDate from, LocalDate to) {
        LocalDate day = at.atZone(ZONE).toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    private static BigDecimal sum(List<ReconciliationRow> rows, Function<ReconciliationRow, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
