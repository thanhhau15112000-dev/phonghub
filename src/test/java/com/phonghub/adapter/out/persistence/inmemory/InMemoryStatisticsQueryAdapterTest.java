package com.phonghub.adapter.out.persistence.inmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.StatisticsQueryPort.InvoiceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.MaintenanceAggregate;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #93: bản in-memory của truy vấn thống kê theo đúng quy ước của bản PostgreSQL. */
class InMemoryStatisticsQueryAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private InMemoryInvoiceRepository invoices;
    private InMemoryContractRepository contracts;
    private InMemoryRoomRepository rooms;
    private InMemoryPropertyRepository properties;
    private InMemoryMaintenanceTicketRepository tickets;
    private InMemoryUserRepository users;
    private InMemoryAuditAdapter audit;
    private InMemoryStatisticsQueryAdapter adapter;

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private Contract c1;
    private Contract c2;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        contracts = new InMemoryContractRepository();
        rooms = new InMemoryRoomRepository();
        properties = new InMemoryPropertyRepository();
        tickets = new InMemoryMaintenanceTicketRepository();
        users = new InMemoryUserRepository();
        audit = new InMemoryAuditAdapter();
        adapter = new InMemoryStatisticsQueryAdapter(invoices, contracts, rooms, properties, tickets, users, audit);

        properties.save(new Property(p1, "P1", "a", null, 3, null, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        properties.save(new Property(p2, "P2", "b", null, 3, null, PropertyApprovalStatus.PENDING, null,
            Instant.parse("2026-09-01T00:00:00Z")));
        Room r1 = rooms.save(room(p1, "101", RoomStatus.OCCUPIED));
        rooms.save(room(p1, "102", RoomStatus.MAINTENANCE));
        Room r3 = rooms.save(room(p2, "201", RoomStatus.OCCUPIED));
        c1 = contracts.save(contract(p1, r1.getId()));
        c2 = contracts.save(contract(p2, r3.getId()));
    }

    private static Room room(UUID propertyId, String number, RoomStatus status) {
        return new Room(UUID.randomUUID(), propertyId, number, 1, null, new BigDecimal("3000000"), 2, status,
            Instant.now(), Instant.now());
    }

    private static Contract contract(UUID propertyId, UUID roomId) {
        return new Contract(UUID.randomUUID(), propertyId, roomId, UUID.randomUUID(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 5, ContractStatus.ACTIVE,
            List.of(), Instant.now(), Instant.now());
    }

    private Invoice invoice(Contract contract, YearMonth period, String total, String paid, InvoiceStatus status,
                            LocalDate due, InvoiceType type) {
        Invoice invoice = new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(),
            contract.getPrimaryTenantId(), period.getMonthValue(), period.getYear(), new BigDecimal(total),
            new BigDecimal(total), new BigDecimal(paid), due, status, null, type,
            type == InvoiceType.MAINTENANCE ? UUID.randomUUID() : null, null, Instant.now(), Instant.now());
        return invoices.save(invoice);
    }

    private Invoice rent(Contract contract, YearMonth period, String total, String paid, InvoiceStatus status, LocalDate due) {
        return invoice(contract, period, total, paid, status, due, InvoiceType.RENT);
    }

    @Test
    @DisplayName("hóa đơn: bỏ VOIDED, phí đã gộp, kỳ ngoài khoảng; công nợ và quá hạn tính trên hóa đơn chưa trả đủ")
    void invoiceAggregates() {
        rent(c1, YearMonth.of(2026, 8), "3000000", "3000000", InvoiceStatus.PAID, LocalDate.of(2026, 8, 5));
        rent(c1, YearMonth.of(2026, 9), "3200000", "1000000", InvoiceStatus.PARTIALLY_PAID, LocalDate.of(2026, 9, 5));
        Invoice october = rent(c1, YearMonth.of(2026, 10), "3000000", "0", InvoiceStatus.ISSUED, LocalDate.of(2026, 10, 20));
        rent(c1, YearMonth.of(2026, 7), "9999", "0", InvoiceStatus.VOIDED, LocalDate.of(2026, 7, 5));
        rent(c1, YearMonth.of(2025, 1), "1000000", "0", InvoiceStatus.ISSUED, LocalDate.of(2025, 1, 5));
        Invoice fee = invoice(c1, YearMonth.of(2026, 10), "200000", "0", InvoiceStatus.ISSUED, LocalDate.of(2026, 10, 1),
            InvoiceType.MAINTENANCE);
        fee.consolidateInto(october.getId());
        invoices.save(fee);
        rent(c2, YearMonth.of(2026, 9), "2000000", "2000000", InvoiceStatus.PAID, LocalDate.of(2026, 9, 5));

        List<InvoiceAggregate> rows = adapter.invoiceAggregates(List.of(p1), YearMonth.of(2025, 11),
            YearMonth.of(2026, 10), TODAY);

        assertEquals(3, rows.size(), "chỉ 3 kỳ của P1 trong khoảng");
        Map<YearMonth, InvoiceAggregate> byPeriod = rows.stream()
            .collect(java.util.stream.Collectors.toMap(InvoiceAggregate::period, r -> r));
        InvoiceAggregate sep = byPeriod.get(YearMonth.of(2026, 9));
        assertEquals(new BigDecimal("3200000"), sep.billed());
        assertEquals(new BigDecimal("1000000"), sep.collected());
        assertEquals(new BigDecimal("2200000"), sep.outstanding());
        assertEquals(1, sep.overdueCount());
        assertEquals(new BigDecimal("2200000"), sep.overdueAmount());
        InvoiceAggregate oct = byPeriod.get(YearMonth.of(2026, 10));
        assertEquals(1, oct.invoiceCount(), "phí đã gộp không tính lại");
        assertEquals(new BigDecimal("3000000"), oct.outstanding());
        assertEquals(0, oct.overdueCount(), "chưa đến hạn");
        assertEquals(BigDecimal.ZERO, byPeriod.get(YearMonth.of(2026, 8)).outstanding());

        assertEquals(4, adapter.invoiceAggregates(null, YearMonth.of(2025, 11), YearMonth.of(2026, 10), TODAY).size(),
            "phạm vi null là toàn hệ thống");
        assertTrue(adapter.invoiceAggregates(List.of(), YearMonth.of(2025, 11), YearMonth.of(2026, 10), TODAY).isEmpty());
    }

    @Test
    @DisplayName("phòng theo trạng thái và phiếu đang mở/quá hạn SLA theo nhà trọ")
    void roomsAndOpenTickets() {
        Instant now = Instant.parse("2026-10-14T05:00:00Z");
        tickets.save(ticket(p1, MaintenanceStatus.REPORTED, now.minus(Duration.ofHours(1)), LiableParty.OWNER));
        tickets.save(ticket(p1, MaintenanceStatus.IN_PROGRESS, now.plus(Duration.ofHours(5)), LiableParty.OWNER));
        tickets.save(ticket(p1, MaintenanceStatus.RESOLVED, now.minus(Duration.ofDays(3)), LiableParty.OWNER));
        tickets.save(ticket(p2, MaintenanceStatus.ASSIGNED, null, LiableParty.OWNER));

        assertEquals(3, adapter.roomStatusCounts(null).stream().mapToLong(StatisticsQueryPort.RoomStatusCount::count).sum());
        assertEquals(2, adapter.roomStatusCounts(List.of(p1)).size());

        List<StatisticsQueryPort.OpenMaintenanceCount> open = adapter.openMaintenance(List.of(p1), now);
        assertEquals(1, open.size());
        assertEquals(2, open.get(0).openCount());
        assertEquals(1, open.get(0).overdueCount());
        assertEquals(2, adapter.openMaintenance(null, now).size());
    }

    @Test
    @DisplayName("phiếu hoàn thành: tính theo lần đầu sửa xong, chi phí chủ chịu bỏ phần người thuê trả, cộng giờ xử lý")
    void completedMaintenance() {
        Instant start = Instant.now();
        MaintenanceTicket ownerPays = ticket(p1, MaintenanceStatus.IN_PROGRESS, null, LiableParty.OWNER,
            start.minus(Duration.ofHours(10)));
        ownerPays.resolve("Thay bóng đèn", new BigDecimal("300000"));
        tickets.save(ownerPays);
        MaintenanceTicket tenantPays = ticket(p1, MaintenanceStatus.IN_PROGRESS, null, LiableParty.TENANT,
            start.minus(Duration.ofHours(2)));
        tenantPays.resolve("Thay khóa", new BigDecimal("150000"));
        tickets.save(tenantPays);
        tickets.save(ticket(p2, MaintenanceStatus.REPORTED, null, LiableParty.OWNER));

        List<MaintenanceAggregate> rows = adapter.completedMaintenance(List.of(p1, p2),
            start.minus(Duration.ofDays(1)), Instant.now().plusSeconds(60));

        assertEquals(1, rows.size());
        MaintenanceAggregate row = rows.get(0);
        assertEquals(p1, row.propertyId());
        assertEquals(2, row.completedCount());
        assertEquals(new BigDecimal("300000"), row.ownerRepairCost());
        assertEquals(12.0, row.totalResolutionHours(), 0.1);
        assertTrue(adapter.completedMaintenance(List.of(p1), start.plus(Duration.ofDays(1)),
            start.plus(Duration.ofDays(2))).isEmpty(), "ngoài khoảng thời gian");
    }

    @Test
    @DisplayName("admin: nhà chờ duyệt kèm chủ nhà, tài khoản, đăng ký mới và hoạt động audit theo tháng")
    void adminQueries() {
        UUID ownerId = UUID.randomUUID();
        users.save(new User(ownerId, "owner", "owner@x", "Chủ nhà", null, UserRole.OWNER, User.UserStatus.ACTIVE, false,
            Instant.parse("2026-09-10T00:00:00Z")));
        users.save(new User(UUID.randomUUID(), "t1", "t1@x", "Thuê", null, UserRole.TENANT, User.UserStatus.INACTIVE,
            false, Instant.parse("2026-09-30T18:00:00Z")));
        properties.save(new Property(UUID.randomUUID(), "Mới", "c", null, 1, ownerId, PropertyApprovalStatus.PENDING,
            null, Instant.parse("2026-10-01T00:00:00Z")));
        audit.recordEvent(new AuditPort.AuditEvent(null, "A", ownerId, "x", "1", Map.of(), Instant.parse("2026-10-02T00:00:00Z")));
        audit.recordEvent(new AuditPort.AuditEvent(null, "B", ownerId, "x", "2", Map.of(), Instant.parse("2026-10-03T00:00:00Z")));
        audit.recordEvent(new AuditPort.AuditEvent(null, "C", null, "x", "3", Map.of(), Instant.parse("2026-11-03T00:00:00Z")));

        List<StatisticsQueryPort.PendingPropertyRow> pending = adapter.pendingProperties();
        assertEquals(List.of("P2", "Mới"), pending.stream().map(StatisticsQueryPort.PendingPropertyRow::name).toList());
        assertEquals("Chủ nhà", pending.get(1).ownerName());

        assertEquals(2, adapter.propertyApprovalCounts().size());
        assertEquals(2, adapter.userCounts().size());

        Instant from = Instant.parse("2026-08-31T17:00:00Z");
        Instant to = Instant.parse("2026-10-31T17:00:00Z");
        List<StatisticsQueryPort.SignupCount> signups = adapter.userSignups(from, to);
        assertEquals(2, signups.size());
        assertTrue(signups.stream().anyMatch(s -> s.role() == UserRole.TENANT && s.period().equals(YearMonth.of(2026, 10))),
            "18:00 UTC ngày 30/09 là ngày 01/10 giờ Việt Nam");

        List<StatisticsQueryPort.ActivityCount> activity = adapter.auditActivity(from, to);
        assertEquals(1, activity.size());
        assertEquals(2, activity.get(0).actions());
        assertEquals(1, activity.get(0).activeUsers());
    }

    private MaintenanceTicket ticket(UUID propertyId, MaintenanceStatus status, Instant dueAt, LiableParty liable) {
        return ticket(propertyId, status, dueAt, liable, Instant.now());
    }

    private MaintenanceTicket ticket(UUID propertyId, MaintenanceStatus status, Instant dueAt, LiableParty liable,
                                     Instant createdAt) {
        UUID roomId = rooms.findByPropertyId(propertyId).get(0).getId();
        return new MaintenanceTicket(UUID.randomUUID(), roomId, propertyId, null, UUID.randomUUID(), "Hỏng", "mô tả",
            MaintenancePriority.MEDIUM, status, BigDecimal.ZERO, null, null, liable, null, dueAt, null, createdAt,
            createdAt);
    }
}
