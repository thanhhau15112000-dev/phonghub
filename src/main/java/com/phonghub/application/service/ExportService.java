package com.phonghub.application.service;

import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TableExportPort;
import com.phonghub.application.port.out.TableExportPort.Table;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExportService implements ExportUseCase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int DEFAULT_MONTHS = 12;

    private final PropertyRepositoryPort propertyRepository;
    private final ContractRepositoryPort contractRepository;
    private final InvoiceRepositoryPort invoiceRepository;
    private final RoomRepositoryPort roomRepository;
    private final TenantRepositoryPort tenantRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final TableExportPort xlsxExporter;
    private final TableExportPort pdfExporter;
    private final Clock clock;

    public ExportService(
        PropertyRepositoryPort propertyRepository,
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        StaffPropertyAssignmentPort assignmentPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        TableExportPort xlsxExporter,
        TableExportPort pdfExporter,
        Clock clock
    ) {
        this.propertyRepository = propertyRepository;
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.assignmentPort = assignmentPort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.xlsxExporter = xlsxExporter;
        this.pdfExporter = pdfExporter;
        this.clock = clock;
    }

    @Override
    public ExportFile export(ExportRequest request) {
        CurrentUser user = currentUserPort.getCurrentUser();
        List<Property> scope = scope(user, request);
        Data data = load(scope);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));

        Table table;
        String name;
        switch (request.dataset()) {
            case INVOICES -> {
                YearMonth to = request.to() != null ? request.to() : YearMonth.from(today);
                YearMonth from = request.from() != null ? request.from() : to.minusMonths(DEFAULT_MONTHS - 1);
                if (from.isAfter(to)) {
                    throw new IllegalArgumentException("Tháng bắt đầu không được sau tháng kết thúc.");
                }
                table = invoices(data, from, to, today, subtitle(scope, today));
                name = "hoa-don_" + from + "_" + to;
            }
            case DEBTS -> {
                table = debts(data, today, subtitle(scope, today));
                name = "cong-no_" + today;
            }
            default -> {
                table = contracts(data, subtitle(scope, today));
                name = "hop-dong_" + today;
            }
        }

        TableExportPort exporter = request.format() == Format.PDF ? pdfExporter : xlsxExporter;
        byte[] content = exporter.render(table);
        if (auditPort != null) {
            auditPort.recordEvent(AuditPort.AuditEvent.of("DATA_EXPORTED", user.id(), "EXPORT", request.dataset().name(),
                Map.of("format", request.format().name(), "properties", scope.size(), "rows", table.rows().size())));
        }
        return new ExportFile(name + "." + exporter.extension(), exporter.contentType(), content);
    }

    /**
     * Phạm vi nhà trọ được xuất: hóa đơn/công nợ cần quyền thu tiền, hợp đồng cần quyền quản lý hợp đồng (owner luôn có
     * ở nhà của mình). Admin chỉ xuất hợp đồng vì không xem số liệu tiền (#163). Kỹ thuật viên và người thuê không được
     * xuất.
     */
    private List<Property> scope(CurrentUser user, ExportRequest request) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        boolean contracts = request.dataset() == Dataset.CONTRACTS;
        if (!contracts) {
            authorizationService.assertCanViewFinance(user);
        }
        if (request.propertyId() != null) {
            if (contracts) {
                authorizationService.assertCanManageContracts(user, request.propertyId());
            } else {
                authorizationService.assertCanCollectPayment(user, request.propertyId());
            }
            return List.of(propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + request.propertyId())));
        }
        return switch (user.role()) {
            case ADMIN -> propertyRepository.findAll();
            case OWNER -> propertyRepository.findByOwnerId(user.id());
            case STAFF -> {
                Predicate<StaffPropertyAssignment> allowed = contracts
                    ? StaffPropertyAssignment::canManageContracts
                    : StaffPropertyAssignment::canCollectPayment;
                yield propertyRepository.findAllById(assignmentPort.findByUserId(user.id()).stream()
                    .filter(allowed).map(StaffPropertyAssignment::propertyId).toList());
            }
            default -> throw new UnauthorizedPropertyAccessException(
                "Chỉ quản trị viên, chủ nhà trọ hoặc nhân viên được phân quyền mới xuất được dữ liệu.");
        };
    }

    private record Data(Map<UUID, Property> properties, List<Contract> contracts, Map<UUID, Room> rooms,
                        Map<UUID, Tenant> tenants) {

        String propertyName(Contract c) {
            Property p = properties.get(c.getPropertyId());
            return p == null ? "" : p.name();
        }

        String roomNumber(Contract c) {
            Room r = rooms.get(c.getRoomId());
            return r == null ? "" : r.getRoomNumber();
        }

        Tenant tenant(Contract c) {
            return tenants.get(c.getPrimaryTenantId());
        }

        Comparator<Contract> order() {
            return Comparator.comparing((Contract c) -> propertyName(c), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> roomNumber(c), String.CASE_INSENSITIVE_ORDER);
        }
    }

    private Data load(List<Property> scope) {
        List<Contract> contracts = scope.stream()
            .flatMap(p -> contractRepository.findByPropertyId(p.id()).stream())
            .toList();
        Map<UUID, Room> rooms = roomRepository.findAllById(contracts.stream().map(Contract::getRoomId).distinct().toList())
            .stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<UUID, Tenant> tenants = tenantRepository.findAllByIds(contracts.stream().map(Contract::getPrimaryTenantId)
                .distinct().toList())
            .stream().collect(Collectors.toMap(Tenant::id, Function.identity()));
        return new Data(scope.stream().collect(Collectors.toMap(Property::id, Function.identity())), contracts, rooms,
            tenants);
    }

    private Table invoices(Data data, YearMonth from, YearMonth to, LocalDate today, String subtitle) {
        record Line(Contract contract, Invoice invoice) {}
        List<Line> lines = new ArrayList<>();
        for (Contract contract : data.contracts()) {
            for (Invoice invoice : invoiceRepository.findByContractId(contract.getId())) {
                YearMonth period = YearMonth.of(invoice.getYear(), invoice.getMonth());
                // Khoản phí đã gộp nằm trong hóa đơn tháng: xuất riêng sẽ bị tính trùng.
                if (!invoice.isConsolidated() && !period.isBefore(from) && !period.isAfter(to)) {
                    lines.add(new Line(contract, invoice));
                }
            }
        }
        lines.sort(Comparator.comparing((Line l) -> YearMonth.of(l.invoice().getYear(), l.invoice().getMonth()))
            .thenComparing(Line::contract, data.order()));

        List<List<Object>> rows = new ArrayList<>();
        for (Line line : lines) {
            Invoice i = line.invoice();
            Tenant tenant = data.tenant(line.contract());
            rows.add(row(data.propertyName(line.contract()), data.roomNumber(line.contract()),
                tenant == null ? "" : tenant.fullName(), String.format("%02d/%d", i.getMonth(), i.getYear()),
                i.getType() == InvoiceType.MAINTENANCE ? "Phí sửa chữa" : "Tiền thuê", i.getTotalAmount(),
                i.getPaidAmount(), i.remainingAmount(), i.getDueDate(), invoiceStatus(i, today), i.getPaymentCode()));
        }
        return new Table("Hóa đơn " + format(from) + " - " + format(to), subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "Kỳ", "Loại", "Tổng tiền", "Đã trả", "Còn lại", "Hạn thanh toán",
                "Trạng thái", "Mã thanh toán"),
            rows);
    }

    private Table debts(Data data, LocalDate today, String subtitle) {
        record Debt(Contract contract, long invoices, BigDecimal remaining, BigDecimal overdue, LocalDate oldestDue) {}
        List<Debt> debts = new ArrayList<>();
        for (Contract contract : data.contracts()) {
            List<Invoice> unpaid = invoiceRepository.findByContractId(contract.getId()).stream()
                .filter(Invoice::isPayable)
                .filter(i -> i.remainingAmount().signum() > 0)
                .toList();
            if (unpaid.isEmpty()) {
                continue;
            }
            debts.add(new Debt(contract, unpaid.size(),
                unpaid.stream().map(Invoice::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                unpaid.stream().filter(i -> i.isOverdue(today)).map(Invoice::remainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                unpaid.stream().map(Invoice::getDueDate).filter(Objects::nonNull).min(Comparator.naturalOrder())
                    .orElse(null)));
        }
        debts.sort(Comparator.comparing(Debt::remaining).reversed().thenComparing(Debt::contract, data.order()));

        List<List<Object>> rows = new ArrayList<>();
        for (Debt debt : debts) {
            Tenant tenant = data.tenant(debt.contract());
            rows.add(row(data.propertyName(debt.contract()), data.roomNumber(debt.contract()),
                tenant == null ? "" : tenant.fullName(), tenant == null ? "" : tenant.phone(), debt.invoices(),
                debt.remaining(), debt.overdue(), debt.oldestDue()));
        }
        return new Table("Công nợ đến ngày " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "Điện thoại", "Số hóa đơn nợ", "Tổng còn nợ", "Đã quá hạn",
                "Hạn sớm nhất"),
            rows);
    }

    private Table contracts(Data data, String subtitle) {
        List<List<Object>> rows = new ArrayList<>();
        data.contracts().stream()
            .sorted(data.order().thenComparing(Contract::getStartDate))
            .forEach(c -> {
                Tenant tenant = data.tenant(c);
                rows.add(row(data.propertyName(c), data.roomNumber(c), tenant == null ? "" : tenant.fullName(),
                    // Không bao giờ xuất số CCCD đầy đủ.
                    tenant == null ? "" : tenant.maskedIdentityCardNumber(), tenant == null ? "" : tenant.phone(),
                    c.getStartDate(), c.getEndDate(), c.getRentAmount(), c.getDepositAmount(), c.getPaymentDay(),
                    contractStatus(c.getStatus())));
            });
        return new Table("Danh sách hợp đồng", subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "CCCD (đã che)", "Điện thoại", "Bắt đầu", "Kết thúc", "Tiền thuê",
                "Tiền cọc", "Ngày thanh toán", "Trạng thái"),
            rows);
    }

    private String subtitle(List<Property> scope, LocalDate today) {
        String where = scope.size() == 1 ? scope.get(0).name() : scope.size() + " nhà trọ trong phạm vi";
        return where + " · Xuất lúc " + LocalDateTime.now(clock.withZone(ZONE)).format(STAMP);
    }

    private static List<Object> row(Object... values) {
        List<Object> row = new ArrayList<>(values.length);
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    private static String format(YearMonth month) {
        return String.format("%02d/%d", month.getMonthValue(), month.getYear());
    }

    private static String invoiceStatus(Invoice invoice, LocalDate today) {
        if (invoice.isOverdue(today)) {
            return "Quá hạn";
        }
        return switch (invoice.getStatus()) {
            case DRAFT -> "Nháp";
            case ISSUED -> "Chưa thanh toán";
            case PARTIALLY_PAID -> "Thanh toán một phần";
            case PAID -> "Đã thanh toán";
            case OVERDUE -> "Quá hạn";
            case VOIDED -> "Đã hủy";
        };
    }

    private static String contractStatus(ContractStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case ACTIVE -> "Đang hiệu lực";
            case EXPIRED -> "Hết hạn";
            case TERMINATED -> "Đã kết thúc";
            case CANCELLED -> "Đã hủy";
        };
    }
}
