package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.PropertyChatContactScope;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Phạm vi liên hệ chat theo nhà trọ phải loại người ở cùng đã rời phòng và tài khoản ngừng hoạt động. */
class PropertyChatContactScopeUnitTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private InMemoryUserRepository users;
    private InMemoryPropertyRepository properties;
    private InMemoryStaffPropertyAssignmentRepository assignments;
    private InMemoryContractRepository contracts;
    private InMemoryTenantRepository tenants;
    private PropertyChatContactScope scope;

    private UUID adminId;
    private UUID inactiveAdminId;
    private UUID ownerId;
    private UUID activeStaffId;
    private UUID suspendedStaffId;
    private UUID primaryTenantUserId;
    private UUID activeCoOccupantUserId;
    private UUID futureCheckoutUserId;
    private UUID checkedOutTodayUserId;
    private UUID checkedOutPastUserId;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        properties = new InMemoryPropertyRepository();
        assignments = new InMemoryStaffPropertyAssignmentRepository();
        contracts = new InMemoryContractRepository();
        tenants = new InMemoryTenantRepository();
        scope = new PropertyChatContactScope(users, properties, assignments, contracts, tenants);

        adminId = saveUser("admin", UserRole.ADMIN, User.UserStatus.ACTIVE);
        inactiveAdminId = saveUser("admin2", UserRole.ADMIN, User.UserStatus.INACTIVE);
        ownerId = saveUser("owner", UserRole.OWNER, User.UserStatus.ACTIVE);
        activeStaffId = saveUser("staff1", UserRole.STAFF, User.UserStatus.ACTIVE);
        suspendedStaffId = saveUser("staff2", UserRole.STAFF, User.UserStatus.SUSPENDED);
        primaryTenantUserId = saveUser("tenant-main", UserRole.TENANT, User.UserStatus.ACTIVE);
        activeCoOccupantUserId = saveUser("co-active", UserRole.TENANT, User.UserStatus.ACTIVE);
        futureCheckoutUserId = saveUser("co-future", UserRole.TENANT, User.UserStatus.ACTIVE);
        checkedOutTodayUserId = saveUser("co-today", UserRole.TENANT, User.UserStatus.ACTIVE);
        checkedOutPastUserId = saveUser("co-past", UserRole.TENANT, User.UserStatus.ACTIVE);

        UUID propertyId = UUID.randomUUID();
        properties.save(new Property(propertyId, "Nhà trọ A", "123 Lê Lợi", "Mô tả", 5,
            ownerId, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), activeStaffId, propertyId,
            true, false, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), suspendedStaffId, propertyId,
            true, false, Instant.now()));

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        UUID primaryTenantId = saveTenant(primaryTenantUserId, "001000000001");
        UUID activeCoId = saveTenant(activeCoOccupantUserId, "001000000002");
        UUID futureCoId = saveTenant(futureCheckoutUserId, "001000000003");
        UUID todayCoId = saveTenant(checkedOutTodayUserId, "001000000004");
        UUID pastCoId = saveTenant(checkedOutPastUserId, "001000000005");

        Contract contract = new Contract(UUID.randomUUID(), propertyId, UUID.randomUUID(), primaryTenantId,
            BigDecimal.TEN, BigDecimal.valueOf(1_000_000), today.minusMonths(2), today.plusMonths(4),
            5, ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now());
        LocalDate checkIn = today.minusMonths(1);
        contract.addOccupant(primaryTenantId, true, checkIn);
        contract.addOccupant(activeCoId, false, checkIn);
        contract.addOccupant(futureCoId, false, checkIn);
        contract.addOccupant(todayCoId, false, checkIn);
        contract.addOccupant(pastCoId, false, checkIn);

        contract.checkOutOccupant(futureCoId, today.plusDays(1));
        contract.checkOutOccupant(todayCoId, today);
        contract.checkOutOccupant(pastCoId, today.minusDays(2));
        contracts.save(contract);
    }

    @Test
    @DisplayName("Chủ trọ chỉ thấy người đang ở (chưa rời phòng hoặc ngày rời phòng sau hôm nay) và tài khoản ACTIVE")
    void excludesCheckedOutCoOccupantsAndInactiveAccounts() {
        CurrentUser owner = new CurrentUser(ownerId, "owner@x", "Chủ trọ", UserRole.OWNER);
        Set<UUID> reachable = scope.reachableUserIds(owner);

        assertEquals(
            Set.of(adminId, activeStaffId, primaryTenantUserId, activeCoOccupantUserId, futureCheckoutUserId),
            reachable
        );
        assertFalse(reachable.contains(checkedOutTodayUserId));
        assertFalse(reachable.contains(checkedOutPastUserId));
        assertFalse(reachable.contains(suspendedStaffId));
        assertFalse(reachable.contains(inactiveAdminId));
    }

    @Test
    @DisplayName("Quản trị viên chỉ thấy các tài khoản ACTIVE, loại INACTIVE và SUSPENDED")
    void adminScopeExcludesNonActiveAccounts() {
        CurrentUser admin = new CurrentUser(adminId, "admin@x", "Quản trị viên", UserRole.ADMIN);
        Set<UUID> reachable = scope.reachableUserIds(admin);

        assertTrue(reachable.contains(ownerId));
        assertTrue(reachable.contains(activeStaffId));
        assertFalse(reachable.contains(adminId));
        assertFalse(reachable.contains(inactiveAdminId));
        assertFalse(reachable.contains(suspendedStaffId));
    }

    private UUID saveUser(String slug, UserRole role, User.UserStatus status) {
        UUID id = UUID.randomUUID();
        users.save(new User(id, slug, slug + "@x.vn", "Tên " + slug, "0900000001",
            role, status, false, Instant.now()));
        return id;
    }

    private UUID saveTenant(UUID userId, String cccd) {
        UUID id = UUID.randomUUID();
        tenants.save(new Tenant(id, userId, "Người thuê " + cccd, cccd, "0901234567", null, null, Instant.now()));
        return id;
    }
}

