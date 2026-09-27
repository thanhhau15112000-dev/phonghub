package com.phonghub.application;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationAndPropertyScopeUnitTest {

    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private InMemoryPropertyRepository propertyRepo;
    private InMemoryTenantRepository tenantRepo;
    private InMemoryContractRepository contractRepo;
    private AuthorizationService authService;

    private UUID prop1Id;
    private UUID prop2Id;

    private CurrentUser admin;
    private CurrentUser ownerUser1;
    private CurrentUser ownerUser2;
    private CurrentUser staffUser;
    private CurrentUser techUser;
    private CurrentUser tenantUser;

    @BeforeEach
    void setUp() {
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();
        propertyRepo = new InMemoryPropertyRepository();
        tenantRepo = new InMemoryTenantRepository();
        contractRepo = new InMemoryContractRepository();

        authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);

        prop1Id = UUID.randomUUID();
        prop2Id = UUID.randomUUID();

        UUID owner1Id = UUID.randomUUID();
        UUID owner2Id = UUID.randomUUID();

        admin = new CurrentUser(UUID.randomUUID(), "admin@local", "Admin", UserRole.ADMIN);
        ownerUser1 = new CurrentUser(owner1Id, "owner1@local", "Owner 1", UserRole.OWNER);
        ownerUser2 = new CurrentUser(owner2Id, "owner2@local", "Owner 2", UserRole.OWNER);
        staffUser = new CurrentUser(UUID.randomUUID(), "staff@local", "Staff", UserRole.STAFF);
        techUser = new CurrentUser(UUID.randomUUID(), "tech@local", "Tech", UserRole.TECHNICIAN);
        tenantUser = new CurrentUser(UUID.randomUUID(), "tenant@local", "Tenant", UserRole.TENANT);

        propertyRepo.save(new Property(prop1Id, "Prop 1", "Addr 1", "", 10, owner1Id, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        propertyRepo.save(new Property(prop2Id, "Prop 2", "Addr 2", "", 5, null, PropertyApprovalStatus.VERIFIED, null, Instant.now()));

        // Staff and Tech assigned only to Prop 1
        assignmentRepo.save(new StaffPropertyAssignment(UUID.randomUUID(), staffUser.id(), prop1Id, true, true, Instant.now()));
        assignmentRepo.save(new StaffPropertyAssignment(UUID.randomUUID(), techUser.id(), prop1Id, false, false, Instant.now()));

        // Tenant linked to active contract in Prop 1
        Tenant tenant = new Tenant(UUID.randomUUID(), tenantUser.id(), "Tenant", "001", "0900", "t@local", "Addr", Instant.now());
        tenantRepo.save(tenant);
        Contract contract = new Contract(
            UUID.randomUUID(), prop1Id, UUID.randomUUID(), tenant.id(),
            BigDecimal.ZERO, new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusMonths(6), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );
        contractRepo.save(contract);
    }

    @Test
    @DisplayName("ADMIN can access any property")
    void testAdminCanAccessAllProperties() {
        assertDoesNotThrow(() -> authService.assertCanAccessProperty(admin, prop1Id));
        assertDoesNotThrow(() -> authService.assertCanAccessProperty(admin, prop2Id));
        assertEquals(2, authService.getAccessiblePropertyIds(admin).size());
    }

    @Test
    @DisplayName("STAFF can only access assigned property; unassigned throws UnauthorizedPropertyAccessException")
    void testStaffScope() {
        assertDoesNotThrow(() -> authService.assertCanAccessProperty(staffUser, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanAccessProperty(staffUser, prop2Id));

        Set<UUID> ids = authService.getAccessiblePropertyIds(staffUser);
        assertEquals(1, ids.size());
        assertTrue(ids.contains(prop1Id));
    }

    @Test
    @DisplayName("TECHNICIAN can work on assigned property; unassigned throws UnauthorizedPropertyAccessException")
    void testTechnicianScope() {
        assertDoesNotThrow(() -> authService.assertTechnicianCanWorkOnProperty(techUser, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertTechnicianCanWorkOnProperty(techUser, prop2Id));
    }

    @Test
    @DisplayName("TENANT can only access property of active contract; unassigned throws UnauthorizedPropertyAccessException")
    void testTenantScope() {
        assertDoesNotThrow(() -> authService.assertCanAccessProperty(tenantUser, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanAccessProperty(tenantUser, prop2Id));
    }

    @Test
    @DisplayName("assertAdmin rejects non-admin users")
    void testAssertAdmin() {
        assertDoesNotThrow(() -> authService.assertAdmin(admin));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertAdmin(ownerUser1));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertAdmin(staffUser));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertAdmin(techUser));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertAdmin(tenantUser));
    }

    @Test
    @DisplayName("OWNER can access and manage owned property; non-owned throws UnauthorizedPropertyAccessException")
    void testOwnerScope() {
        assertDoesNotThrow(() -> authService.assertCanAccessProperty(ownerUser1, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanAccessProperty(ownerUser1, prop2Id));

        assertDoesNotThrow(() -> authService.assertCanManageProperty(ownerUser1, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanManageProperty(ownerUser1, prop2Id));

        Set<UUID> ids = authService.getAccessiblePropertyIds(ownerUser1);
        assertEquals(1, ids.size());
        assertTrue(ids.contains(prop1Id));
    }

    @Test
    @DisplayName("OWNER with no properties returns empty set and cannot access other properties")
    void testOwnerWithNoPropertiesReturnsEmpty() {
        Set<UUID> ids = authService.getAccessiblePropertyIds(ownerUser2);
        assertTrue(ids.isEmpty());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanAccessProperty(ownerUser2, prop1Id));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> authService.assertCanAccessProperty(ownerUser2, prop2Id));
    }
}
