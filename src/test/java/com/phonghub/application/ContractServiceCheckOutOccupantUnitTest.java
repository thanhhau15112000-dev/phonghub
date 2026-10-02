package com.phonghub.application;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.PrimaryOccupantRemovalException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ContractServiceCheckOutOccupantUnitTest {

    private InMemoryPropertyRepository propertyRepo;
    private InMemoryRoomRepository roomRepo;
    private InMemoryContractRepository contractRepo;
    private InMemoryTenantRepository tenantRepo;
    private InMemoryUserRepository userRepo;
    private InMemoryStaffPropertyAssignmentRepository assignmentRepo;
    private LocalDemoAuthenticationAdapter authAdapter;
    private AuthorizationService authService;
    private AuditPort auditPort;
    private ContractService contractService;

    private Property property;
    private Room room;
    private Tenant primaryTenant;
    private Tenant coTenant;
    private Contract activeContract;
    private ContractOccupant coOccupant;
    private List<AuditPort.AuditEvent> recordedEvents;

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
        
        recordedEvents = new ArrayList<>();
        auditPort = event -> recordedEvents.add(event);

        contractService = new ContractService(
            contractRepo,
            roomRepo,
            propertyRepo,
            tenantRepo,
            userRepo,
            null,
            authAdapter,
            authService,
            auditPort
        );

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

        room = roomRepo.save(Room.create(property.id(), "P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 3));

        primaryTenant = tenantRepo.save(Tenant.create(
            LocalDemoAuthenticationAdapter.TENANT_1_ID,
            "Nguyễn Văn A",
            "079200001111",
            "0901234567",
            "primary@phonghub.local",
            "TP.HCM"
        ));

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
            LocalDate.now().minusDays(10),
            LocalDate.now().plusMonths(6),
            5
        ));
        contractService.activateContract(activeContract.getId());

        // Add co-tenant
        coTenant = tenantRepo.save(Tenant.create(
            null,
            "Trần Thị B",
            "079200002222",
            "0909888999",
            "b@example.com",
            "Đồng Nai"
        ));

        coOccupant = contractService.addOccupant(new ContractUseCase.AddOccupantCommand(
            activeContract.getId(),
            coTenant.fullName(),
            coTenant.identityCardNumber(),
            coTenant.phone(),
            coTenant.email(),
            coTenant.permanentAddress(),
            LocalDate.now().minusDays(5),
            false
        ));
        activeContract = contractRepo.findById(activeContract.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Owner checks out co-occupant successfully and records audit event")
    void testOwnerChecksOutOccupantSuccessfully() {
        LocalDate checkOutDate = LocalDate.now();
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            checkOutDate
        );

        ContractOccupant checkedOut = contractService.checkOutOccupant(cmd);

        assertNotNull(checkedOut);
        assertEquals(checkOutDate, checkedOut.checkOutDate());

        Contract updated = contractRepo.findById(activeContract.getId()).orElseThrow();
        ContractOccupant savedOccupant = updated.getOccupants().stream()
            .filter(o -> o.tenantId().equals(coTenant.id()))
            .findFirst()
            .orElseThrow();
        assertEquals(checkOutDate, savedOccupant.checkOutDate());

        // Verify audit event
        assertEquals(1, recordedEvents.size());
        AuditPort.AuditEvent event = recordedEvents.getFirst();
        assertEquals("OCCUPANT_CHECKOUT", event.action());
        assertEquals(LocalDemoAuthenticationAdapter.OWNER_1_ID, event.actorId());
        assertEquals("CONTRACT_OCCUPANT", event.targetType());
        assertEquals(savedOccupant.id().toString(), event.targetId());
        assertEquals(activeContract.getId().toString(), event.details().get("contractId"));
        assertEquals(coTenant.id().toString(), event.details().get("tenantId"));
        assertEquals(checkOutDate.toString(), event.details().get("checkOutDate"));
    }

    @Test
    @DisplayName("Admin checks out co-occupant successfully")
    void testAdminChecksOutOccupantSuccessfully() {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            LocalDate.now()
        );

        ContractOccupant checkedOut = contractService.checkOutOccupant(cmd);
        assertNotNull(checkedOut.checkOutDate());
    }

    @Test
    @DisplayName("Staff or another Owner checking out occupant throws UnauthorizedPropertyAccessException (403)")
    void testUnauthorizedUserThrowsForbidden() {
        // Staff user
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            LocalDate.now()
        );

        assertThrows(UnauthorizedPropertyAccessException.class, () -> contractService.checkOutOccupant(cmd));

        // Different Owner
        CurrentUser otherOwner = new CurrentUser(
            UUID.randomUUID(),
            "other@phonghub.local",
            "Chủ Khác",
            UserRole.OWNER,
            false
        );
        authAdapter.setCurrentUser(otherOwner);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Checking out primary occupant throws PrimaryOccupantRemovalException (409)")
    void testCannotCheckOutPrimaryOccupant() {
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            primaryTenant.id(),
            LocalDate.now()
        );

        assertThrows(PrimaryOccupantRemovalException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Checking out occupant not in contract throws DomainException")
    void testCannotCheckOutNonExistentOccupant() {
        UUID unknownTenantId = UUID.randomUUID();
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            unknownTenantId,
            LocalDate.now()
        );

        assertThrows(DomainException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Checking out already checked-out occupant throws DomainException")
    void testCannotCheckOutAlreadyCheckedOutOccupant() {
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            LocalDate.now()
        );
        contractService.checkOutOccupant(cmd);

        assertThrows(DomainException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Checkout date before check-in date throws IllegalArgumentException (400)")
    void testCheckOutBeforeCheckInDateThrowsIllegalArgument() {
        LocalDate beforeCheckIn = coOccupant.checkInDate().minusDays(1);
        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            beforeCheckIn
        );

        assertThrows(IllegalArgumentException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Cannot check out occupant from non-active contract")
    void testCannotCheckOutFromTerminatedContract() {
        contractService.terminateContract(activeContract.getId(), false);

        ContractUseCase.CheckOutOccupantCommand cmd = new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            LocalDate.now()
        );

        assertThrows(DomainException.class, () -> contractService.checkOutOccupant(cmd));
    }

    @Test
    @DisplayName("Checked-out occupant loses access: findByOccupantTenantId no longer returns the contract")
    void testCheckedOutOccupantLosesAccess() {
        // Before checkout, contract is found for co-tenant
        List<Contract> beforeList = contractRepo.findByOccupantTenantId(coTenant.id());
        assertEquals(1, beforeList.size());

        // Perform checkout
        contractService.checkOutOccupant(new ContractUseCase.CheckOutOccupantCommand(
            activeContract.getId(),
            coTenant.id(),
            LocalDate.now()
        ));

        // After checkout, contract is no longer found for co-tenant
        List<Contract> afterList = contractRepo.findByOccupantTenantId(coTenant.id());
        assertTrue(afterList.isEmpty());
    }
}
