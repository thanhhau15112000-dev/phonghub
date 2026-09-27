package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ContractServiceAddOccupantUnitTest {

    private InMemoryPropertyRepository propertyRepo;
    private InMemoryRoomRepository roomRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private LocalDemoAuthenticationAdapter authAdapter;
    private AuthorizationService authService;
    private IdentityProviderPort identityProvider;
    private ContractService contractService;

    private Property property;
    private Room room;
    private Tenant primaryTenant;
    private Contract activeContract;

    @BeforeEach
    void setUp() {
        propertyRepo = new InMemoryPropertyRepository();
        roomRepo = new InMemoryRoomRepository();
        contractRepo = new InMemoryContractRepository();
        tenantRepo = new InMemoryTenantRepository();
        userRepo = new InMemoryUserRepository();
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();

        authAdapter = new LocalDemoAuthenticationAdapter();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);
        identityProvider = Mockito.mock(IdentityProviderPort.class);

        contractService = new ContractService(
            contractRepo,
            roomRepo,
            propertyRepo,
            tenantRepo,
            userRepo,
            identityProvider,
            authAdapter,
            authService
        );

        // Setup verified property owned by OWNER_1
        property = propertyRepo.save(new Property(
            UUID.randomUUID(),
            "Nhà Trọ Hạnh Phúc",
            "123 Nguyễn Văn Linh, Q7",
            "",
            5,
            LocalDemoAuthenticationAdapter.OWNER_1_ID,
            PropertyApprovalStatus.VERIFIED,
            null,
            Instant.now()
        ));

        // Setup room with maxOccupants = 2
        room = roomRepo.save(Room.create(property.id(), "P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2));

        // Setup primary tenant
        primaryTenant = tenantRepo.save(Tenant.create(
            LocalDemoAuthenticationAdapter.TENANT_1_ID,
            "Nguyễn Văn A",
            "079200001111",
            "0901234567",
            "primary@phonghub.local",
            "TP.HCM"
        ));

        // Create and activate contract
        activeContract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(),
            room.getId(),
            primaryTenant.fullName(),
            primaryTenant.identityCardNumber(),
            primaryTenant.phone(),
            primaryTenant.email(),
            primaryTenant.userId(),
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        ));
        contractService.activateContract(activeContract.getId());
        activeContract = contractRepo.findById(activeContract.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Owner adds occupant to active contract successfully")
    void testOwnerAddsOccupantSuccessfully() {
        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Trần Thị B",
            "079200002222",
            "0909888999",
            "b@example.com",
            "Đồng Nai",
            LocalDate.now(),
            false
        );

        ContractOccupant occupant = contractService.addOccupant(cmd);

        assertNotNull(occupant);
        assertNotNull(occupant.id());
        assertEquals(activeContract.getId(), occupant.contractId());
        assertFalse(occupant.isPrimary());
        assertEquals(LocalDate.now(), occupant.checkInDate());

        Contract updatedContract = contractRepo.findById(activeContract.getId()).orElseThrow();
        assertEquals(2, updatedContract.getOccupants().size());

        Tenant savedTenant = tenantRepo.findByIdentityCardNumber("079200002222").orElseThrow();
        assertEquals("Trần Thị B", savedTenant.fullName());
        assertEquals("0909888999", savedTenant.phone());
        assertEquals(occupant.tenantId(), savedTenant.id());
    }

    @Test
    @DisplayName("Admin adds occupant to active contract successfully")
    void testAdminAddsOccupantSuccessfully() {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Lê Văn C",
            "079200003333",
            "0911222333",
            null,
            null,
            LocalDate.now(),
            false
        );

        ContractOccupant occupant = contractService.addOccupant(cmd);
        assertNotNull(occupant);
        assertEquals(2, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("Non-owner and non-admin throws UnauthorizedPropertyAccessException (403)")
    void testUnauthorizedUserThrowsException() {
        // Staff 1 is not assigned/authorized as owner
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Trần Thị B",
            "079200002222",
            "0909888999",
            null,
            null,
            LocalDate.now(),
            false
        );

        assertThrows(UnauthorizedPropertyAccessException.class, () -> contractService.addOccupant(cmd));

        // Owner 2 (different property owner)
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_2);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> contractService.addOccupant(cmd));
    }

    @Test
    @DisplayName("Property not VERIFIED throws InvalidPropertyStatusException (409)")
    void testUnverifiedPropertyThrowsInvalidPropertyStatusException() {
        Property pendingProp = propertyRepo.save(new Property(
            UUID.randomUUID(),
            "Nhà trọ Chờ Duyệt",
            "Địa chỉ",
            "",
            5,
            LocalDemoAuthenticationAdapter.OWNER_1_ID,
            PropertyApprovalStatus.PENDING,
            null,
            Instant.now()
        ));
        Room pendingRoom = roomRepo.save(Room.create(pendingProp.id(), "P201", 1, new BigDecimal("20.0"), new BigDecimal("3000000"), 2));
        Contract pendingContract = Contract.create(
            pendingProp.id(),
            pendingRoom.getId(),
            primaryTenant.id(),
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        );
        pendingContract.activate();
        contractRepo.save(pendingContract);

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            pendingContract.getId(),
            "Trần Thị B",
            "079200002222",
            "0909888999",
            null,
            null,
            LocalDate.now(),
            false
        );

        assertThrows(InvalidPropertyStatusException.class, () -> contractService.addOccupant(cmd));
    }

    @Test
    @DisplayName("Inactive contract throws DomainException")
    void testInactiveContractThrowsDomainException() {
        Room room2 = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2));
        Contract draftContract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(),
            room2.getId(),
            "Tenant Draft",
            "079200009999",
            "0900000000",
            null,
            null,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        ));

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            draftContract.getId(),
            "Trần Thị B",
            "079200002222",
            "0909888999",
            null,
            null,
            LocalDate.now(),
            false
        );

        DomainException ex = assertThrows(DomainException.class, () -> contractService.addOccupant(cmd));
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    @DisplayName("Exceeding room maxOccupants throws InvalidRoomCapacityException (409)")
    void testExceedingRoomCapacityThrowsInvalidRoomCapacityException() {
        // Add 1 occupant (total reaches maxOccupants = 2)
        contractService.addOccupant(new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Trần Thị B",
            "079200002222",
            "0909888999",
            null,
            null,
            LocalDate.now(),
            false
        ));

        // Add 2nd additional occupant (total 3 > max 2) -> must throw 409
        ContractUseCase.AddOccupantCommand thirdCmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Lê Văn C",
            "079200003333",
            "0911222333",
            null,
            null,
            LocalDate.now(),
            false
        );

        assertThrows(InvalidRoomCapacityException.class, () -> contractService.addOccupant(thirdCmd));
    }

    @Test
    @DisplayName("Tenant already active in another contract throws DuplicateActiveContractException (409)")
    void testTenantAlreadyActiveInAnotherContractThrowsConflict() {
        // Primary tenant of active contract 1 cannot be added as occupant anywhere
        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            primaryTenant.fullName(),
            primaryTenant.identityCardNumber(),
            primaryTenant.phone(),
            primaryTenant.email(),
            null,
            LocalDate.now(),
            false
        );

        assertThrows(DuplicateActiveContractException.class, () -> contractService.addOccupant(cmd));
    }

    @Test
    @DisplayName("Reuses existing tenant profile when CCCD already exists")
    void testReusesExistingTenantByCccd() {
        // Pre-create tenant with CCCD "079200008888"
        Tenant existing = tenantRepo.save(Tenant.create(
            null,
            "Cựu Khách Thuê",
            "079200008888",
            "0988777666",
            "old@phonghub.local",
            "Cần Thơ"
        ));

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Cựu Khách Thuê Updated",
            "079200008888",
            "0988777666",
            "updated@phonghub.local",
            "Cần Thơ",
            LocalDate.now(),
            false
        );

        ContractOccupant occ = contractService.addOccupant(cmd);
        assertEquals(existing.id(), occ.tenantId());

        // Verify no duplicate tenant created in repo
        assertEquals(2, tenantRepo.findAll().size()); // primaryTenant + existing
    }

    @Test
    @DisplayName("Provisions TENANT account when createAccount is true and valid email provided")
    void testAddOccupantProvisionsUserAccount() {
        UUID authUserId = UUID.randomUUID();
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenReturn(authUserId);

        ContractUseCase.AddOccupantCommand cmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Võ Thị D",
            "079200005555",
            "0933444555",
            "vothid@example.com",
            "Bình Dương",
            LocalDate.now(),
            true
        );

        ContractOccupant occ = contractService.addOccupant(cmd);
        assertNotNull(occ);

        Tenant savedTenant = tenantRepo.findByIdentityCardNumber("079200005555").orElseThrow();
        assertNotNull(savedTenant.userId(), "Tenant should be linked to user ID");

        User savedUser = userRepo.findById(savedTenant.userId()).orElseThrow();
        assertEquals("vothid@example.com", savedUser.email());
        assertEquals(UserRole.TENANT, savedUser.role());
    }
}
