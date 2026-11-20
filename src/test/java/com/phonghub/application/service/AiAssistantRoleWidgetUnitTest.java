package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.AiAssistantUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.AiCompletionPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FaqSearchPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.config.AiAssistantProperties;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiAssistantRoleWidgetUnitTest {

    private final InvoiceUseCase invoiceUseCase = mock(InvoiceUseCase.class);
    private final ContractUseCase contractUseCase = mock(ContractUseCase.class);
    private final InvoiceRepositoryPort invoiceRepository = mock(InvoiceRepositoryPort.class);
    private final ContractRepositoryPort contractRepository = mock(ContractRepositoryPort.class);
    private final RoomRepositoryPort roomRepository = mock(RoomRepositoryPort.class);
    private final MaintenanceTicketRepositoryPort ticketRepository = mock(MaintenanceTicketRepositoryPort.class);
    private final PropertyRepositoryPort propertyRepository = mock(PropertyRepositoryPort.class);
    private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
    private final StaffPropertyAssignmentPort assignmentPort = mock(StaffPropertyAssignmentPort.class);
    private final TenantRepositoryPort tenantRepository = mock(TenantRepositoryPort.class);
    private final AiCompletionPort completionPort = mock(AiCompletionPort.class);
    private final FaqSearchPort faqSearchPort = mock(FaqSearchPort.class);

    private AiAssistantService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AiAssistantService(invoiceUseCase, contractUseCase, invoiceRepository, contractRepository,
            roomRepository, ticketRepository, propertyRepository, currentUserPort,
            new AuthorizationService(assignmentPort, propertyRepository, tenantRepository, contractRepository),
            completionPort, faqSearchPort, new AiAssistantProperties(), mapper,
            Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(completionPort.complete(anyString(), anyString())).thenReturn("OK");
    }

    @Test
    void adminWidgetAndAskExcludeAllMoneyAndContractData() {
        UUID adminId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(adminId, "admin@x", "Admin", UserRole.ADMIN));
        UUID propId = UUID.randomUUID();
        Property prop = new Property(propId, "Nhà A", "Q1", "Mô tả", 2, UUID.randomUUID(),
            PropertyApprovalStatus.PENDING, null, Instant.now());
        when(propertyRepository.findAll()).thenReturn(List.of(prop));
        when(roomRepository.findByPropertyId(propId)).thenReturn(List.of(
            new Room(UUID.randomUUID(), propId, "101", 1, new BigDecimal("20"), new BigDecimal("3000000"), 2, RoomStatus.AVAILABLE, Instant.now(), Instant.now()),
            new Room(UUID.randomUUID(), propId, "102", 1, new BigDecimal("20"), new BigDecimal("3500000"), 2, RoomStatus.OCCUPIED, Instant.now(), Instant.now())
        ));

        AiAssistantUseCase.AiWidgetContext ctx = service.getWidgetContext();
        assertEquals("ADMIN", ctx.role());
        assertFalse(ctx.quickPrompts().isEmpty());

        service.askAssistant("AUTO", null, "Tổng quan nhà trọ?");
        JsonNode facts = captureFacts();
        assertEquals("ADMIN", facts.path("viewerRole").asString());
        assertEquals(1, facts.path("platformProperties").path("pendingApproval").asInt());
        assertEquals(2, facts.path("platformRooms").path("total").asInt());
        assertEquals("50.0%", facts.path("platformRooms").path("occupancyRate").asString());
        assertTrue(facts.has("moneyHidden"));
        assertFalse(facts.has("unpaidInvoices"));

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.askAssistant("PROPERTY", propId, "Công nợ?"));
    }

    @Test
    void technicianWidgetAndAskExposeOnlyAssignedTicketsWithoutMoney() {
        UUID techId = UUID.randomUUID();
        UUID propId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(techId, "tech@x", "KTV", UserRole.TECHNICIAN));
        when(assignmentPort.findPropertyIdsByUserId(techId)).thenReturn(Set.of(propId));
        MaintenanceTicket ticket = mock(MaintenanceTicket.class);
        when(ticket.getPropertyId()).thenReturn(propId);
        when(ticket.getRoomId()).thenReturn(roomId);
        when(ticket.getStatus()).thenReturn(MaintenanceStatus.ASSIGNED);
        when(ticket.getPriority()).thenReturn(MaintenancePriority.HIGH);
        when(ticket.getCreatedAt()).thenReturn(Instant.parse("2026-11-19T00:00:00Z"));
        when(ticketRepository.findByAssignedTechnicianId(techId)).thenReturn(List.of(ticket));
        when(roomRepository.findAllById(Set.of(roomId))).thenReturn(List.of(
            new Room(roomId, propId, "P201", 2, new BigDecimal("25"), new BigDecimal("4000000"), 2, RoomStatus.OCCUPIED, Instant.now(), Instant.now())
        ));

        AiAssistantUseCase.AiWidgetContext ctx = service.getWidgetContext();
        assertEquals("TECHNICIAN", ctx.role());

        service.askAssistant("AUTO", null, "Phiếu được giao?");
        JsonNode facts = captureFacts();
        assertEquals("TECHNICIAN", facts.path("viewerRole").asString());
        assertEquals(1, facts.path("openAssignedTicketsCount").asInt());
        assertEquals("P201", facts.path("openAssignedTickets").get(0).path("room").asString());
        assertTrue(facts.has("moneyHidden"));
        assertFalse(facts.has("unpaidInvoices"));
        assertFalse(facts.has("activeContract"));
    }

    @Test
    void staffWithoutFinancePermissionGetsPropertyFactsWithMoneyHidden() {
        UUID staffId = UUID.randomUUID();
        UUID propId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(staffId, "staff@x", "NV", UserRole.STAFF));
        Property prop = new Property(propId, "Nhà B", "Q3", "", 5, UUID.randomUUID(),
            PropertyApprovalStatus.VERIFIED, null, Instant.now());
        when(assignmentPort.findPropertyIdsByUserId(staffId)).thenReturn(Set.of(propId));
        when(assignmentPort.isUserAssignedToProperty(staffId, propId)).thenReturn(true);
        when(assignmentPort.find(staffId, propId)).thenReturn(Optional.of(
            new StaffPropertyAssignment(UUID.randomUUID(), staffId, propId, false, false, true, false, Instant.now())
        ));
        when(propertyRepository.findAllById(Set.of(propId))).thenReturn(List.of(prop));
        when(propertyRepository.findById(propId)).thenReturn(Optional.of(prop));
        when(roomRepository.findByPropertyId(propId)).thenReturn(List.of());
        when(contractRepository.findByPropertyId(propId)).thenReturn(List.of());
        when(ticketRepository.findByPropertyId(propId)).thenReturn(List.of());

        AiAssistantUseCase.AiWidgetContext ctx = service.getWidgetContext();
        assertFalse(ctx.quickPrompts().stream().anyMatch(p -> p.label().contains("công nợ")));

        service.askAssistant("PROPERTY", propId, "Tình trạng phòng?");
        JsonNode facts = captureFacts();
        assertTrue(facts.has("moneyHidden"));
        assertFalse(facts.has("unpaidInvoices"));
    }

    @Test
    void tenantAutoIncludesOwnContractAndPayableInvoiceFacts() {
        UUID tenantUserId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(tenantUserId, "t@x", "Minh Anh", UserRole.TENANT));

        Contract contract = mock(Contract.class);
        when(contract.getId()).thenReturn(contractId);
        when(contract.getRoomId()).thenReturn(roomId);
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(contract.getEndDate()).thenReturn(LocalDate.of(2026, 12, 31));
        when(contract.getRentAmount()).thenReturn(new BigDecimal("3500000"));
        when(contract.getDepositAmount()).thenReturn(new BigDecimal("3500000"));
        when(contract.getPaymentDay()).thenReturn(5);
        when(contractUseCase.getActiveContractForTenant(tenantUserId)).thenReturn(Optional.of(contract));

        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        Invoice inv = new Invoice(invoiceId, contractId, roomId, UUID.randomUUID(), 11, 2026,
            new BigDecimal("3500000"), new BigDecimal("3800000"), BigDecimal.ZERO,
            LocalDate.of(2026, 11, 10), InvoiceStatus.ISSUED, "PH001", InvoiceType.RENT, null, now, now, now);
        when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of(inv));
        when(ticketRepository.findByRoomId(roomId)).thenReturn(List.of());

        AiAssistantUseCase.AiWidgetContext ctx = service.getWidgetContext();
        assertEquals("TENANT", ctx.role());
        assertTrue(ctx.contexts().stream().anyMatch(c -> "INVOICE".equals(c.contextType()) && invoiceId.equals(c.targetId())));

        service.askAssistant("AUTO", null, "Tiền kỳ này bao nhiêu?");
        JsonNode facts = captureFacts();
        assertEquals("TENANT", facts.path("viewerRole").asString());
        assertEquals(1, facts.path("payableInvoiceCount").asInt());
        assertEquals("3800000", facts.path("totalPayableRemaining").asString());
        assertTrue(facts.has("activeContract"));
        assertTrue(facts.has("invoice"));

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.askAssistant("PROPERTY", UUID.randomUUID(), "Công nợ nhà?"));
    }

    private JsonNode captureFacts() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), captor.capture());
        String prompt = captor.getValue();
        int idx = prompt.indexOf("\n", prompt.indexOf("(JSON):")) + 1;
        return mapper.readTree(prompt.substring(idx));
    }
}
