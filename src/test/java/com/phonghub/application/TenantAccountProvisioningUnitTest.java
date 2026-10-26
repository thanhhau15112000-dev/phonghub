package com.phonghub.application;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.TenantService;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cấp hoặc gắn tài khoản TENANT cho hồ sơ người thuê, và tạo hồ sơ kèm tài khoản trong một bước (#198, #219). */
class TenantAccountProvisioningUnitTest {

    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private IdentityProviderPort identityProvider;
    private TenantService tenantService;
    private Tenant tenant;
    private UUID propertyId;

    @BeforeEach
    void setUp() {
        tenantRepo = new InMemoryTenantRepository();
        userRepo = new InMemoryUserRepository();
        InMemoryContractRepository contractRepo = new InMemoryContractRepository();
        InMemoryPropertyRepository propertyRepo = new InMemoryPropertyRepository();
        LocalDemoAuthenticationAdapter authAdapter = new LocalDemoAuthenticationAdapter();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        AuthorizationService authService = new AuthorizationService(
            new InMemoryStaffPropertyAssignmentRepository(), propertyRepo, tenantRepo, contractRepo);
        identityProvider = mock(IdentityProviderPort.class);
        tenantService = new TenantService(tenantRepo, authAdapter, authService, new InMemoryAuditAdapter(),
            contractRepo, null, userRepo, identityProvider);

        Property property = propertyRepo.save(new Property(UUID.randomUUID(), "Nhà trọ A", "1 Đường A", "", 5,
            LocalDemoAuthenticationAdapter.OWNER_1_ID, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        propertyId = property.id();
        tenant = tenantRepo.save(Tenant.create(null, "Lê Văn C", "079200007777", "0911222333", null, "Cần Thơ"));
        tenantRepo.linkToProperty(tenant.id(), propertyId, LocalDemoAuthenticationAdapter.OWNER_1_ID);
    }

    @Test
    @DisplayName("A new TENANT account with a one-time password is created and linked to the profile")
    void createsNewAccount() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);

        TenantUseCase.TenantAccountResult result = issue("levanc@example.com");

        assertNotNull(result.temporaryPassword());
        assertEquals(authUserId, tenantRepo.findById(tenant.id()).orElseThrow().userId());
        User user = userRepo.findById(authUserId).orElseThrow();
        assertEquals(UserRole.TENANT, user.role());
        assertEquals(result.username(), user.username());
    }

    @Test
    @DisplayName("An existing TENANT account with the same email is linked without creating a new one")
    void linksExistingAccount() {
        User existing = userRepo.save(new User(UUID.randomUUID(), "levanc", "levanc@example.com", "Lê Văn C",
            "0911222333", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));

        TenantUseCase.TenantAccountResult result = issue("LeVanC@example.com");

        assertNull(result.temporaryPassword());
        assertEquals(existing.id(), tenantRepo.findById(tenant.id()).orElseThrow().userId());
        verify(identityProvider, never()).adminCreateUser(anyString(), anyString());
    }

    @Test
    @DisplayName("A profile that already has an account, or an email of another role, is refused")
    void refusesInvalidRequests() {
        userRepo.save(new User(UUID.randomUUID(), "owner9", "owner9@example.com", "Chủ trọ", "0900000009",
            UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.now()));
        assertThrows(ConflictException.class, () -> issue("owner9@example.com"));

        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);
        issue("levanc@example.com");
        assertThrows(ConflictException.class, () -> issue("khac@example.com"));
    }

    @Test
    @DisplayName("Creating a tenant creates the profile, the account and the property link in one step")
    void createsProfileWithAccount() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);

        TenantUseCase.TenantAccountResult result = tenantService.createTenantAccount(
            new TenantUseCase.CreateTenantAccountCommand(propertyId, "Phạm Thị D", "079200001111", "0911000111",
                "phamd@example.com", null));

        assertNotNull(result.temporaryPassword());
        Tenant created = tenantRepo.findByIdentityCardNumber("079200001111").orElseThrow();
        assertEquals(authUserId, created.userId());
        assertTrue(tenantRepo.findLinkedPropertyIds(created.id()).contains(propertyId));
    }

    @Test
    @DisplayName("A CCCD that already has an account, or a phone/email held by another account, is a conflict")
    void refusesDuplicates() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);
        issue("levanc@example.com");

        assertThrows(DuplicateIdentityCardException.class, () -> tenantService.createTenantAccount(
            new TenantUseCase.CreateTenantAccountCommand(propertyId, "Lê Văn C", "079200007777", "0911222333",
                "levanc2@example.com", null)));
        assertThrows(ConflictException.class, () -> tenantService.createTenantAccount(
            new TenantUseCase.CreateTenantAccountCommand(propertyId, "Người khác", "079200002222", "0911222333",
                "khac2@example.com", null)));
        verify(identityProvider, times(1)).adminCreateUser(anyString(), anyString());
    }

    @Test
    @DisplayName("A phone held by another account is refused before any identity is created")
    void refusesPhoneHeldByAnotherAccount() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);
        userRepo.save(new User(UUID.randomUUID(), "taken", "x@example.com", "X", "0911222333",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));

        assertThrows(ConflictException.class, () -> issue("levanc@example.com"));
        verify(identityProvider, never()).adminCreateUser(anyString(), anyString());
    }

    private TenantUseCase.TenantAccountResult issue(String email) {
        return tenantService.issueTenantAccount(new TenantUseCase.IssueTenantAccountCommand(propertyId, tenant.id(), email));
    }
}
