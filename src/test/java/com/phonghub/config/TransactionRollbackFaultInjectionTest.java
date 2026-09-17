package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class TransactionRollbackFaultInjectionTest {

    private static InMemoryRoomRepository realRoomRepo;
    private static InMemoryMaintenanceTicketRepository realTicketRepo;
    private static InMemoryContractRepository realContractRepo;
    private static InMemoryTenantRepository realTenantRepo;

    private static boolean failTicketSave;
    private static boolean failContractSave;
    private static boolean failRoomSave;

    private static CurrentUserPort mockCurrentUserPort;
    private static PropertyRepositoryPort mockPropertyRepo;
    private static StaffPropertyAssignmentPort mockAssignmentRepo;

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public PlatformTransactionManager transactionManager() {
            return new SnapshottingTransactionManager(realRoomRepo, realTicketRepo, realContractRepo, realTenantRepo);
        }

        @Bean
        public RoomRepositoryPort roomRepository() {
            return new RoomRepositoryPort() {
                @Override
                public Room save(Room room) {
                    if (failRoomSave) {
                        throw new RuntimeException("Simulated database failure on room update");
                    }
                    return realRoomRepo.save(room);
                }

                @Override
                public Optional<Room> findById(UUID id) {
                    return realRoomRepo.findById(id);
                }

                @Override
                public List<Room> findByPropertyId(UUID propertyId) {
                    return realRoomRepo.findByPropertyId(propertyId);
                }

                @Override
                public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
                    return realRoomRepo.findByPropertyIdAndStatus(propertyId, status);
                }

                @Override
                public Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber) {
                    return realRoomRepo.findByPropertyIdAndRoomNumber(propertyId, roomNumber);
                }

                @Override
                public List<Room> findAllById(java.util.Collection<UUID> ids) {
                    return realRoomRepo.findAllById(ids);
                }

                @Override
                public boolean existsById(UUID id) {
                    return realRoomRepo.existsById(id);
                }
            };
        }

        @Bean
        public MaintenanceTicketRepositoryPort ticketRepository() {
            return new MaintenanceTicketRepositoryPort() {
                @Override
                public MaintenanceTicket save(MaintenanceTicket ticket) {
                    if (failTicketSave) {
                        throw new RuntimeException("Simulated database error: connection lost on ticket insert");
                    }
                    return realTicketRepo.save(ticket);
                }

                @Override
                public Optional<MaintenanceTicket> findById(UUID id) {
                    return realTicketRepo.findById(id);
                }

                @Override
                public List<MaintenanceTicket> findByPropertyId(UUID propertyId) {
                    return realTicketRepo.findByPropertyId(propertyId);
                }

                @Override
                public List<MaintenanceTicket> findByRoomId(UUID roomId) {
                    return realTicketRepo.findByRoomId(roomId);
                }

                @Override
                public List<MaintenanceTicket> findByRoomIdAndStatusNot(UUID roomId, MaintenanceStatus status) {
                    return realTicketRepo.findByRoomIdAndStatusNot(roomId, status);
                }

                @Override
                public List<MaintenanceTicket> findByAssignedTechnicianId(UUID technicianId) {
                    return realTicketRepo.findByAssignedTechnicianId(technicianId);
                }

                @Override
                public List<MaintenanceTicket> findAll() {
                    return realTicketRepo.findAll();
                }
            };
        }

        @Bean
        public ContractRepositoryPort contractRepository() {
            return new ContractRepositoryPort() {
                @Override
                public Contract save(Contract contract) {
                    if (failContractSave) {
                        throw new RuntimeException("Simulated database deadlock on contract update");
                    }
                    return realContractRepo.save(contract);
                }

                @Override
                public Optional<Contract> findById(UUID id) {
                    return realContractRepo.findById(id);
                }

                @Override
                public List<Contract> findByPropertyId(UUID propertyId) {
                    return realContractRepo.findByPropertyId(propertyId);
                }

                @Override
                public List<Contract> findByRoomId(UUID roomId) {
                    return realContractRepo.findByRoomId(roomId);
                }

                @Override
                public Optional<Contract> findActiveByRoomId(UUID roomId) {
                    return realContractRepo.findActiveByRoomId(roomId);
                }

                @Override
                public List<Contract> findByPrimaryTenantId(UUID tenantId) {
                    return realContractRepo.findByPrimaryTenantId(tenantId);
                }

                @Override
                public List<Contract> findByOccupantTenantId(UUID tenantId) {
                    return realContractRepo.findByOccupantTenantId(tenantId);
                }

                @Override
                public List<Contract> findAll() {
                    return realContractRepo.findAll();
                }
            };
        }

        @Bean
        public TenantRepositoryPort tenantRepository() {
            return realTenantRepo;
        }

        @Bean
        public AuthorizationService authorizationService() {
            return new AuthorizationService(mockAssignmentRepo, mockPropertyRepo, realTenantRepo, realContractRepo);
        }

        @Bean
        public MaintenanceUseCase maintenanceUseCase(
            MaintenanceTicketRepositoryPort ticketRepo,
            RoomRepositoryPort roomRepo,
            TenantRepositoryPort tenantRepo,
            AuthorizationService auth
        ) {
            MaintenanceService service = new MaintenanceService(
                ticketRepo, roomRepo, tenantRepo, mockCurrentUserPort, auth
            );
            return new TransactionalMaintenanceUseCase(service);
        }

        @Bean
        public ContractUseCase contractUseCase(
            ContractRepositoryPort contractRepo,
            RoomRepositoryPort roomRepo,
            TenantRepositoryPort tenantRepo,
            AuthorizationService auth
        ) {
            ContractService service = new ContractService(
                contractRepo, roomRepo, mockPropertyRepo, tenantRepo, mockCurrentUserPort, auth
            );
            return new TransactionalContractUseCase(service);
        }
    }

    private AnnotationConfigApplicationContext context;
    private MaintenanceUseCase maintenanceUseCase;
    private ContractUseCase contractUseCase;

    @BeforeEach
    void setUp() {
        realRoomRepo = new InMemoryRoomRepository();
        realTicketRepo = new InMemoryMaintenanceTicketRepository();
        realContractRepo = new InMemoryContractRepository();
        realTenantRepo = new InMemoryTenantRepository();

        failTicketSave = false;
        failContractSave = false;
        failRoomSave = false;

        mockCurrentUserPort = mock(CurrentUserPort.class);
        mockPropertyRepo = mock(PropertyRepositoryPort.class);
        mockAssignmentRepo = mock(StaffPropertyAssignmentPort.class);

        when(mockAssignmentRepo.isUserAssignedToProperty(any(), any())).thenReturn(true);
        when(mockPropertyRepo.existsById(any())).thenReturn(true);

        context = new AnnotationConfigApplicationContext(TestConfig.class);
        maintenanceUseCase = context.getBean(MaintenanceUseCase.class);
        contractUseCase = context.getBean(ContractUseCase.class);
    }

    @Test
    @DisplayName("Maintenance creation rolls back room status and leaves no phantom ticket when ticket insert fails")
    void maintenanceCreateTicketRollsBackWhenTicketPersistenceFailsAfterRoomTransition() {
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(mockCurrentUserPort.getCurrentUser()).thenReturn(admin);

        Room initialRoom = new Room(roomId, propertyId, "101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2, RoomStatus.AVAILABLE, Instant.now(), Instant.now());
        realRoomRepo.save(initialRoom);

        // Inject fault on 2nd write: ticketRepository.save fails
        failTicketSave = true;

        MaintenanceUseCase.CreateMaintenanceTicketCommand cmd = new MaintenanceUseCase.CreateMaintenanceTicketCommand(
            roomId, "Broken AC", "Water leaking", MaintenancePriority.HIGH, true
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> maintenanceUseCase.createTicket(cmd));
        assertTrue(ex.getMessage().contains("connection lost on ticket insert"));

        // REAL STATE VERIFICATION:
        // 1. Room status rolled back to AVAILABLE (not left in MAINTENANCE)
        Room roomAfterRollback = realRoomRepo.findById(roomId).orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, roomAfterRollback.getStatus(), "Room status must roll back to AVAILABLE");

        // 2. No phantom ticket persisted in repository
        assertTrue(realTicketRepo.findAll().isEmpty(), "No phantom maintenance ticket must persist after failure");
    }

    @Test
    @DisplayName("Contract activation rolls back room status and contract status when contract update fails")
    void contractActivationRollsBackWhenContractPersistenceFailsAfterRoomOccupy() {
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(mockCurrentUserPort.getCurrentUser()).thenReturn(admin);

        Contract contract = new Contract(
            contractId, propertyId, roomId, tenantId,
            new BigDecimal("5000000"), new BigDecimal("5000000"),
            LocalDate.now(), LocalDate.now().plusYears(1), 5,
            ContractStatus.DRAFT, Collections.emptyList(), Instant.now(), Instant.now()
        );
        realContractRepo.save(contract);

        Room room = new Room(roomId, propertyId, "201", 2, new BigDecimal("30.0"), new BigDecimal("5000000"), 2, RoomStatus.AVAILABLE, Instant.now(), Instant.now());
        realRoomRepo.save(room);

        // Inject fault on 2nd write: contractRepository.save fails
        failContractSave = true;

        RuntimeException ex = assertThrows(RuntimeException.class, () -> contractUseCase.activateContract(contractId));
        assertTrue(ex.getMessage().contains("deadlock on contract update"));

        // REAL STATE VERIFICATION:
        // 1. Room remains AVAILABLE (not left in OCCUPIED)
        Room roomAfter = realRoomRepo.findById(roomId).orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, roomAfter.getStatus(), "Room must remain AVAILABLE after contract activation failure");

        // 2. Contract remains in DRAFT
        Contract contractAfter = realContractRepo.findById(contractId).orElseThrow();
        assertEquals(ContractStatus.DRAFT, contractAfter.getStatus(), "Contract must remain DRAFT after failure");
    }

    @Test
    @DisplayName("Contract termination rolls back room status and contract status when contract update fails")
    void contractTerminationRollsBackWhenContractPersistenceFailsAfterRoomVacate() {
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(mockCurrentUserPort.getCurrentUser()).thenReturn(admin);

        Contract contract = new Contract(
            contractId, propertyId, roomId, tenantId,
            new BigDecimal("5000000"), new BigDecimal("5000000"),
            LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6), 5,
            ContractStatus.ACTIVE, Collections.emptyList(), Instant.now(), Instant.now()
        );
        realContractRepo.save(contract);

        Room room = new Room(roomId, propertyId, "201", 2, new BigDecimal("30.0"), new BigDecimal("5000000"), 2, RoomStatus.OCCUPIED, Instant.now(), Instant.now());
        realRoomRepo.save(room);

        // Inject fault on 2nd write: contractRepository.save fails
        failContractSave = true;

        RuntimeException ex = assertThrows(RuntimeException.class, () -> contractUseCase.terminateContract(contractId, false));
        assertTrue(ex.getMessage().contains("deadlock on contract update"));

        // REAL STATE VERIFICATION:
        // 1. Room remains OCCUPIED (not left in AVAILABLE)
        Room roomAfter = realRoomRepo.findById(roomId).orElseThrow();
        assertEquals(RoomStatus.OCCUPIED, roomAfter.getStatus(), "Room must remain OCCUPIED after termination failure");

        // 2. Contract remains ACTIVE
        Contract contractAfter = realContractRepo.findById(contractId).orElseThrow();
        assertEquals(ContractStatus.ACTIVE, contractAfter.getStatus(), "Contract must remain ACTIVE after termination failure");
    }

    @Test
    @DisplayName("Maintenance resolve rolls back ticket status when room release fails")
    void maintenanceResolveRollsBackWhenRoomReleaseFailsAfterTicketResolve() {
        UUID propertyId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        CurrentUser technician = new CurrentUser(UUID.randomUUID(), "tech@phonghub.local", "Tech User", UserRole.TECHNICIAN);
        when(mockCurrentUserPort.getCurrentUser()).thenReturn(technician);

        MaintenanceTicket ticket = MaintenanceTicket.create(
            roomId, propertyId, null, "Leaking pipe", "Sink pipe broken", MaintenancePriority.HIGH
        );
        ticket.accept(technician.id());
        realTicketRepo.save(ticket);

        Room room = new Room(roomId, propertyId, "301", 3, new BigDecimal("28.0"), new BigDecimal("4000000"), 2, RoomStatus.MAINTENANCE, Instant.now(), Instant.now());
        realRoomRepo.save(room);

        // Inject fault on 2nd write: roomRepository.save fails when releasing room to AVAILABLE
        failRoomSave = true;

        MaintenanceUseCase.ResolveMaintenanceTicketCommand cmd = new MaintenanceUseCase.ResolveMaintenanceTicketCommand(
            ticket.getId(), "Replaced pipe", new BigDecimal("250000"), true
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> maintenanceUseCase.resolveTicket(cmd));
        assertTrue(ex.getMessage().contains("failure on room update"));

        // REAL STATE VERIFICATION:
        // 1. Ticket remains IN_PROGRESS (not left RESOLVED)
        MaintenanceTicket ticketAfter = realTicketRepo.findById(ticket.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.IN_PROGRESS, ticketAfter.getStatus(), "Ticket must remain IN_PROGRESS after room release failure");

        // 2. Room remains in MAINTENANCE
        Room roomAfter = realRoomRepo.findById(roomId).orElseThrow();
        assertEquals(RoomStatus.MAINTENANCE, roomAfter.getStatus(), "Room must remain in MAINTENANCE after resolution failure");
    }
}