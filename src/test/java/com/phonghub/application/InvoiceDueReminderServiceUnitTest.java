package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceReminderRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.service.InvoiceDueReminderService;
import com.phonghub.application.service.NotificationDispatcher;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #86: nhắc hóa đơn sắp đến hạn cho người thuê, mỗi hạn thanh toán một lần. */
class InvoiceDueReminderServiceUnitTest {

    /** Hợp đồng có ngày thanh toán 5 nên hóa đơn kỳ 11/2026 hạn 05/11/2026. */
    private static final LocalDate DUE = LocalDate.of(2026, 11, 5);

    private InMemoryInvoiceRepository invoices;
    private InMemoryNotificationRepository notifications;
    private InMemoryContractRepository contracts;
    private InvoiceDueReminderService service;
    private Contract contract;
    private User owner;
    private User staff;
    private User tenantUser;
    private User occupantUser;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        notifications = new InMemoryNotificationRepository();
        contracts = new InMemoryContractRepository();
        InMemoryRoomRepository rooms = new InMemoryRoomRepository();
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        InMemoryTenantRepository tenants = new InMemoryTenantRepository();
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryStaffPropertyAssignmentRepository assignments = new InMemoryStaffPropertyAssignmentRepository();
        NotificationDispatcher dispatcher = new NotificationDispatcher(notifications, properties, assignments, tenants,
            users);
        service = new InvoiceDueReminderService(invoices, new InMemoryInvoiceReminderRepository(), contracts, rooms,
            properties, dispatcher, 3);

        owner = users.save(user("owner", UserRole.OWNER));
        staff = users.save(user("staff", UserRole.STAFF));
        tenantUser = users.save(user("tenant", UserRole.TENANT));
        occupantUser = users.save(user("occupant", UserRole.TENANT));
        UUID propertyId = UUID.randomUUID();
        properties.save(new Property(propertyId, "Nhà Một", "a", null, 1, owner.id(), PropertyApprovalStatus.VERIFIED,
            null, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staff.id(), propertyId, true, true,
            Instant.now()));
        Room room = rooms.save(new Room(UUID.randomUUID(), propertyId, "A101", 1, null, new BigDecimal("3000000"), 2,
            RoomStatus.OCCUPIED, Instant.now(), Instant.now()));
        Tenant primary = tenants.save(Tenant.create(tenantUser.id(), "Người Thuê", "079201000001", "0900000001", null, null));
        Tenant occupant = tenants.save(Tenant.create(occupantUser.id(), "Người Ở Cùng", "079201000002", "0900000002",
            null, null));
        Tenant left = tenants.save(Tenant.create(users.save(user("left", UserRole.TENANT)).id(), "Đã Rời Đi",
            "079201000003", "0900000003", null, null));
        UUID contractId = UUID.randomUUID();
        contract = contracts.save(new Contract(contractId, propertyId, room.getId(), primary.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 5, ContractStatus.ACTIVE,
            List.of(
                new ContractOccupant(UUID.randomUUID(), contractId, primary.id(), true, LocalDate.of(2026, 1, 1), null),
                new ContractOccupant(UUID.randomUUID(), contractId, occupant.id(), false, LocalDate.of(2026, 1, 1), null),
                new ContractOccupant(UUID.randomUUID(), contractId, left.id(), false, LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 1))),
            Instant.now(), Instant.now()));
    }

    private static User user(String username, UserRole role) {
        return new User(UUID.randomUUID(), username, username + "@x", username, null, role, User.UserStatus.ACTIVE,
            false, Instant.now());
    }

    private Invoice invoice(YearMonth period) {
        return invoices.save(Invoice.issueMonthlyRent(contract, period, "PH" + period, List.of()));
    }

    private Set<UUID> recipients() {
        return notifications.findAll().stream().map(Notification::targetUserId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("hạn thanh toán trong 3 ngày tới: nhắc người thuê chính và người ở cùng, không nhắc chủ trọ/staff/người đã rời")
    void remindsTenants() {
        Invoice invoice = invoice(YearMonth.of(2026, 11));
        assertEquals(DUE, invoice.getDueDate());

        assertEquals(0, service.runDaily(DUE.minusDays(4)), "ngoài cửa sổ nhắc");
        assertEquals(1, service.runDaily(DUE.minusDays(3)));

        assertEquals(Set.of(tenantUser.id(), occupantUser.id()), recipients());
        Notification sample = notifications.findAll().getFirst();
        assertEquals("Hóa đơn sắp đến hạn", sample.title());
        assertEquals("/invoices/" + invoice.getId() + "/pay", sample.link());
        assertTrue(sample.message().startsWith("Hóa đơn kỳ 11/2026 (phòng A101 - Nhà Một) còn "), sample.message());
        assertTrue(sample.message().endsWith("hạn thanh toán 05/11/2026 (còn 3 ngày)."), sample.message());
    }

    @Test
    @DisplayName("chạy lại không nhắc trùng; đến ngày hạn vẫn không nhắc lại")
    void idempotent() {
        invoice(YearMonth.of(2026, 11));
        assertEquals(1, service.runDaily(DUE.minusDays(2)));
        assertEquals(0, service.runDaily(DUE.minusDays(2)));
        assertEquals(0, service.runDaily(DUE));
        assertEquals(2, notifications.findAll().size());
    }

    @Test
    @DisplayName("đến hạn hôm nay ghi '(hôm nay)'; hóa đơn đã trả đủ hoặc đã hủy không nhắc")
    void dueTodayAndClosedInvoices() {
        invoice(YearMonth.of(2026, 11));
        Invoice paid = invoice(YearMonth.of(2026, 10));
        paid.applyPayment(paid.getTotalAmount(), Instant.now());
        invoices.save(paid);
        Invoice voided = invoice(YearMonth.of(2026, 12));
        voided.voidInvoice();
        invoices.save(voided);

        assertEquals(1, service.runDaily(DUE));
        assertTrue(notifications.findAll().getFirst().message().endsWith("(hôm nay)."));
        assertEquals(0, service.runDaily(LocalDate.of(2026, 10, 3)), "kỳ 10 đã trả đủ");
        assertEquals(0, service.runDaily(LocalDate.of(2026, 12, 3)), "kỳ 12 đã hủy");
        assertThrows(IllegalArgumentException.class, () -> new InvoiceDueReminderService(invoices,
            new InMemoryInvoiceReminderRepository(), contracts, new InMemoryRoomRepository(),
            new InMemoryPropertyRepository(), null, -1));
    }
}
