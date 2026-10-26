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
        when(identityProvider.adminCreateUser(anyString(), anyString())).thenAnswer(call -> UUID.randomUUID());

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
        userRepo.save(new User(LocalDemoAuthenticationAdapter.TENANT_1_ID, "primary@phonghub.local", "Nguyễn Văn A",
            "0901234567", UserRole.TENANT, User.UserStatus.ACTIVE, Instant.now()));
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
            primaryTenant.id(),
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
    @DisplayName("Contract selects an existing account-backed tenant profile")
    void selectedTenantAccountLinksToExistingProfile() {
        Tenant unlinked = tenantRepo.save(Tenant.create(
            null, "Lê Thị D", "079200004444", "0911222444", "d@example.com", null));
        User account = tenantAccount("tenant-d", "d@example.com", "Lê Thị D");
        Tenant linked = tenantRepo.save(new Tenant(unlinked.id(), account.id(), unlinked.fullName(),
            unlinked.identityCardNumber(), unlinked.phone(), unlinked.email(), unlinked.permanentAddress(),
            unlinked.createdAt()));

        Contract contract = contractService.createContract(createContractCommand(linked.id()));

        assertEquals(linked.id(), contract.getPrimaryTenantId());
        assertEquals(account.id(), tenantRepo.findById(linked.id()).orElseThrow().userId());
    }

    @Test
    @DisplayName("Contract creation rejects an account without a tenant profile")
    void selectedTenantAccountRequiresTenantProfile() {
        User account = tenantAccount("tenant-new", "new-tenant@example.com", "Đỗ Thị P");

        assertThrows(DomainException.class, () -> contractService.createContract(
            createContractCommand(tenantRepo.save(Tenant.create(null, "Đỗ Thị P", "079200010008", "0900000008",
                "new-tenant@example.com", null)).id())));
        assertTrue(tenantRepo.findByUserId(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Contract creation rejects an account that is not a tenant")
    void createContractRejectsNonTenantAccount() {
        User admin = new User(UUID.randomUUID(), "admin-user", "admin-user@example.com", "Admin User", null,
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now());
        userRepo.save(admin);

        Tenant profile = tenantRepo.save(Tenant.create(admin.id(), "Admin User", "079200005555", "0900000005",
            "admin-user@example.com", null));
        assertThrows(DomainException.class,
            () -> contractService.createContract(createContractCommand(profile.id())));
    }

    @Test
    @DisplayName("Contract creation rejects an account already linked to a different tenant profile")
    void createContractRejectsAccountLinkedToDifferentTenant() {
        User account = tenantAccount("tenant-e", "e@example.com", "Ngô Thị E");
        Tenant linked = tenantRepo.save(Tenant.create(
            account.id(), "Ngô Thị E", "079200006666", "0911222666", "e@example.com", null));

        Tenant profile = tenantRepo.save(Tenant.create(null, "Khác", "079200007777", "0900000007", null, null));
        assertThrows(DomainException.class,
            () -> contractService.createContract(createContractCommand(profile.id())));
        assertEquals(linked.id(), tenantRepo.findByUserId(account.id()).orElseThrow().id());
    }

    @Test
    @DisplayName("Selecting a tenant profile keeps its existing account link")
    void contractUsesExistingAccountBackedProfile() {
        User existingAccount = tenantAccount("tenant-existing", "existing@example.com", "Lý Thị Q");
        Tenant linked = tenantRepo.save(Tenant.create(
            existingAccount.id(), "Lý Thị Q", "079200010009", "0911222999", "existing@example.com", null));

        Contract contract = contractService.createContract(createContractCommand(linked.id()));
        assertEquals(linked.id(), contract.getPrimaryTenantId());
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
        User user = tenantAccount("tenant-" + idCard.substring(idCard.length() - 4), idCard + "@test.local", fullName);
        Tenant tenant = tenantRepo.save(Tenant.create(user.id(), fullName, idCard, "0900000000", user.email(), null));
        tenantRepo.linkToProperty(tenant.id(), property.id(), LocalDemoAuthenticationAdapter.OWNER_1_ID);
        return tenant;
    }

    private User tenantAccount(String username, String email, String fullName) {
        User user = new User(UUID.randomUUID(), username, email, fullName, "0911222333", UserRole.TENANT,
            User.UserStatus.ACTIVE, false, Instant.now());
        return userRepo.save(user);
    }

    private ContractUseCase.CreateContractCommand createContractCommand(UUID tenantId) {
        Room anotherRoom = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"),
            new BigDecimal("3500000"), 2));
        return new ContractUseCase.CreateContractCommand(
            property.id(), anotherRoom.getId(), tenantId,
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
            LocalDate.now());

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
            "c@example.com",
            null,
            LocalDate.now());

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
            "b@example.com",
            null,
            LocalDate.now());

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
            "b@example.com",
            null,
            LocalDate.now());

        assertThrows(InvalidPropertyStatusException.class, () -> contractService.addOccupant(cmd));
    }

    @Test
    @DisplayName("Inactive contract throws DomainException")
    void testInactiveContractThrowsDomainException() {
        Room room2 = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2));
        Contract draftContract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(),
            room2.getId(),
            linkedAvailableTenant("079200009999", "Tenant Draft").id(),
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
            LocalDate.now());

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
            "b@example.com",
            null,
            LocalDate.now()));

        // Add 2nd additional occupant (total 3 > max 2) -> must throw 409
        ContractUseCase.AddOccupantCommand thirdCmd = new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            "Lê Văn C",
            "079200003333",
            "0911222333",
            "c@example.com",
            null,
            LocalDate.now());

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
            LocalDate.now());

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
            LocalDate.now());

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
            LocalDate.now());

        ContractOccupant occ = contractService.addOccupant(cmd).occupant();
        assertNotNull(occ);

        Tenant savedTenant = tenantRepo.findByIdentityCardNumber("079200005555").orElseThrow();
        assertNotNull(savedTenant.userId(), "Tenant should be linked to user ID");

        User savedUser = userRepo.findById(savedTenant.userId()).orElseThrow();
        assertEquals("vothid@example.com", savedUser.email());
        assertEquals(UserRole.TENANT, savedUser.role());
    }

    // ---- Thêm người ở: một hàm đếm chỗ, kiểm tra hết trước khi ghi, lỗi nêu tên người, tự gắn nhà trọ ----

    private ContractUseCase.AddOccupantCommand newPersonRow(String idCard, String fullName) {
        return new ContractUseCase.AddOccupantCommand(activeContract.getId(), fullName, idCard, "0900000001",
            idCard + "@test.local", null, LocalDate.now());
    }

    private ContractUseCase.AddOccupantCommand profileRow(UUID tenantId) {
        return new ContractUseCase.AddOccupantCommand(activeContract.getId(), null, null, null, null, null,
            LocalDate.now(), tenantId, null);
    }

    @Test
    @DisplayName("Batch add counts the primary tenant too when a legacy contract has no occupant rows")
    void batchAddCountsPrimaryTenantForLegacyContract() {
        Contract legacy = new Contract(
            activeContract.getId(), activeContract.getPropertyId(), activeContract.getRoomId(),
            activeContract.getPrimaryTenantId(), activeContract.getDepositAmount(), activeContract.getRentAmount(),
            activeContract.getStartDate(), activeContract.getEndDate(), activeContract.getPaymentDay(),
            ContractStatus.ACTIVE, List.of(), activeContract.getCreatedAt(), activeContract.getUpdatedAt());
        contractRepo.save(legacy);

        // Phòng chứa 2 người, người thuê chính đã chiếm 1 chỗ: thêm 2 người phải bị từ chối
        assertThrows(InvalidRoomCapacityException.class, () -> contractService.addOccupants(
            new ContractUseCase.AddOccupantsCommand(activeContract.getId(),
                List.of(newPersonRow("079200020001", "Hoàng Văn P"), newPersonRow("079200020002", "Mai Thị Q")))));

        assertTrue(tenantRepo.findByIdentityCardNumber("079200020001").isEmpty());
        assertTrue(contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().isEmpty());
    }

    @Test
    @DisplayName("Batch add validates every row before writing: one invalid row leaves no new profile behind")
    void batchAddWritesNothingWhenALaterRowIsInvalid() {
        room.updateInfo("P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 3);
        roomRepo.save(room);
        Tenant residing = linkedAvailableTenant("079200020003", "Nguyễn Thị M");
        Room anotherRoom = roomRepo.save(Room.create(property.id(), "P102", 1, new BigDecimal("25.0"),
            new BigDecimal("3500000"), 2));
        Contract otherContract = Contract.create(property.id(), anotherRoom.getId(), residing.id(),
            new BigDecimal("3500000"), new BigDecimal("3500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
        otherContract.activate();
        contractRepo.save(otherContract);

        // Dòng 1 là người mới (sẽ tạo hồ sơ), dòng 2 đang ở hợp đồng khác: không dòng nào được ghi
        DuplicateActiveContractException error = assertThrows(DuplicateActiveContractException.class,
            () -> contractService.addOccupants(new ContractUseCase.AddOccupantsCommand(activeContract.getId(),
                List.of(newPersonRow("079200020004", "Người Mới Một"), profileRow(residing.id())))));

        assertTrue(error.getMessage().contains("Nguyễn Thị M"), "lỗi nêu tên người vi phạm");
        assertTrue(tenantRepo.findByIdentityCardNumber("079200020004").isEmpty(), "dòng 1 không được tạo hồ sơ");
        assertEquals(1, contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("Bulk add of existing profiles names the person who is already in the contract")
    void bulkAddErrorNamesThePerson() {
        DomainException error = assertThrows(DomainException.class, () -> contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(
                activeContract.getId(), List.of(primaryTenant.id()), LocalDate.now())));

        assertTrue(error.getMessage().contains(primaryTenant.fullName()));
    }

    @Test
    @DisplayName("A profile picked from another property is linked to this property when it is added")
    void profileFromAnotherPropertyIsLinkedToThisProperty() {
        Property other = propertyRepo.save(new Property(UUID.randomUUID(), "Nhà Trọ Khác", "9 Đường B", "", 5,
            LocalDemoAuthenticationAdapter.OWNER_1_ID, PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        User strangerAccount = tenantAccount("tenant-0005", "079200020005@test.local", "Đặng Văn T");
        Tenant stranger = tenantRepo.save(Tenant.create(strangerAccount.id(), "Đặng Văn T", "079200020005",
            "0900000005", strangerAccount.email(), null));
        tenantRepo.linkToProperty(stranger.id(), other.id(), LocalDemoAuthenticationAdapter.OWNER_1_ID);
        assertFalse(tenantRepo.findLinkedPropertyIds(stranger.id()).contains(property.id()));

        contractService.addOccupants(new ContractUseCase.AddOccupantsCommand(
            activeContract.getId(), List.of(profileRow(stranger.id()))));

        assertTrue(tenantRepo.findLinkedPropertyIds(stranger.id()).contains(property.id()));
        assertTrue(contractRepo.findById(activeContract.getId()).orElseThrow().getOccupants().stream()
            .anyMatch(occupant -> occupant.tenantId().equals(stranger.id())));
    }

    @Test
    @DisplayName("Bulk add uses the check-in date given for the whole batch")
    void bulkAddUsesTheGivenCheckInDate() {
        Tenant tenant = linkedAvailableTenant("079200020006", "Lương Thị U");
        LocalDate checkIn = LocalDate.now().plusDays(1);

        List<ContractOccupant> added = contractService.addExistingOccupants(
            new ContractUseCase.AddExistingOccupantsCommand(activeContract.getId(), List.of(tenant.id()), checkIn));

        assertEquals(checkIn, added.get(0).checkInDate());
    }
}
