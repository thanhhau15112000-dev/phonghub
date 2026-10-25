package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
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
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
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
import java.util.List;
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
    private InMemoryHandoverRepository handoverRepo;
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
        handoverRepo = new InMemoryHandoverRepository();
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
            authService,
            null,
            handoverRepo,
            null
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
        userRepo.save(new User(
            LocalDemoAuthenticationAdapter.TENANT_1_ID,
            "primary-tenant",
            "primary@phonghub.local",
            "Nguyễn Văn A",
            "0901234567",
            UserRole.TENANT,
            User.UserStatus.ACTIVE,
            false,
            Instant.now()
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
        activateWithApprovedCheckIn(activeContract.getId());
        activeContract = contractRepo.findById(activeContract.getId()).orElseThrow();
    }

    private void activateWithApprovedCheckIn(UUID contractId) {
        UUID actorId = authAdapter.getCurrentUser().id();
        Handover draft = Handover.draft(contractId, HandoverType.CHECK_IN, BigDecimal.ZERO, BigDecimal.ZERO,
            "Phòng đã kiểm tra", null, actorId);
        handoverRepo.save(draft.send("terms-hash", null, null, actorId).tenantConfirm(actorId).approve(actorId));
        contractService.activateContract(contractId);
    }

    @Test
    @DisplayName("Selected tenant account links to an existing profile matched by identity card")
    void selectedTenantAccountLinksToExistingProfile() {
        Tenant unlinked = tenantRepo.save(Tenant.create(
            null, "Lê Thị D", "079200004444", "0911222444", "d@example.com", null));
        User account = tenantAccount("tenant-d", "d@example.com", "Lê Thị D");

        Contract contract = contractService.createContract(createContractCommand(
            "079200004444", account.id()));

        assertEquals(unlinked.id(), contract.getPrimaryTenantId());
        assertEquals(account.id(), tenantRepo.findById(unlinked.id()).orElseThrow().userId());
    }

    @Test
    @DisplayName("Selected tenant account is saved on a newly created tenant profile")
    void selectedTenantAccountLinksToNewProfile() {
        User account = tenantAccount("tenant-new", "new-tenant@example.com", "Đỗ Thị P");

        Contract contract = contractService.createContract(createContractCommand("079200010008", account.id()));

        Tenant created = tenantRepo.findByIdentityCardNumber("079200010008").orElseThrow();
        assertEquals(account.id(), created.userId());
        assertEquals(created.id(), contract.getPrimaryTenantId());
    }

    @Test
    @DisplayName("Contract creation rejects an account that is not a tenant")
    void createContractRejectsNonTenantAccount() {
        User admin = new User(UUID.randomUUID(), "admin-user", "admin-user@example.com", "Admin User", null,
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now());
        userRepo.save(admin);

        assertThrows(IllegalArgumentException.class,
            () -> contractService.createContract(createContractCommand("079200005555", admin.id())));
        assertTrue(tenantRepo.findByIdentityCardNumber("079200005555").isEmpty());
    }

    @Test
    @DisplayName("Contract creation rejects an account already linked to a different tenant profile")
    void createContractRejectsAccountLinkedToDifferentTenant() {
        User account = tenantAccount("tenant-e", "e@example.com", "Ngô Thị E");
        Tenant linked = tenantRepo.save(Tenant.create(
            account.id(), "Ngô Thị E", "079200006666", "0911222666", "e@example.com", null));

        assertThrows(DomainException.class,
            () -> contractService.createContract(createContractCommand("079200007777", account.id())));
        assertTrue(tenantRepo.findByIdentityCardNumber("079200007777").isEmpty());
        assertEquals(linked.id(), tenantRepo.findByUserId(account.id()).orElseThrow().id());
    }

    @Test
    @DisplayName("Contract creation rejects linking an existing identity profile to a different account")
    void createContractRejectsDifferentAccountForExistingIdentityProfile() {
        User existingAccount = tenantAccount("tenant-existing", "existing@example.com", "Lý Thị Q");
        User selectedAccount = tenantAccount("tenant-selected", "selected@example.com", "Lý Thị Q");
        Tenant linked = tenantRepo.save(Tenant.create(
            existingAccount.id(), "Lý Thị Q", "079200010009", "0911222999", "existing@example.com", null));

        assertThrows(DomainException.class,
            () -> contractService.createContract(createContractCommand("079200010009", selectedAccount.id())));
        assertEquals(existingAccount.id(), tenantRepo.findById(linked.id()).orElseThrow().userId());
    }

    @Test
    @DisplayName("Bulk add accepts multiple linked, available tenants in one operation")
    void bulkAddExistingOccupantsSuccessfully() {
        room.updateInfo("P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 3);
        roomRepo.save(room);
        Tenant first = linkedAvailableTenant("079200008888", "Phạm Văn F");
        Tenant second = linkedAvailableTenant("079200009999", "Vũ Thị G");

        List<ContractOccupant> added = contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(first.id(), second.id()), LocalDate.now()));

        assertEquals(2, added.size());
        assertEquals(3, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
        assertFalse(contractService.listAvailableOccupants(activeContract.getId()).stream()
            .anyMatch(tenant -> tenant.id().equals(first.id()) || tenant.id().equals(second.id())));
    }

    @Test
    @DisplayName("Bulk add rejects the entire batch when capacity is exceeded")
    void bulkAddRejectsBatchWhenCapacityIsExceeded() {
        Tenant first = linkedAvailableTenant("079200010001", "Trần Văn H");
        Tenant second = linkedAvailableTenant("079200010002", "Nguyễn Thị I");

        assertThrows(InvalidRoomCapacityException.class, () -> contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(first.id(), second.id()), LocalDate.now())));

        assertEquals(1, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("Bulk capacity check counts the primary tenant when a legacy contract has no occupant rows")
    void bulkCapacityCountsPrimaryTenantWhenOccupantRowsAreMissing() {
        Contract legacyContract = new Contract(
            activeContract.getId(), activeContract.getPropertyId(), activeContract.getRoomId(),
            activeContract.getPrimaryTenantId(), activeContract.getDepositAmount(), activeContract.getRentAmount(),
            activeContract.getStartDate(), activeContract.getEndDate(), activeContract.getPaymentDay(),
            ContractStatus.ACTIVE, List.of(), activeContract.getCreatedAt(), activeContract.getUpdatedAt());
        contractRepo.save(legacyContract);
        Tenant first = linkedAvailableTenant("079200010010", "Lê Văn R");
        Tenant second = linkedAvailableTenant("079200010011", "Bùi Thị S");

        assertThrows(InvalidRoomCapacityException.class, () -> contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(first.id(), second.id()), LocalDate.now())));

        assertTrue(contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().isEmpty());
    }

    @Test
    @DisplayName("Bulk add rejects the entire batch when one selected tenant already resides elsewhere")
    void bulkAddRejectsBatchWhenOneTenantAlreadyResidesElsewhere() {
        room.updateInfo("P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 3);
        roomRepo.save(room);
        Tenant available = linkedAvailableTenant("079200010005", "Trần Văn L");
        Tenant residing = linkedAvailableTenant("079200010006", "Nguyễn Thị M");
        Room anotherRoom = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"),
            new BigDecimal("3500000"), 2));
        Contract otherContract = Contract.create(property.id(), anotherRoom.getId(), residing.id(),
            new BigDecimal("3500000"), new BigDecimal("3500000"), LocalDate.now(),
            LocalDate.now().plusMonths(6), 5);
        otherContract.activate();
        contractRepo.save(otherContract);

        assertThrows(DuplicateActiveContractException.class, () -> contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(available.id(), residing.id()), LocalDate.now())));

        assertEquals(1, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("Bulk add rejects profiles hidden from the property")
    void bulkAddRejectsHiddenPropertyProfile() {
        Tenant hidden = linkedAvailableTenant("079200010007", "Võ Thị N");
        tenantRepo.setHidden(hidden.id(), property.id(), Instant.now(), LocalDemoAuthenticationAdapter.OWNER_1_ID);

        assertThrows(DomainException.class, () -> contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(hidden.id()), LocalDate.now())));

        assertEquals(1, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("Bulk add candidates are limited to available tenants linked to the contract property")
    void availableOccupantsAreScopedAndExcludeCurrentResidents() {
        Tenant linked = linkedAvailableTenant("079200010003", "Võ Văn J");
        Tenant otherProperty = tenantRepo.save(Tenant.create(null, "Phan Văn K", "079200010004", "0900000004", null, null));
        tenantRepo.linkToProperty(otherProperty.id(), UUID.randomUUID(), LocalDemoAuthenticationAdapter.OWNER_1_ID);

        List<Tenant> available = contractService.listAvailableOccupants(activeContract.getId());

        assertEquals(List.of(linked.id()), available.stream().map(Tenant::id).toList());
        assertFalse(available.stream().anyMatch(t -> t.id().equals(primaryTenant.id())));
    }

    private Tenant linkedAvailableTenant(String idCard, String fullName) {
        Tenant tenant = tenantRepo.save(Tenant.create(null, fullName, idCard, "0900000000", null, null));
        tenantRepo.linkToProperty(tenant.id(), property.id(), LocalDemoAuthenticationAdapter.OWNER_1_ID);
        return tenant;
    }

    private User tenantAccount(String username, String email, String fullName) {
        User user = new User(UUID.randomUUID(), username, email, fullName, "0911222333", UserRole.TENANT,
            User.UserStatus.ACTIVE, false, Instant.now());
        return userRepo.save(user);
    }

    private ContractUseCase.CreateContractCommand createContractCommand(String idCard, UUID userId) {
        Room anotherRoom = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"),
            new BigDecimal("3500000"), 2));
        return new ContractUseCase.CreateContractCommand(
            property.id(), anotherRoom.getId(), "Tenant Test", idCard, "0901234000", null, userId,
            new BigDecimal("3500000"), new BigDecimal("3500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
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

        ContractOccupant occupant = contractService.addOccupant(cmd).occupant();

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

        ContractOccupant occupant = contractService.addOccupant(cmd).occupant();
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

        ContractOccupant occ = contractService.addOccupant(cmd).occupant();
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

        ContractOccupant occ = contractService.addOccupant(cmd).occupant();
        assertNotNull(occ);

        Tenant savedTenant = tenantRepo.findByIdentityCardNumber("079200005555").orElseThrow();
        assertNotNull(savedTenant.userId(), "Tenant should be linked to user ID");

        User savedUser = userRepo.findById(savedTenant.userId()).orElseThrow();
        assertEquals("vothid@example.com", savedUser.email());
        assertEquals(UserRole.TENANT, savedUser.role());
    }
}
