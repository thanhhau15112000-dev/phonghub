package com.phonghub.application.service;

import com.phonghub.application.port.in.PaymentHistoryUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PaymentClaimRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoicePaymentRecord;
import com.phonghub.domain.model.PaymentClaim;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaymentHistoryService implements PaymentHistoryUseCase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepositoryPort paymentRepository;
    private final InvoicePaymentRecordRepositoryPort paymentRecordRepository;
    private final PaymentClaimRepositoryPort claimRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final ContractRepositoryPort contractRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    public PaymentHistoryService(
        PaymentRepositoryPort paymentRepository,
        InvoicePaymentRecordRepositoryPort paymentRecordRepository,
        PaymentClaimRepositoryPort claimRepository,
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.claimRepository = claimRepository;
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public PaymentHistory history(UUID tenantId, Filter filter) {
        CurrentUser user = currentUserPort.getCurrentUser();
        List<Contract> contracts = visibleContracts(user, tenantId);
        Filter criteria = filter == null ? Filter.all() : filter;
        if (criteria.contractId() != null) {
            contracts = contracts.stream().filter(c -> c.getId().equals(criteria.contractId())).toList();
        }
        Map<UUID, Contract> byId = contracts.stream()
            .collect(Collectors.toMap(Contract::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Invoice> invoices = new java.util.HashMap<>();
        for (Contract contract : contracts) {
            invoiceRepository.findByContractId(contract.getId()).forEach(invoice -> invoices.put(invoice.getId(), invoice));
        }
        Map<UUID, Room> rooms = roomRepository.findAllById(contracts.stream().map(Contract::getRoomId).distinct().toList())
            .stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Property> properties = propertyRepository
            .findAllById(contracts.stream().map(Contract::getPropertyId).distinct().toList())
            .stream().collect(Collectors.toMap(Property::id, Function.identity(), (a, b) -> a));

        List<Row> rows = new ArrayList<>();
        // SePay: chỉ các khoản đã gắn được vào hóa đơn của người thuê; khoản chưa khớp để chủ trọ xử lý ở trang đối soát.
        paymentRepository.findByContractIds(byId.keySet()).stream()
            .filter(tx -> tx.status() == PaymentStatus.SUCCESS && "in".equalsIgnoreCase(tx.transferType())
                && tx.invoiceId() != null && invoices.containsKey(tx.invoiceId()))
            .forEach(tx -> rows.add(gatewayRow(tx, byId, invoices, rooms, properties)));
        for (Contract contract : contracts) {
            claimRepository.findByContractId(contract.getId())
                .forEach(claim -> rows.add(claimRow(claim, byId, invoices, rooms, properties)));
        }
        paymentRecordRepository.findByContractIds(byId.keySet())
            .forEach(record -> rows.add(offsetRow(record, byId, invoices, rooms, properties)));

        List<Row> filtered = rows.stream()
            .filter(row -> criteria.status() == null || row.status() == criteria.status())
            .filter(row -> withinDates(row.at(), criteria.from(), criteria.to()))
            .sorted(Comparator.comparing(Row::at).reversed())
            .toList();
        BigDecimal totalRecorded = filtered.stream().filter(row -> row.status() == Status.SUCCESS)
            .map(Row::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        long total = filtered.size();
        int totalPages = (int) Math.max(1, (total + criteria.size() - 1) / criteria.size());
        int page = Math.min(criteria.page(), totalPages);
        int from = (page - 1) * criteria.size();
        List<Row> pageRows = filtered.subList(Math.min(from, filtered.size()),
            Math.min(from + criteria.size(), filtered.size()));

        List<ContractOption> options = visibleContracts(user, tenantId).stream()
            .map(contract -> new ContractOption(contract.getId(), label(contract, rooms, properties),
                contract.getStatus() == ContractStatus.EXPIRED || contract.getStatus() == ContractStatus.TERMINATED
                    || contract.getStatus() == ContractStatus.CANCELLED))
            .toList();
        return new PaymentHistory(List.copyOf(pageRows), page, totalPages, total, totalRecorded, options);
    }

    /**
     * Hợp đồng mà người xem được xem lịch sử: người thuê chính xem của mình; chủ trọ và nhân viên có quyền thu tiền
     * xem của người thuê trong nhà trọ họ thu tiền. Quản trị viên không xem số liệu tiền (#163).
     */
    private List<Contract> visibleContracts(CurrentUser user, UUID tenantId) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        switch (user.role()) {
            case TENANT -> {
                Tenant own = tenantRepository.findByUserId(user.id()).orElseThrow(() -> new UnauthorizedPropertyAccessException(
                    "Tài khoản này chưa gắn với hồ sơ người thuê nào."));
                if (tenantId != null && !tenantId.equals(own.id())) {
                    throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được lịch sử thanh toán của chính mình.");
                }
                return contractRepository.findByPrimaryTenantId(own.id());
            }
            case OWNER, STAFF -> {
                if (tenantId == null) {
                    throw new IllegalArgumentException("Cần chọn người thuê để xem lịch sử thanh toán.");
                }
                List<Contract> all = contractRepository.findByPrimaryTenantId(tenantId);
                List<Contract> allowed = all.stream().filter(contract -> canCollect(user, contract.getPropertyId())).toList();
                if (!all.isEmpty() && allowed.isEmpty()) {
                    throw new UnauthorizedPropertyAccessException(
                        "Bạn chưa được cấp quyền thu tiền ở nhà trọ của người thuê này.");
                }
                return allowed;
            }
            case ADMIN -> {
                authorizationService.assertCanViewFinance(user);
                throw new UnauthorizedPropertyAccessException(AuthorizationService.ADMIN_FINANCE_DENIED);
            }
            default -> throw new UnauthorizedPropertyAccessException(
                "Chỉ người thuê, chủ nhà trọ hoặc nhân viên có quyền thu tiền được xem lịch sử thanh toán.");
        }
    }

    private boolean canCollect(CurrentUser user, UUID propertyId) {
        try {
            authorizationService.assertCanCollectPayment(user, propertyId);
            return true;
        } catch (UnauthorizedPropertyAccessException ex) {
            return false;
        }
    }

    private Row gatewayRow(PaymentTransaction tx, Map<UUID, Contract> contracts, Map<UUID, Invoice> invoices,
                           Map<UUID, Room> rooms, Map<UUID, Property> properties) {
        boolean mismatch = tx.reviewReason() == PaymentReviewReason.OVERPAID
            || tx.reviewReason() == PaymentReviewReason.INVOICE_NOT_PAYABLE;
        BigDecimal amount = tx.appliedAmount() != null ? tx.appliedAmount() : tx.transferAmount();
        return row(tx.id().toString(), tx.createdAt(), tx.contractId(), tx.invoiceId(), amount, Method.SEPAY,
            tx.referenceCode() != null ? tx.referenceCode() : tx.content(), mismatch ? Status.MISMATCH : Status.SUCCESS,
            null, contracts, invoices, rooms, properties);
    }

    private Row claimRow(PaymentClaim claim, Map<UUID, Contract> contracts, Map<UUID, Invoice> invoices,
                         Map<UUID, Room> rooms, Map<UUID, Property> properties) {
        Status status = switch (claim.status()) {
            case PENDING -> Status.PENDING;
            case CONFIRMED -> Status.SUCCESS;
            case REJECTED -> Status.REJECTED;
        };
        Method method = claim.source() == PaymentClaim.Source.MANAGER_RECORD ? Method.MANAGER_RECORD : Method.TENANT_CLAIM;
        String reference = (claim.method() == PaymentClaim.Method.CASH ? "Tiền mặt" : "Chuyển khoản")
            + (claim.note() == null ? "" : ": " + claim.note());
        Instant at = claim.decidedAt() != null && claim.status() != PaymentClaim.Status.REJECTED
            ? claim.decidedAt() : claim.createdAt();
        return row(claim.id().toString(), at, claim.contractId(), claim.invoiceId(), claim.amount(), method, reference,
            status, claim.rejectReason(), contracts, invoices, rooms, properties);
    }

    private Row offsetRow(InvoicePaymentRecord record, Map<UUID, Contract> contracts, Map<UUID, Invoice> invoices,
                          Map<UUID, Room> rooms, Map<UUID, Property> properties) {
        return row(record.id().toString(), record.paidAt(), record.contractId(), record.invoiceId(), record.amount(),
            Method.DEPOSIT_OFFSET, record.note() == null ? "Cấn trừ tiền cọc" : record.note(), Status.SUCCESS, null,
            contracts, invoices, rooms, properties);
    }

    private Row row(String id, Instant at, UUID contractId, UUID invoiceId, BigDecimal amount, Method method,
                    String reference, Status status, String rejectReason, Map<UUID, Contract> contracts,
                    Map<UUID, Invoice> invoices, Map<UUID, Room> rooms, Map<UUID, Property> properties) {
        Contract contract = contracts.get(contractId);
        Invoice invoice = invoices.get(invoiceId);
        Room room = contract == null ? null : rooms.get(contract.getRoomId());
        Property property = contract == null ? null : properties.get(contract.getPropertyId());
        return new Row(id, at, contractId, property == null ? null : property.name(),
            room == null ? null : room.getRoomNumber(), invoiceId, invoice == null ? null : invoice.getType(),
            invoice == null ? null : invoice.getMonth(), invoice == null ? null : invoice.getYear(), amount, method,
            reference, status, rejectReason, invoice != null && invoice.getPaidAmount().signum() > 0);
    }

    private static String label(Contract contract, Map<UUID, Room> rooms, Map<UUID, Property> properties) {
        Room room = rooms.get(contract.getRoomId());
        Property property = properties.get(contract.getPropertyId());
        return (property == null ? "Nhà trọ" : property.name()) + " · Phòng " + (room == null ? "?" : room.getRoomNumber())
            + " · " + contract.getStartDate() + " → " + contract.getEndDate();
    }

    private static boolean withinDates(Instant at, LocalDate from, LocalDate to) {
        LocalDate day = at.atZone(ZONE).toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }
}
