package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.ExportUseCase.Dataset;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.ExportRequest;
import com.phonghub.application.port.in.ExportUseCase.Format;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TableExportPort;
import com.phonghub.application.port.out.TableExportPort.Table;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ExportService;
import com.phonghub.domain.exception.NoExportDataException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #85: xuất dữ liệu đúng phạm vi quyền, đúng số liệu và không lộ CCCD đầy đủ. */
class ExportServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-15T03:00:00Z");

    private final UUID ownerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    private InMemoryStaffPropertyAssignmentRepository assignments;
    private InMemoryInvoiceRepository invoices;
    private InMemoryAuditAdapter audit;
    private CurrentUserPort currentUserPort;
    private CapturingExporter xlsx;
    private CapturingExporter pdf;
    private ExportService service;
    private Contract c1;

    /** Exporter giả: giữ lại bảng được dựng để kiểm tra nội dung. */
    private static final class CapturingExporter implements TableExportPort {
        private final String extension;
        private Table last;

        CapturingExporter(String extension) {
            this.extension = extension;
        }

        @Override
        public byte[] render(Table table) {
            this.last = table;
            return new byte[] {1, 2, 3};
        }

        @Override
        public String contentType() {
            return "test/" + extension;
        }

        @Override
        public String extension() {
            return extension;
        }
    }

    @BeforeEach
    void setUp() {
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        InMemoryRoomRepository rooms = new InMemoryRoomRepository();
        InMemoryContractRepository contracts = new InMemoryContractRepository();
        InMemoryTenantRepository tenants = new InMemoryTenantRepository();
        assignments = new InMemoryStaffPropertyAssignmentRepository();
        invoices = new InMemoryInvoiceRepository();
        audit = new InMemoryAuditAdapter();
        currentUserPort = mock(CurrentUserPort.class);
        xlsx = new CapturingExporter("xlsx");
        pdf = new CapturingExporter("pdf");
        service = new ExportService(properties, contracts, invoices, rooms, tenants, assignments, currentUserPort,
            new AuthorizationService(assignments, properties, tenants, contracts), audit, xlsx, pdf,
            Clock.fixed(NOW, ZoneOffset.UTC));

        properties.save(new Property(p1, "Nhà Một", "a", null, 2, ownerId, PropertyApprovalStatus.VERIFIED, null, NOW));
        properties.save(new Property(p2, "Nhà Hai", "b", null, 2, UUID.randomUUID(), PropertyApprovalStatus.VERIFIED, null, NOW));
        Room r1 = rooms.save(new Room(UUID.randomUUID(), p1, "A101", 1, null, new BigDecimal("3000000"), 2, RoomStatus.OCCUPIED, NOW, NOW));
        Room r2 = rooms.save(new Room(UUID.randomUUID(), p2, "B201", 2, null, new BigDecimal("3000000"), 2, RoomStatus.OCCUPIED, NOW, NOW));
        Tenant t1 = tenants.save(Tenant.create(null, "Nguyễn Thuê", "079201001234", "0901111111", null, null));
        Tenant t2 = tenants.save(Tenant.create(null, "Trần Khác", "079201005678", "0902222222", null, null));
        c1 = contracts.save(new Contract(UUID.randomUUID(), p1, r1.getId(), t1.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 5, ContractStatus.ACTIVE,
            List.of(), NOW, NOW));
        Contract c2 = contracts.save(new Contract(UUID.randomUUID(), p2, r2.getId(), t2.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 5, ContractStatus.ACTIVE,
            List.of(), NOW, NOW));

        invoices.save(invoice(c1, 8, "3000000", "3000000", InvoiceStatus.PAID, LocalDate.of(2026, 8, 5)));
        invoices.save(invoice(c1, 9, "3000000", "1000000", InvoiceStatus.PARTIALLY_PAID, LocalDate.of(2026, 9, 5)));
        Invoice october = invoices.save(invoice(c1, 10, "3000000", "0", InvoiceStatus.ISSUED, LocalDate.of(2026, 10, 20)));
        Invoice fee = new Invoice(UUID.randomUUID(), c1.getId(), c1.getRoomId(), c1.getPrimaryTenantId(), 10, 2026,
            new BigDecimal("200000"), new BigDecimal("200000"), BigDecimal.ZERO, LocalDate.of(2026, 10, 1),
            InvoiceStatus.ISSUED, null, InvoiceType.MAINTENANCE, UUID.randomUUID(), null, NOW, NOW);
        fee.consolidateInto(october.getId());
        invoices.save(fee);
        invoices.save(invoice(c1, 1, "3000000", "0", InvoiceStatus.ISSUED, LocalDate.of(2025, 1, 5)));
        invoices.save(invoice(c2, 9, "9999999", "0", InvoiceStatus.ISSUED, LocalDate.of(2026, 9, 5)));
    }

    private static Invoice invoice(Contract c, int month, String total, String paid, InvoiceStatus status, LocalDate due) {
        int year = due.getYear();
        return new Invoice(UUID.randomUUID(), c.getId(), c.getRoomId(), c.getPrimaryTenantId(), month, year,
            new BigDecimal(total), new BigDecimal(total), new BigDecimal(paid), due, status, "PH" + month + year,
            InvoiceType.RENT, null, null, NOW, NOW);
    }

    private void login(UUID id, UserRole role) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role + "@x", role.name(), role));
    }

    @Test
    @DisplayName("hóa đơn: trong khoảng kỳ, bỏ phí đã gộp, chỉ nhà của owner; trạng thái quá hạn; tên tệp theo kỳ")
    void invoices() {
        login(ownerId, UserRole.OWNER);

        ExportFile file = service.export(new ExportRequest(Dataset.INVOICES, Format.XLSX, null, null, null));

        assertEquals("hoa-don_2025-11_2026-10.xlsx", file.filename());
        assertEquals("test/xlsx", file.contentType());
        Table table = xlsx.last;
        assertEquals("Hóa đơn 11/2025 - 10/2026", table.title());
        assertEquals(3, table.rows().size(), "8, 9, 10/2026 của nhà mình; bỏ 01/2025 ngoài khoảng và phí đã gộp");
        assertEquals(List.of("Nhà Một", "A101", "Nguyễn Thuê", "09/2026", "Tiền thuê", new BigDecimal("3000000"),
            new BigDecimal("1000000"), new BigDecimal("2000000"), LocalDate.of(2026, 9, 5), "Quá hạn", "PH92026"),
            table.rows().get(1));
        assertEquals("Đã thanh toán", table.rows().get(0).get(9));
        assertEquals("Chưa thanh toán", table.rows().get(2).get(9));
        assertTrue(table.subtitle().startsWith("Nhà Một · Xuất lúc 15/10/2026 10:00"));
    }

    @Test
    @DisplayName("công nợ: gom theo hợp đồng, tổng còn nợ, phần quá hạn, hạn sớm nhất")
    void debts() {
        login(ownerId, UserRole.OWNER);

        ExportFile file = service.export(new ExportRequest(Dataset.DEBTS, Format.PDF, null, null, null));

        assertEquals("cong-no_2026-10-15.pdf", file.filename());
        Table table = pdf.last;
        assertEquals(1, table.rows().size());
        List<Object> row = table.rows().get(0);
        assertEquals("Nguyễn Thuê", row.get(2));
        assertEquals("0901111111", row.get(3));
        assertEquals(3L, row.get(4), "kỳ 9, 10/2026 và 01/2025; khoản phí đã gộp vào hóa đơn tháng không tính riêng");
        assertEquals(new BigDecimal("8000000"), row.get(5));
        assertEquals(new BigDecimal("5000000"), row.get(6), "quá hạn: 01/2025 (3tr) + 09/2026 (2tr)");
        assertEquals(LocalDate.of(2025, 1, 5), row.get(7));
    }

    @Test
    @DisplayName("hợp đồng: CCCD luôn bị che, không bao giờ xuất số đầy đủ")
    void contractsMaskIdentity() {
        login(UUID.randomUUID(), UserRole.ADMIN);

        service.export(new ExportRequest(Dataset.CONTRACTS, Format.XLSX, null, null, null));

        Table table = xlsx.last;
        assertEquals(2, table.rows().size(), "admin xuất mọi nhà");
        assertEquals("CCCD (đã che)", table.headers().get(3));
        for (List<Object> row : table.rows()) {
            String masked = (String) row.get(3);
            assertFalse(masked.contains("079201"), masked);
            assertTrue(masked.endsWith("1234") || masked.endsWith("5678"), masked);
        }
        assertFalse(table.rows().toString().contains("079201001234"));
    }

    @Test
    @DisplayName("#163: admin không xuất hóa đơn, công nợ (toàn bộ hay một nhà) vì không xem số liệu tiền")
    void adminCannotExportFinance() {
        login(UUID.randomUUID(), UserRole.ADMIN);
        for (Dataset dataset : List.of(Dataset.INVOICES, Dataset.DEBTS)) {
            UnauthorizedPropertyAccessException ex = assertThrows(UnauthorizedPropertyAccessException.class,
                () -> service.export(new ExportRequest(dataset, Format.XLSX, null, null, null)));
            assertEquals(AuthorizationService.ADMIN_FINANCE_DENIED, ex.getMessage());
            assertThrows(UnauthorizedPropertyAccessException.class,
                () -> service.export(new ExportRequest(dataset, Format.PDF, p1, null, null)));
        }
        assertTrue(audit.getRecordedEvents().isEmpty(), "bị từ chối thì không ghi nhận xuất dữ liệu");
    }

    @Test
    @DisplayName("#227: không có dòng nào thì không tạo tệp và không ghi nhật ký xuất, với cả ba tập dữ liệu và hai định dạng")
    void emptyResultIsRejected() {
        login(ownerId, UserRole.OWNER);
        YearMonth empty = YearMonth.of(2020, 1);
        for (Format format : Format.values()) {
            assertThrows(NoExportDataException.class,
                () -> service.export(new ExportRequest(Dataset.INVOICES, format, null, empty, empty)));
        }
        invoices.clear();
        assertThrows(NoExportDataException.class,
            () -> service.export(new ExportRequest(Dataset.DEBTS, Format.XLSX, null, null, null)));
        assertTrue(audit.getRecordedEvents().isEmpty(), "xuất rỗng thì không ghi DATA_EXPORTED");
        assertEquals(null, xlsx.last, "không gọi bộ xuất tệp");
    }

    @Test
    @DisplayName("staff: hóa đơn cần quyền thu tiền, hợp đồng cần quyền quản lý hợp đồng")
    void staffPermissions() {
        login(staffId, UserRole.STAFF);
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffId, p1, false, true, NOW));

        assertThrows(NoExportDataException.class,
            () -> service.export(new ExportRequest(Dataset.INVOICES, Format.XLSX, null, null, null)),
            "chưa có quyền thu tiền thì không có hóa đơn nào để xuất");
        service.export(new ExportRequest(Dataset.CONTRACTS, Format.XLSX, null, null, null));
        assertEquals(1, xlsx.last.rows().size());
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.export(new ExportRequest(Dataset.DEBTS, Format.XLSX, p1, null, null)));
        service.export(new ExportRequest(Dataset.CONTRACTS, Format.PDF, p1, null, null));
        assertEquals(1, pdf.last.rows().size());
    }

    @Test
    @DisplayName("kỹ thuật viên, người thuê, chưa đăng nhập, owner lọc nhà người khác bị 403; nhà không tồn tại 404")
    void forbidden() {
        for (UserRole role : List.of(UserRole.TECHNICIAN, UserRole.TENANT)) {
            login(UUID.randomUUID(), role);
            for (Dataset dataset : Dataset.values()) {
                assertThrows(UnauthorizedPropertyAccessException.class,
                    () -> service.export(new ExportRequest(dataset, Format.XLSX, null, null, null)));
            }
        }
        login(ownerId, UserRole.OWNER);
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.export(new ExportRequest(Dataset.CONTRACTS, Format.XLSX, p2, null, null)));
        login(UUID.randomUUID(), UserRole.ADMIN);
        assertThrows(PropertyNotFoundException.class,
            () -> service.export(new ExportRequest(Dataset.CONTRACTS, Format.XLSX, UUID.randomUUID(), null, null)));
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.export(new ExportRequest(Dataset.INVOICES, Format.XLSX, null, null, null)));
        assertTrue(audit.getRecordedEvents().isEmpty(), "bị từ chối thì không ghi nhận xuất dữ liệu");
    }

    @Test
    @DisplayName("ghi audit mỗi lần xuất; khoảng kỳ sai bị chặn; định dạng mặc định là Excel")
    void auditAndValidation() {
        login(ownerId, UserRole.OWNER);
        service.export(new ExportRequest(Dataset.INVOICES, null, p1, YearMonth.of(2026, 9), YearMonth.of(2026, 9)));

        assertEquals(1, xlsx.last.rows().size());
        assertEquals(1, audit.getRecordedEvents().size());
        assertEquals("DATA_EXPORTED", audit.getRecordedEvents().get(0).action());
        assertEquals("INVOICES", audit.getRecordedEvents().get(0).targetId());
        assertThrows(IllegalArgumentException.class,
            () -> new ExportRequest(Dataset.INVOICES, Format.XLSX, null, YearMonth.of(2026, 9), YearMonth.of(2026, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new ExportRequest(Dataset.INVOICES, Format.XLSX, null, YearMonth.of(2010, 1), YearMonth.of(2026, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> service.export(new ExportRequest(Dataset.INVOICES, Format.XLSX, null, YearMonth.of(2030, 1), null)));
        assertThrows(IllegalArgumentException.class, () -> Dataset.parse("nope"));
        assertEquals(Format.PDF, Format.parse(" PDF "));
    }

    @Test
    @DisplayName("#78: sự kiện xuất gắn nhà trọ để chủ nhà thấy; file nhiều nhà ghi mỗi nhà một sự kiện, không lộ số dòng")
    void auditScopedToProperties() {
        login(ownerId, UserRole.OWNER);
        service.export(new ExportRequest(Dataset.CONTRACTS, Format.XLSX, null, null, null));

        assertEquals(1, audit.getRecordedEvents().size());
        assertEquals(p1, audit.getRecordedEvents().get(0).propertyId());
        assertEquals(1, audit.getRecordedEvents().get(0).details().get("rows"));

        audit.clear();
        login(UUID.randomUUID(), UserRole.ADMIN);
        service.export(new ExportRequest(Dataset.CONTRACTS, Format.PDF, null, null, null));

        var events = audit.getRecordedEvents();
        assertEquals(2, events.size());
        assertEquals(Set.of(p1, p2), Set.of(events.get(0).propertyId(), events.get(1).propertyId()));
        assertEquals(events.get(0).details().get("exportId"), events.get(1).details().get("exportId"));
        assertFalse(events.get(0).details().containsKey("rows"), "chủ nhà không thấy tổng số dòng của nhà khác");
    }
}
