package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractReminderRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractLifecycleUseCase.RunResult;
import com.phonghub.application.service.ContractLifecycleService;
import com.phonghub.application.service.TenantLockPolicy;
import com.phonghub.domain.exception.DomainException;
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
import java.time.Duration;
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

/** #87: hết hạn hợp đồng tự động (idempotent, không trả phòng) và nhắc trước hạn. */
class ContractLifecycleServiceUnitTest {

    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    private InMemoryContractRepository contracts;
    private InMemoryRoomRepository rooms;
    private InMemoryNotificationRepository notifications;
    private InMemoryAuditAdapter audit;
    private InMemoryStaffPropertyAssignmentRepository assignments;
    private ContractLifecycleService service;
    private Room room;
    private Contract contract;
    private User owner;
    private User staffManager;
    private User staffCashier;
    private User tenantUser;
    private User occupantUser;
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        contracts = new InMemoryContractRepository();
        rooms = new InMemoryRoomRepository();
        notifications = new InMemoryNotificationRepository();
        audit = new InMemoryAuditAdapter();
        assignments = new InMemoryStaffPropertyAssignmentRepository();
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        InMemoryTenantRepository tenants = new InMemoryTenantRepository();
        InMemoryUserRepository users = new InMemoryUserRepository();
        service = new ContractLifecycleService(contracts, new InMemoryContractReminderRepository(), properties, rooms,
            tenants, users, assignments, notifications, audit, 30);

        owner = users.save(user("owner", UserRole.OWNER));
        staffManager = users.save(user("staff.manager", UserRole.STAFF));
        staffCashier = users.save(user("staff.cashier", UserRole.STAFF));
        tenantUser = users.save(user("tenant", UserRole.TENANT));
        occupantUser = users.save(user("occupant", UserRole.TENANT));
        properties.save(new Property(propertyId, "Nhà Một", "a", null, 1, owner.id(), PropertyApprovalStatus.VERIFIED, null,
            Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffManager.id(), propertyId, false, true,
            Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffCashier.id(), propertyId, true, false,
            Instant.now()));
        room = rooms.save(new Room(UUID.randomUUID(), propertyId, "A101", 1, null, new BigDecimal("3000000"), 2,
            RoomStatus.OCCUPIED, Instant.now(), Instant.now()));
        Tenant primary = tenants.save(Tenant.create(tenantUser.id(), "Người Thuê", "079201000001", "0900000001", null, null));
        Tenant occupant = tenants.save(Tenant.create(occupantUser.id(), "Người Ở Cùng", "079201000002", "0900000002", null, null));
        Tenant left = tenants.save(Tenant.create(null, "Đã Rời Đi", "079201000003", "0900000003", null, null));
        UUID contractId = UUID.randomUUID();
        contract = contracts.save(new Contract(contractId, propertyId, room.getId(), primary.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), END, 5, ContractStatus.ACTIVE, List.of(
                new ContractOccupant(UUID.randomUUID(), contractId, primary.id(), true, LocalDate.of(2026, 1, 1), null),
                new ContractOccupant(UUID.randomUUID(), contractId, occupant.id(), false, LocalDate.of(2026, 1, 1), null),
                new ContractOccupant(UUID.randomUUID(), contractId, left.id(), false, LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 1))),
            Instant.now(), Instant.now()));
    }

    private static User user(String username, UserRole role) {
        return new User(UUID.randomUUID(), username, username + "@x", username, null, role, User.UserStatus.ACTIVE, false,
            Instant.now());
    }

    private Set<UUID> recipients(String title) {
        return notifications.findAll().stream().filter(n -> n.title().equals(title)).map(Notification::targetUserId)
            .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("ngày kết thúc vẫn còn hiệu lực; qua ngày kết thúc thì EXPIRED, phòng vẫn OCCUPIED")
    void expiresAfterEndDate() {
        assertEquals(new RunResult(0, 1), service.runDaily(END), "ngày cuối: chỉ nhắc, chưa hết hạn");
        assertEquals(ContractStatus.ACTIVE, contracts.findById(contract.getId()).orElseThrow().getStatus());

        assertEquals(new RunResult(1, 0), service.runDaily(END.plusDays(1)));
        Contract expired = contracts.findById(contract.getId()).orElseThrow();
        assertEquals(ContractStatus.EXPIRED, expired.getStatus());
        assertTrue(expired.isResiding(), "hết hạn nhưng chưa check-out vẫn đang ở");
        assertEquals(RoomStatus.OCCUPIED, rooms.findById(room.getId()).orElseThrow().getStatus(), "không tự trả phòng");
        assertEquals("CONTRACT_EXPIRED", audit.getRecordedEvents().get(0).action());
        assertEquals(Set.of(owner.id(), staffManager.id(), tenantUser.id(), occupantUser.id()),
            recipients("Hợp đồng đã hết hạn"));
    }

    @Test
    @DisplayName("chạy lại không làm gì thêm (idempotent), kể cả ngày sau")
    void idempotent() {
        service.runDaily(END.plusDays(1));
        int sent = notifications.findAll().size();

        assertEquals(new RunResult(0, 0), service.runDaily(END.plusDays(1)));
        assertEquals(new RunResult(0, 0), service.runDaily(END.plusDays(5)));
        assertEquals(sent, notifications.findAll().size());
        assertEquals(1, audit.getRecordedEvents().size());
    }

    @Test
    @DisplayName("nhắc trong 30 ngày trước hạn, mỗi ngày kết thúc một lần; người nhận đúng vai trò")
    void remindsOncePerEndDate() {
        assertEquals(new RunResult(0, 0), service.runDaily(END.minusDays(31)), "ngoài cửa sổ nhắc");
        assertEquals(new RunResult(0, 1), service.runDaily(END.minusDays(30)));
        assertEquals(new RunResult(0, 0), service.runDaily(END.minusDays(10)), "đã nhắc cho ngày kết thúc này");

        Set<UUID> to = recipients("Hợp đồng sắp hết hạn");
        assertEquals(Set.of(owner.id(), staffManager.id(), tenantUser.id(), occupantUser.id()), to);
        assertFalse(to.contains(staffCashier.id()), "staff không quản lý hợp đồng không nhận");
        Notification sample = notifications.findAll().get(0);
        assertTrue(sample.message().contains("phòng A101 - Nhà Một"), sample.message());
        assertTrue(sample.message().contains("31/12/2026 (còn 30 ngày)"), sample.message());
    }

    @Test
    @DisplayName("không đụng hợp đồng nháp/đã kết thúc; số ngày nhắc âm bị từ chối")
    void ignoresOtherStatuses() {
        Contract draft = contracts.save(Contract.create(propertyId, room.getId(), contract.getPrimaryTenantId(),
            BigDecimal.ONE, BigDecimal.TEN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30), 5));
        assertEquals(1, service.runDaily(END.plusDays(1)).expired());
        assertEquals(ContractStatus.DRAFT, contracts.findById(draft.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class, () -> new ContractLifecycleService(contracts,
            new InMemoryContractReminderRepository(), null, null, null, null, null, null, null, -1));
    }

    @Test
    @DisplayName("domain: hết hạn chỉ từ ACTIVE; hợp đồng hết hạn vẫn kết thúc được khi check-out")
    void domainTransitions() {
        Contract c = contracts.findById(contract.getId()).orElseThrow();
        assertFalse(c.isPastEndDate(END));
        assertTrue(c.isPastEndDate(END.plusDays(1)));
        c.expire();
        assertFalse(c.isPastEndDate(END.plusDays(2)), "đã hết hạn thì không xử lý lại");
        assertThrows(DomainException.class, c::expire);
        c.terminate();
        assertEquals(ContractStatus.TERMINATED, c.getStatus());
        assertFalse(c.isResiding());
        assertFalse(c.isOpen());
        assertThrows(DomainException.class, c::terminate);
    }

    @Test
    @DisplayName("hết hạn chưa check-out: vẫn là hợp đồng của phòng, hồ sơ không bị khóa, vẫn lập hóa đơn trong thời hạn")
    void expiredButResiding() {
        service.runDaily(END.plusDays(1));
        Contract expired = contracts.findById(contract.getId()).orElseThrow();

        assertEquals(contract.getId(), contracts.findActiveByRoomId(room.getId()).orElseThrow().getId());
        assertTrue(expired.isOpen());
        TenantLockPolicy.LockInfo lock = new TenantLockPolicy(contracts, Duration.ofDays(30))
            .lockInfo(contract.getPrimaryTenantId());
        assertTrue(lock.live(), "người thuê còn ở thì hồ sơ không bị khóa/xóa");
        assertFalse(lock.locked());

        Invoice last = Invoice.issueMonthlyRent(expired, YearMonth.from(END), "PH-TEST", List.of());
        assertEquals(12, last.getMonth());
        assertThrows(DomainException.class,
            () -> Invoice.issueMonthlyRent(expired, YearMonth.from(END).plusMonths(1), "PH-TEST", List.of()),
            "không lập hóa đơn ngoài thời hạn hợp đồng");
    }
}
