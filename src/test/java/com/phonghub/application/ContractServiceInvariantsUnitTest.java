package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.application.service.RoomService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
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
    private InMemoryMaintenanceTicketRepository ticketRepo;
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
        ticketRepo = new InMemoryMaintenanceTicketRepository();
        assignmentRepo = new InMemoryStaffPropertyAssignmentRepository();

        authAdapter = new LocalDemoAuthenticationAdapter();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        authService = new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);

        contractService = new ContractService(contractRepo, roomRepo, propertyRepo, tenantRepo, authAdapter, authService);
        maintenanceService = new MaintenanceService(ticketRepo, roomRepo, tenantRepo, authAdapter, authService);
        roomService = new RoomService(roomRepo, propertyRepo, ticketRepo, contractRepo, tenantRepo, authAdapter, authService);

        property = propertyRepo.save(new Property(UUID.randomUUID(), "Test Prop", "Test Addr", "", 5, Instant.now()));
        room = roomRepo.save(Room.create(property.id(), "R101", 1, new BigDecimal("25.0"), new BigDecimal("3000000"), 2));
    }

    @Test
    @DisplayName("Activating a contract changes the room status to OCCUPIED")
    void testActivatingContractOccupiesRoom() {
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        Contract contract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), room.getId(), "Tenant Name", "ID123", "0900", "t@example.com", null,
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5
        ));

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).get().getStatus());

        contractService.activateContract(contract.getId());

        Contract updatedContract = contractRepo.findById(contract.getId()).get();
        assertEquals(ContractStatus.ACTIVE, updatedContract.getStatus());

        Room updatedRoom = roomRepo.findById(room.getId()).get();
        assertEquals(RoomStatus.OCCUPIED, updatedRoom.getStatus());
    }

    @Test
    @DisplayName("Reject duplicate active contract: cannot create or activate second contract for occupied room")
    void testCannotHaveTwoActiveContractsForOneRoom() {
        Contract contract1 = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), room.getId(), "Tenant 1", "ID1", "0901", "t1@example.com", null,
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5
        ));
        contractService.activateContract(contract1.getId());

        // Creating another contract for this room must fail
        assertThrows(DuplicateActiveContractException.class, () ->
            contractService.createContract(new ContractUseCase.CreateContractCommand(
                property.id(), room.getId(), "Tenant 2", "ID2", "0902", "t2@example.com", null,
                new BigDecimal("3000000"), new BigDecimal("3000000"),
                LocalDate.now(), LocalDate.now().plusMonths(6), 5
            ))
        );
    }

    @Test
    @DisplayName("Ending contract changes room to AVAILABLE when no maintenance required")
    void testEndingContractWithoutMaintenance() {
        Contract contract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), room.getId(), "Tenant 1", "ID1", "0901", "t1@example.com", null,
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5
        ));
        contractService.activateContract(contract.getId());

        contractService.terminateContract(contract.getId(), false);

        assertEquals(ContractStatus.TERMINATED, contractRepo.findById(contract.getId()).get().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(room.getId()).get().getStatus());
    }

    private Contract draft(Room targetRoom, String idCard) {
        return contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), targetRoom.getId(), "Tenant " + idCard, idCard, "0900", null, null,
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
        contractService.activateContract(first.getId());

        assertThrows(DuplicateActiveContractException.class,
            () -> contractService.activateContract(second.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepo.findById(secondRoom.getId()).orElseThrow().getStatus());
    }

    @Test
    void activationRejectsPrimaryTenantAlreadyResidingAsCoOccupant() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID2");
        first = contractService.activateContract(first.getId());
        first.addOccupant(second.getPrimaryTenantId(), false, LocalDate.now());
        contractRepo.save(first);

        assertThrows(DuplicateActiveContractException.class,
            () -> contractService.activateContract(second.getId()));
        assertEquals(ContractStatus.DRAFT, contractRepo.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void terminatedContractDoesNotPreventActivationForSameTenant() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID1");
        contractService.activateContract(first.getId());
        contractService.terminateContract(first.getId(), false);

        Contract activated = contractService.activateContract(second.getId());
        assertEquals(ContractStatus.ACTIVE, activated.getStatus());
    }

    @Test
    void checkedOutCoOccupantCanActivateAnotherContract() {
        Contract first = draft(room, "ID1");
        Contract second = draft(anotherRoom(), "ID2");
        first = contractService.activateContract(first.getId());
        first.addOccupant(second.getPrimaryTenantId(), false, LocalDate.now());
        first.checkOutOccupant(second.getPrimaryTenantId(), LocalDate.now());
        contractRepo.save(first);

        Contract activated = contractService.activateContract(second.getId());
        assertEquals(ContractStatus.ACTIVE, activated.getStatus());
    }

    @Test
    @DisplayName("Ending contract changes room to MAINTENANCE when maintenance is explicitly required")
    void testEndingContractWithMaintenance() {
        Contract contract = contractService.createContract(new ContractUseCase.CreateContractCommand(
            property.id(), room.getId(), "Tenant 1", "ID1", "0901", "t1@example.com", null,
            new BigDecimal("3000000"), new BigDecimal("3000000"),
            LocalDate.now(), LocalDate.now().plusMonths(6), 5
        ));
        contractService.activateContract(contract.getId());

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
                pendingProp.id(), pendingRoom.getId(), "Tenant Name", "ID999", "0900", "t@example.com", null,
                new BigDecimal("3000000"), new BigDecimal("3000000"),
                LocalDate.now(), LocalDate.now().plusMonths(6), 5
            ))
        );
        assertTrue(ex.getMessage().contains("chưa được duyệt"));
    }
}
