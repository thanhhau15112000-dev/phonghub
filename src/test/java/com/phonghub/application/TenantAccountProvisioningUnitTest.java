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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cấp hoặc gắn tài khoản TENANT từ trang hồ sơ người thuê (#198). */
class TenantAccountProvisioningUnitTest {

    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private IdentityProviderPort identityProvider;
    private TenantService tenantService;
    private Tenant tenant;

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
        tenant = tenantRepo.save(Tenant.create(null, "Lê Văn C", "079200007777", "0911222333", null, "Cần Thơ"));
        tenantRepo.linkToProperty(tenant.id(), property.id(), LocalDemoAuthenticationAdapter.OWNER_1_ID);
    }

    @Test
    @DisplayName("A new TENANT account with a one-time password is created and linked to the profile")
    void createsNewAccount() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);

        TenantUseCase.AccountProvisioning result = tenantService.provisionAccount(tenant.id(), "levanc@example.com");

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

        TenantUseCase.AccountProvisioning result = tenantService.provisionAccount(tenant.id(), "LeVanC@example.com");

        assertNull(result.temporaryPassword());
        assertEquals(existing.id(), tenantRepo.findById(tenant.id()).orElseThrow().userId());
        verify(identityProvider, never()).adminCreateUser(anyString(), anyString());
    }

    @Test
    @DisplayName("A profile that already has an account, or an email of another role, is refused")
    void refusesInvalidRequests() {
        userRepo.save(new User(UUID.randomUUID(), "owner9", "owner9@example.com", "Chủ trọ", "0900000009",
            UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.now()));
        assertThrows(RuntimeException.class, () -> tenantService.provisionAccount(tenant.id(), "owner9@example.com"));

        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);
        tenantService.provisionAccount(tenant.id(), "levanc@example.com");
        assertThrows(IllegalArgumentException.class, () -> tenantService.provisionAccount(tenant.id(), "khac@example.com"));
    }
}
