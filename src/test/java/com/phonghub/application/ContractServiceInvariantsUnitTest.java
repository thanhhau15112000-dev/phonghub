package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.application.service.RoomService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.InvalidContractStateException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
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

import static org.junit.jupiter.api.Assertions.*;

class ContractServiceInvariantsUnitTest {

    private InMemoryPropertyRepository propertyRepo;
    private InMemoryRoomRepository roomRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private InMemoryMaintenanceTicketRepository ticketRepo;
    private InMemoryHandoverRepository handoverRepo;
    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private LocalDemoAuthenticationAdapter authAdapter;
    private AuthorizationService authService;

    private ContractService contractService;
    private MaintenanceService maintenanceService;
    private RoomService roomService;

    private Property property;
    private Room room;

    @BeforeEach
    void setUp() {
        propertyRepo = new InMemoryPropertyRepository();
        roomRepo = new InMemoryRoomRepository();
        contractRepo = new InMemoryContractRepository();
        tenantRepo = new InMemoryTenantRepository();
        userRepo = new InMemoryUserRepository();
        ticketRepo = new InMemoryMaintenanceTicketRepository();
        handoverRepo = new InMemoryHandoverRepository();
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();

        authAdapter = new LocalDemoAuthenticationAdapter();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);

        contractService = new ContractService(contractRepo, roomRepo, propertyRepo, tenantRepo, userRepo, null,
            authAdapter, authService, null, handoverRepo);
        maintenanceService = new MaintenanceService(ticketRepo, roomRepo, tenantRepo, authAdapter, authService);
        roomService = new RoomService(roomRepo, propertyRepo, ticketRepo, contractRepo, tenantRepo, authAdapter, authService);

        property = propertyRepo.save(new Property(UUID.randomUUID(), "Test Prop", "Test Addr", "", 5, Instant.now()));
        room = roomRepo.save(Room.create(property.id(), "R101", 1, new BigDecimal("25.0"), new BigDecimal("3000000"), 2));
    }

    private void approveCheckIn(UUID contractId) {
        Handover draft = Handover.draft(contractId, HandoverType.CHECK_IN, BigDecimal.ZERO, BigDecimal.ZERO,
            "Phòng đã kiểm tra", null, authAdapter.getCurrentUser().id());
        handoverRepo.save(draft.send("terms-hash", null, null, authAdapter.getCurrentUser().id())
            .tenantConfirm(authAdapter.getCurrentUser().id()).approve(authAdapter.getCurrentUser().id()));
    }

    private Contract activateWithApprovedCheckIn(UUID contractId) {
        approveCheckIn(contractId);
        return contractService.activateContract(contractId);
    }

    @Test
    @DisplayName("Activating a contract changes the room status to OCCUPIED")
    void testActivatingContractOccupiesRoom() {
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        Contract contract = draft(room, "ID123");

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).get().getStatus());

        activateWithApprovedCheckIn(contract.getId());

        Contract updatedContract = contractRepo.findById(contract.getId()).get();
        assertEquals(ContractStatus.ACTIVE, updatedContract.getStatus());

        Room updatedRoom = roomRepo.findById(room.getId()).get();
        assertEquals(RoomStatus.OCCUPIED, updatedRoom.getStatus());
    }

    @Test
    void activationRejectsTenantWhoseAccountWasDeactivatedAfterDraftCreation() {
        Contract contract = draft(room, "ID123");
        approveCheckIn(contract.getId());
        Tenant tenant = tenantRepo.findById(contract.getPrimaryTenantId()).orElseThrow();
        User account = userRepo.findById(tenant.userId()).orElseThrow();
        userRepo.save(new User(account.id(), account.username(), account.email(), account.fullName(), account.phone(),
            UserRole.TENANT, User.UserStatus.SUSPENDED, account.mustChangePassword(), account.createdAt()));

        assertThrows(DomainException.class, () -> contractService.activateContract(contract.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(contract.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void activationRequiresTenantConfirmedAndApprovedCheckIn() {
        Contract contract = draft(room, "NO-CHECKIN");

        assertThrows(InvalidContractStateException.class, () -> contractService.activateContract(contract.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(contract.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void primaryTenantSearchUsesCccdSuffixInsidePropertyAndNotPhoneDigits() {
        Contract contract = draft(room, "123456789");
        Tenant tenant = tenantRepo.findById(contract.getPrimaryTenantId()).orElseThrow();

        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), "6789").stream()
            .anyMatch(candidate -> candidate.id().equals(tenant.id())));
        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), "0901234567").stream()
            .noneMatch(candidate -> candidate.id().equals(tenant.id())));
    }

    @Test
    void adminCreatedUnassignedTenantIsAvailableByNameOrExactCccdButNotSuffix() {
        UUID tenantUserId = UUID.randomUUID();
        User tenantUser = userRepo.save(new User(tenantUserId, "globaltenant", "global@test.local", "Global Tenant",
            "0911111222", UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()));
        Tenant globalTenant = tenantRepo.save(Tenant.create(tenantUser.id(), "Global Tenant", "123456789",
            tenantUser.phone(), tenantUser.email(), null));

        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), null).stream()
            .anyMatch(candidate -> candidate.id().equals(globalTenant.id())));
        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), "Global").stream()
            .anyMatch(candidate -> candidate.id().equals(globalTenant.id())));
        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), "6789").stream()
            .noneMatch(candidate -> candidate.id().equals(globalTenant.id())));
        assertTrue(contractService.listAvailablePrimaryTenants(property.id(), "123456789").stream()
            .anyMatch(candidate -> candidate.id().equals(globalTenant.id())));
    }

    @Test
    @DisplayName("Reject duplicate active contract: cannot create or activate second contract for occupied room")
    void testCannotHaveTwoActiveContractsForOneRoom() {
        Contract contract1 = draft(room, "ID1");
        activateWithApprovedCheckIn(contract1.getId());

        // Creating another contract for this room must fail
        assertThrows(DuplicateActiveContractException.class, () ->
            draft(room, "ID2")
        );
    }

    @Test
    @DisplayName("Ending contract changes room to AVAILABLE when no maintenance required")
    void testEndingContractWithoutMaintenance() {
        Contract contract = draft(room, "ID1");
        activateWithApprovedCheckIn(contract.getId());

        contractService.terminateContract(contract.getId(), false);

        assertEquals(ContractStatus.TERMINATED, contractRepo.findById(contract.getId()).get().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).get().getStatus());
    }

    private Contract draft(Room targetRoom, String idCard) {
        Tenant tenant = tenantRepo.findByIdentityCardNumber(idCard).orElseGet(() -> {
            String suffix = Integer.toString(userRepo.findAll().size() + 1);
            UUID userId = UUID.randomUUID();
            userRepo.save(new User(userId, "tenant" + suffix, "tenant" + suffix + "@test.local",
                "Tenant " + idCard, "0901234567", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));
            return tenantRepo.save(Tenant.create(userId, "Tenant " + idCard, idCard, "0901234567",
                "tenant" + suffix + "@test.local", null));
        });
        return contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), targetRoom.getId(), tenant.id(),
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5
        ));
    }

    private Room anotherRoom() {
        return roomRepo.save(Room.create(property.id(), "R102", 1,
            new BigDecimal("25.0"), new BigDecimal("3000000"), 2));
    }

    @Test
    void activationRejectsPrimaryTenantAlreadyActiveInAnotherRoom() {
        Contract first = draft(room, "ID1");
        Room secondRoom = anotherRoom();
        Contract second = draft(secondRoom, "ID1");
        activateWithApprovedCheckIn(first.getId());

        assertThrows(DuplicateActiveContractException.class,
            () -> activateWithApprovedCheckIn(second.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(secondRoom.getId()).orElseThrow().getStatus());
    }

    @Test
    void activationRejectsPrimaryTenantAlreadyResidingAsCoOccupant() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID2");
        first = activateWithApprovedCheckIn(first.getId());
        first.addOccupant(second.getPrimaryTenantId(), false, LocalDate.now());
        contractRepo.save(first);

        assertThrows(DuplicateActiveContractException.class,
            () -> activateWithApprovedCheckIn(second.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void terminatedContractDoesNotPreventActivationForSameTenant() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID1");
        activateWithApprovedCheckIn(first.getId());
        contractService.terminateContract(first.getId(), false);

        Contract activated = activateWithApprovedCheckIn(second.getId());
        assertEquals(ContractStatus.ACTIVE, activated.getStatus());
    }

    @Test
    void checkedOutCoOccupantCanActivateAnotherContract() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID2");
        first = activateWithApprovedCheckIn(first.getId());
        first.addOccupant(second.getPrimaryTenantId(), false, LocalDate.now());
        first.checkOutOccupant(second.getPrimaryTenantId(), LocalDate.now());
        contractRepo.save(first);

        Contract activated = activateWithApprovedCheckIn(second.getId());
        assertEquals(ContractStatus.ACTIVE, activated.getStatus());
    }

    @Test
    @DisplayName("Ending contract changes room to MAINTENANCE when maintenance is explicitly required")
    void testEndingContractWithMaintenance() {
        Contract contract = draft(room, "ID1");
        activateWithApprovedCheckIn(contract.getId());

        contractService.terminateContract(contract.getId(), true);

        assertEquals(ContractStatus.TERMINATED, contractRepo.findById(contract.getId()).get().getStatus());
        assertEquals(RoomStatus.MAINTENANCE, roomRepo.findById(room.getId()).get().getStatus());
    }

    @Test
    @DisplayName("Cannot release room from MAINTENANCE if unresolved tickets remain")
    void testCannotReleaseRoomWithUnresolvedTickets() {
        room.putUnderMaintenance();
        roomRepo.save(room);

        // Create open ticket
        MaintenanceTicket ticket = maintenanceService.createTicket(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
            room.getId(), "Broken window", "Glass shattered", MaintenancePriority.HIGH, false
        ));

        // Attempt to change room status directly to AVAILABLE
        assertThrows(InvalidRoomStateException.class, () ->
            roomService.changeRoomStatus(room.getId(), RoomStatus.AVAILABLE)
        );

        // Resolve ticket
        maintenanceService.acceptTicket(ticket.getId());
        maintenanceService.resolveTicket(new MaintenanceUseCase.ResolveMaintenanceTicketCommand(
            ticket.getId(), "Replaced glass pane", new BigDecimal("200000"), true
        ));

        // Now room is back to AVAILABLE
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).get().getStatus());
    }

    @Test
    @DisplayName("Creating contract on unverified (PENDING or REJECTED) property throws DomainException")
    void testCreatingContractOnUnverifiedPropertyThrowsDomainException() {
        Property pendingProp = propertyRepo.save(new Property(
            UUID.randomUUID(), "Pending Prop", "Addr", "", 5, null, PropertyApprovalStatus.PENDING, null, Instant.now()
        ));
        Room pendingRoom = roomRepo.save(Room.create(pendingProp.id(), "P101", 1, new BigDecimal("25.0"), new BigDecimal("3000000"), 2));

        DomainException ex = assertThrows(DomainException.class, () ->
            contractService.createContract(new ContractUseCase.CreateContractCommand(
                pendingProp.id(), pendingRoom.getId(), UUID.randomUUID(), new BigDecimal("3000000"),
                new BigDecimal("3000000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5))
        );
        assertTrue(ex.getMessage().contains("chưa được duyệt"));
    }
}
