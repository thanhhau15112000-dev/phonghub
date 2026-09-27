package com.phonghub.application;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.TenantService;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantServiceUnitTest {

    private InMemoryTenantRepository tenantRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryPropertyRepository propertyRepo;
    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private InMemoryAuditAdapter auditAdapter;
    private LocalDemoAuthenticationAdapter authAdapter;
    private AuthorizationService authService;
    private TenantService tenantService;

    private Property property;
    private Tenant tenant1;
    private Tenant tenant2;
    private Contract contract;

    @BeforeEach
    void setUp() {
        tenantRepo = new InMemoryTenantRepository();
        contractRepo = new InMemoryContractRepository();
        propertyRepo = new InMemoryPropertyRepository();
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();
        auditAdapter = new InMemoryAuditAdapter();

        authAdapter = new LocalDemoAuthenticationAdapter();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);
        tenantService = new TenantService(tenantRepo, authAdapter, authService, auditAdapter);

        // Property owned by OWNER_1
        property = propertyRepo.save(new Property(
            UUID.randomUUID(),
            "Nhà trọ Hoa Mai",
            "123 Lê Văn Lương",
            "",
            5,
            LocalDemoAuthenticationAdapter.OWNER_1_ID,
            PropertyApprovalStatus.VERIFIED,
            null,
            Instant.now()
        ));

        // Tenant 1 resides in Property (via Contract)
        UUID tenant1UserId = UUID.randomUUID();
        tenant1 = tenantRepo.save(Tenant.create(
            tenant1UserId,
            "Nguyễn Văn An",
            "079201001111",
            "0901234567",
            "an@example.com",
            "TP.HCM"
        ));

        // Another tenant in system
        tenant2 = tenantRepo.save(Tenant.create(
            null,
            "Trần Thị Bích",
            "079201002222",
            "0909888777",
            "bich@example.com",
            "Đồng Nai"
        ));

        // Contract linking tenant 1 to property
        contract = Contract.create(
            property.id(),
            UUID.randomUUID(),
            tenant1.id(),
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        );
        contractRepo.save(contract);
    }

    @Test
    @DisplayName("Owner of property where tenant resides updates tenant successfully with audit log")
    void testOwnerCanUpdateTenantInformationSuccessfully() {
        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            tenant1.id(),
            "Nguyễn Văn An Updated",
            "079201009999",
            "0912345678",
            "an.new@example.com",
            "Bình Dương"
        );

        Tenant updated = tenantService.updateTenant(cmd);

        assertNotNull(updated);
        assertEquals("Nguyễn Văn An Updated", updated.fullName());
        assertEquals("079201009999", updated.identityCardNumber());
        assertEquals("0912345678", updated.phone());
        assertEquals("an.new@example.com", updated.email());
        assertEquals("Bình Dương", updated.permanentAddress());
        assertEquals(tenant1.userId(), updated.userId(), "User ID must remain unchanged");

        // Verify in repository
        Tenant inRepo = tenantRepo.findById(tenant1.id()).orElseThrow();
        assertEquals("Nguyễn Văn An Updated", inRepo.fullName());

        // Verify audit log
        List<AuditPort.AuditEvent> events = auditAdapter.getRecordedEvents();
        assertFalse(events.isEmpty());
        AuditPort.AuditEvent audit = events.getLast();
        assertEquals("TENANT_UPDATE", audit.action());
        assertEquals("TENANT", audit.targetType());
        assertEquals(tenant1.id().toString(), audit.targetId());
        assertEquals(LocalDemoAuthenticationAdapter.OWNER_1_ID, audit.actorId());
        assertEquals("Nguyễn Văn An Updated", audit.details().get("fullName"));
    }

    @Test
    @DisplayName("Admin updates tenant information successfully")
    void testAdminCanUpdateTenantSuccessfully() {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            tenant1.id(),
            "Nguyễn Văn An (Admin Edit)",
            "079201001111",
            "0901234567",
            "an@example.com",
            "TP.HCM"
        );

        Tenant updated = tenantService.updateTenant(cmd);
        assertEquals("Nguyễn Văn An (Admin Edit)", updated.fullName());
    }

    @Test
    @DisplayName("Unauthorized user (Staff or unrelated Owner) throws UnauthorizedPropertyAccessException (403)")
    void testUnauthorizedUserThrowsException() {
        // Staff
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            tenant1.id(),
            "Hacker Name",
            "079201001111",
            "0901234567",
            null,
            null
        );
        assertThrows(UnauthorizedPropertyAccessException.class, () -> tenantService.updateTenant(cmd));

        // Owner 2 who does not own the property where tenant1 resides
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_2);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> tenantService.updateTenant(cmd));
    }

    @Test
    @DisplayName("Changing CCCD to duplicate another tenant's CCCD throws DuplicateIdentityCardException (409)")
    void testDuplicateIdentityCardThrowsConflict() {
        // tenant2 already has "079201002222"
        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            tenant1.id(),
            "Nguyễn Văn An",
            "079201002222",
            "0901234567",
            null,
            null
        );

        assertThrows(DuplicateIdentityCardException.class, () -> tenantService.updateTenant(cmd));
    }

    @Test
    @DisplayName("Updating tenant with same CCCD is allowed")
    void testUpdatingWithSameCccdAllowed() {
        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            tenant1.id(),
            "Nguyễn Văn An Renamed",
            "079201001111",
            "0901234567",
            null,
            null
        );

        Tenant updated = tenantService.updateTenant(cmd);
        assertEquals("Nguyễn Văn An Renamed", updated.fullName());
        assertEquals("079201001111", updated.identityCardNumber());
    }

    @Test
    @DisplayName("Non-existent tenant ID throws TenantNotFoundException (404)")
    void testTenantNotFoundThrowsException() {
        TenantUseCase.UpdateTenantCommand cmd = new TenantUseCase.UpdateTenantCommand(
            UUID.randomUUID(),
            "Ai Đó",
            "079201008888",
            "0908888999",
            null,
            null
        );

        assertThrows(TenantNotFoundException.class, () -> tenantService.updateTenant(cmd));
    }

    @Test
    @DisplayName("Invalid phone format throws IllegalArgumentException")
    void testInvalidPhoneFormatThrowsIllegalArgumentException() {
        // Less than 10 digits
        assertThrows(IllegalArgumentException.class, () -> new TenantUseCase.UpdateTenantCommand(
            tenant1.id(), "Tên", "079201001111", "090123", null, null
        ));

        // Does not start with 0
        assertThrows(IllegalArgumentException.class, () -> new TenantUseCase.UpdateTenantCommand(
            tenant1.id(), "Tên", "079201001111", "1901234567", null, null
        ));

        // 11 digits
        assertThrows(IllegalArgumentException.class, () -> new TenantUseCase.UpdateTenantCommand(
            tenant1.id(), "Tên", "079201001111", "09012345678", null, null
        ));
    }
}
