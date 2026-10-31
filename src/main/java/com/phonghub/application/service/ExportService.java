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
import com.phonghub.domain.exception.ExportLimitExceededException;
import com.phonghub.domain.exception.NoExportDataException;
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
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
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
        return export(request, ExportFilter.none(), null);
    }

    @Override
    public ExportFile export(ExportRequest request, ExportFilter filter, Set<String> selectedIds) {
        CurrentUser user = currentUserPort.getCurrentUser();
        ExportFilter f = filter == null ? ExportFilter.none() : filter;
        List<Property> scope = narrow(scope(user, request), f);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Data data = load(scope);

        Built built;
        if (selectedIds == null || selectedIds.isEmpty()) {
            built = build(data, request, f, today, scope);
        } else {
            // Mọi id phải nằm trong tập dữ liệu thuộc phạm vi của người xuất, tính lại ở server; không tin trình duyệt.
            Built universe = build(data, request, ExportFilter.none(), today, scope);
            if (!new LinkedHashSet<>(universe.ids()).containsAll(selectedIds)) {
                throw new UnauthorizedPropertyAccessException("Có dòng được chọn không thuộc phạm vi bạn được xuất.");
            }
            built = universe.only(selectedIds);
        }

        int rows = built.table().rows().size();
        if (rows == 0) {
            throw new NoExportDataException();
        }
        if (rows > MAX_ROWS) {
            throw new ExportLimitExceededException(MAX_ROWS);
        }
        TableExportPort exporter = request.format() == Format.PDF ? pdfExporter : xlsxExporter;
        byte[] content = exporter.render(built.table());
        if (auditPort != null) {
            audit(user, request, scope, rows);
        }
        return new ExportFile(built.name() + "_" + rows + "-dong." + exporter.extension(), exporter.contentType(),
            content);
    }

    @Override
    public Preview preview(ExportRequest request, ExportFilter filter, int page, int size) {
        CurrentUser user = currentUserPort.getCurrentUser();
        ExportFilter f = filter == null ? ExportFilter.none() : filter;
        List<Property> scope = narrow(scope(user, request), f);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Built built = build(load(scope), request, f, today, scope);
        int pageSize = Math.max(1, Math.min(size, 200));
        int total = built.ids().size();
        int from = (int) Math.min((long) Math.max(page, 0) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<PreviewRow> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            rows.add(new PreviewRow(built.ids().get(i), built.table().rows().get(i).stream().map(this::cell).toList()));
        }
        return new Preview(built.table().headers(), rows, total, Math.max(page, 0), pageSize);
    }

    /** Bảng đã dựng cùng id từng dòng (hóa đơn: id hóa đơn; công nợ và hợp đồng: id hợp đồng). */
    private record Built(Table table, List<String> ids, String name) {
        Built only(Set<String> selected) {
            List<List<Object>> rows = new ArrayList<>();
            List<String> keep = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                if (selected.contains(ids.get(i))) {
                    rows.add(table.rows().get(i));
                    keep.add(ids.get(i));
                }
            }
            return new Built(new Table(table.title(), table.subtitle(), table.headers(), rows), keep, name);
        }
    }

    private Built build(Data data, ExportRequest request, ExportFilter f, LocalDate today, List<Property> scope) {
        String subtitle = subtitle(scope, today);
        switch (request.dataset()) {
            case INVOICES -> {
                YearMonth to = request.to() != null ? request.to() : YearMonth.from(today);
                YearMonth from = request.from() != null ? request.from() : to.minusMonths(DEFAULT_MONTHS - 1);
                if (from.isAfter(to)) {
                    throw new IllegalArgumentException("Tháng bắt đầu không được sau tháng kết thúc.");
                }
                return invoices(data, f, from, to, today, subtitle, "hoa-don_" + from + "_" + to);
            }
            case DEBTS -> {
                return debts(data, f, today, subtitle, "cong-no_" + today);
            }
            default -> {
                return contracts(data, f, subtitle, "hop-dong_" + today);
            }
        }
    }

    /** Thu hẹp phạm vi theo các nhà trọ được chọn; có nhà ngoài phạm vi thì từ chối cả yêu cầu. */
    private static List<Property> narrow(List<Property> scope, ExportFilter f) {
        if (f.propertyIds() == null || f.propertyIds().isEmpty()) {
            return scope;
        }
        Set<UUID> allowed = scope.stream().map(Property::id).collect(Collectors.toSet());
        if (!allowed.containsAll(f.propertyIds())) {
            throw new UnauthorizedPropertyAccessException("Có nhà trọ được chọn nằm ngoài phạm vi bạn được xuất.");
        }
        return scope.stream().filter(p -> f.propertyIds().contains(p.id())).toList();
    }

    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (value instanceof BigDecimal b) {
            return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(b);
        }
        return String.valueOf(value);
    }

    private static boolean contains(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        return fold(haystack).contains(fold(needle));
    }

    private static String fold(String value) {
        String n = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean inRange(LocalDate value, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        return value != null && (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    private static boolean contractMatches(Data data, Contract c, ExportFilter f) {
        Tenant tenant = data.tenant(c);
        return contains(data.roomNumber(c), f.roomNumber())
            && contains(tenant == null ? "" : tenant.fullName(), f.tenantName());
    }

    private static InvoiceState state(Invoice invoice, LocalDate today) {
        if (invoice.isOverdue(today)) {
            return InvoiceState.OVERDUE;
        }
        return switch (invoice.getStatus()) {
            case DRAFT -> InvoiceState.DRAFT;
            case ISSUED -> InvoiceState.UNPAID;
            case PARTIALLY_PAID -> InvoiceState.PARTIAL;
            case PAID -> InvoiceState.PAID;
            case OVERDUE -> InvoiceState.OVERDUE;
            case VOIDED -> InvoiceState.VOIDED;
        };
    }

    /**
     * Phạm vi nhà trọ được xuất: hóa đơn/công nợ cần quyền thu tiền, hợp đồng cần quyền quản lý hợp đồng (owner luôn có
     * ở nhà của mình). Admin chỉ xuất hợp đồng vì không xem số liệu tiền (#163). Kỹ thuật viên và người thuê không được
     * xuất.
     */
    private List<Property> scope(CurrentUser user, ExportRequest request) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
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

    /**
     * Mỗi nhà trọ có trong file một sự kiện, để chủ nhà thấy ai đã xuất dữ liệu nhà mình trong Nhật ký (#78). File gồm
     * nhiều nhà (admin, nhân sự nhiều nhà) thì không ghi số nhà/số dòng, tránh lộ quy mô nhà khác cho từng chủ; các
     * sự kiện cùng lần xuất có chung {@code exportId}.
     */
    private void audit(CurrentUser user, ExportRequest request, List<Property> scope, int rows) {
        String dataset = request.dataset().name();
        String format = request.format().name();
        if (scope.size() <= 1) {
            UUID propertyId = scope.isEmpty() ? null : scope.getFirst().id();
            auditPort.recordEvent(AuditPort.AuditEvent.of("DATA_EXPORTED", user.id(), "EXPORT", dataset, propertyId,
                Map.of("format", format, "properties", scope.size(), "rows", rows)));
            return;
        }
        String exportId = UUID.randomUUID().toString();
        for (Property property : scope) {
            auditPort.recordEvent(AuditPort.AuditEvent.of("DATA_EXPORTED", user.id(), "EXPORT", dataset, property.id(),
                Map.of("format", format, "exportId", exportId)));
        }
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

    private Built invoices(Data data, ExportFilter f, YearMonth from, YearMonth to, LocalDate today,
                           String subtitle, String name) {
        record Line(Contract contract, Invoice invoice) {}
        List<Line> lines = new ArrayList<>();
        for (Contract contract : data.contracts()) {
            if (!contractMatches(data, contract, f)) {
                continue;
            }
            for (Invoice invoice : invoiceRepository.findByContractId(contract.getId())) {
                YearMonth period = YearMonth.of(invoice.getYear(), invoice.getMonth());
                // Khoản phí đã gộp nằm trong hóa đơn tháng: xuất riêng sẽ bị tính trùng.
                if (!invoice.isConsolidated() && !period.isBefore(from) && !period.isAfter(to)
                    && (f.states() == null || f.states().isEmpty() || f.states().contains(state(invoice, today)))
                    && (f.types() == null || f.types().isEmpty() || f.types().contains(invoice.getType()))
                    && inRange(invoice.getDueDate(), f.dueFrom(), f.dueTo())
                    && (!f.onlyOutstanding() || invoice.remainingAmount().signum() > 0)) {
                    lines.add(new Line(contract, invoice));
                }
            }
        }
        lines.sort(Comparator.comparing((Line l) -> YearMonth.of(l.invoice().getYear(), l.invoice().getMonth()))
            .thenComparing(Line::contract, data.order()));

        List<List<Object>> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Line line : lines) {
            Invoice i = line.invoice();
            ids.add(i.getId().toString());
            Tenant tenant = data.tenant(line.contract());
            rows.add(row(data.propertyName(line.contract()), data.roomNumber(line.contract()),
                tenant == null ? "" : tenant.fullName(), String.format("%02d/%d", i.getMonth(), i.getYear()),
                switch (i.getType()) {
                    case MAINTENANCE -> "Phí sửa chữa";
                    case CHECK_OUT -> "Chốt điện/nước";
                    case CANCELLATION -> "Phí hủy hợp đồng";
                    case RENT -> "Tiền thuê";
                }, i.getTotalAmount(),
                i.getPaidAmount(), i.remainingAmount(), i.getDueDate(), invoiceStatus(i, today), i.getPaymentCode()));
        }
        return new Built(new Table("Hóa đơn " + format(from) + " - " + format(to), subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "Kỳ", "Loại", "Tổng tiền", "Đã trả", "Còn lại", "Hạn thanh toán",
                "Trạng thái", "Mã thanh toán"),
            rows), ids, name);
    }

    private Built debts(Data data, ExportFilter f, LocalDate today, String subtitle, String name) {
        record Debt(Contract contract, long invoices, BigDecimal remaining, BigDecimal overdue, LocalDate oldestDue) {}
        List<Debt> debts = new ArrayList<>();
        for (Contract contract : data.contracts()) {
            if (!contractMatches(data, contract, f)) {
                continue;
            }
            List<Invoice> unpaid = invoiceRepository.findByContractId(contract.getId()).stream()
                .filter(Invoice::isPayable)
                .filter(i -> i.remainingAmount().signum() > 0)
                .filter(i -> f.states() == null || f.states().isEmpty() || f.states().contains(state(i, today)))
                .filter(i -> f.types() == null || f.types().isEmpty() || f.types().contains(i.getType()))
                .filter(i -> inRange(i.getDueDate(), f.dueFrom(), f.dueTo()))
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
        List<String> ids = new ArrayList<>();
        for (Debt debt : debts) {
            ids.add(debt.contract().getId().toString());
            Tenant tenant = data.tenant(debt.contract());
            rows.add(row(data.propertyName(debt.contract()), data.roomNumber(debt.contract()),
                tenant == null ? "" : tenant.fullName(), tenant == null ? "" : tenant.phone(), debt.invoices(),
                debt.remaining(), debt.overdue(), debt.oldestDue()));
        }
        return new Built(new Table("Công nợ đến ngày " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "Điện thoại", "Số hóa đơn nợ", "Tổng còn nợ", "Đã quá hạn",
                "Hạn sớm nhất"),
            rows), ids, name);
    }

    private Built contracts(Data data, ExportFilter f, String subtitle, String name) {
        List<List<Object>> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        data.contracts().stream()
            .filter(c -> contractMatches(data, c, f))
            .filter(c -> f.contractStatuses() == null || f.contractStatuses().isEmpty()
                || f.contractStatuses().contains(c.getStatus()))
            .filter(c -> inRange(c.getStartDate(), f.startFrom(), f.startTo()))
            .filter(c -> inRange(c.getEndDate(), f.endFrom(), f.endTo()))
            .sorted(data.order().thenComparing(Contract::getStartDate))
            .forEach(c -> {
                ids.add(c.getId().toString());
                Tenant tenant = data.tenant(c);
                rows.add(row(data.propertyName(c), data.roomNumber(c), tenant == null ? "" : tenant.fullName(),
                    // Không bao giờ xuất số CCCD đầy đủ.
                    tenant == null ? "" : tenant.maskedIdentityCardNumber(), tenant == null ? "" : tenant.phone(),
                    c.getStartDate(), c.getEndDate(), c.getRentAmount(), c.getDepositAmount(), c.getPaymentDay(),
                    contractStatus(c.getStatus())));
            });
        return new Built(new Table("Danh sách hợp đồng", subtitle,
            List.of("Nhà trọ", "Phòng", "Người thuê", "CCCD (đã che)", "Điện thoại", "Bắt đầu", "Kết thúc", "Tiền thuê",
                "Tiền cọc", "Ngày thanh toán", "Trạng thái"),
            rows), ids, name);
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
