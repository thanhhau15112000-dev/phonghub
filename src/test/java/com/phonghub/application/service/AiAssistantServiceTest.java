package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.exception.AiUnavailableException;
import com.phonghub.application.port.in.AiAssistantUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.AiCompletionPort;
import com.phonghub.application.port.out.FaqSearchPort;
import com.phonghub.application.port.out.FaqSearchPort.FaqMatch;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.config.AiAssistantProperties;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class AiAssistantServiceTest {
    private final InvoiceUseCase invoiceUseCase = mock(InvoiceUseCase.class);
    private final ContractUseCase contractUseCase = mock(ContractUseCase.class);
    private final InvoiceRepositoryPort invoiceRepository = mock(InvoiceRepositoryPort.class);
    private final ContractRepositoryPort contractRepository = mock(ContractRepositoryPort.class);
    private final RoomRepositoryPort roomRepository = mock(RoomRepositoryPort.class);
    private final MaintenanceTicketRepositoryPort ticketRepository = mock(MaintenanceTicketRepositoryPort.class);
    private final PropertyRepositoryPort propertyRepository = mock(PropertyRepositoryPort.class);
    private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
    private final com.phonghub.application.port.out.StaffPropertyAssignmentPort assignmentPort =
        mock(com.phonghub.application.port.out.StaffPropertyAssignmentPort.class);
    private final com.phonghub.application.port.out.TenantRepositoryPort tenantRepository =
        mock(com.phonghub.application.port.out.TenantRepositoryPort.class);
    private final AiCompletionPort completionPort = mock(AiCompletionPort.class);
    private final FaqSearchPort faqSearchPort = mock(FaqSearchPort.class);
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        AiAssistantProperties properties = new AiAssistantProperties();
        service = new AiAssistantService(invoiceUseCase, contractUseCase, invoiceRepository, contractRepository,
            roomRepository, ticketRepository, propertyRepository, currentUserPort,
            new AuthorizationService(assignmentPort, propertyRepository, tenantRepository, contractRepository),
            completionPort, faqSearchPort, properties, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(completionPort.complete(anyString(), anyString())).thenReturn("Phân tích có nguồn.");
    }

    @Test
    void invoicePromptContainsOnlyScopedFactsAndPrecomputedComparison() {
        UUID roomId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        Invoice current = invoice(currentId, roomId, tenantId, 11, 2026, "1000000", "1110500",
            List.of(
                utility("Điện tháng 11 (10 kWh)", "10", "50", "500"),
                InvoiceItem.of("Private repair description", new BigDecimal("100000"), UUID.randomUUID()),
                InvoiceItem.lateFee(new BigDecimal("10000"))
            ));
        Invoice prior = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "900000", "900400",
            List.of(utility("Điện tháng 10 (8 kWh)", "8", "50", "400")));
        when(invoiceUseCase.getInvoice(currentId)).thenReturn(current);
        when(invoiceRepository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3)).thenReturn(List.of(prior));

        AiAssistantUseCase.AiResponse response = service.askAboutInvoice(currentId, "Vì sao hóa đơn thay đổi?");

        assertEquals("Phân tích có nguồn.", response.answer());
        assertEquals(2, response.sources().size());
        assertEquals("Hóa đơn đang xem, kỳ 11/2026", response.sources().getFirst().label());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        String sent = prompt.getValue();
        assertTrue(sent.contains("\"comparisonPeriodCount\":3"));
        assertTrue(sent.contains("\"rentDifference\":\"100000\""));
        assertTrue(sent.contains("\"electricityQuantityDifference\":\"2\""));
        assertTrue(sent.contains("\"repairAmountSeparate\":\"100000\""));
        assertTrue(sent.contains("\"lateFeeSeparate\":\"10000\""));
        assertFalse(sent.contains("Private repair description"));
        assertFalse(sent.contains("PAYMENT-SECRET"));
        assertFalse(sent.contains(tenantId.toString()));
        verify(invoiceRepository).findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3);
    }

    @Test
    void invoiceAuthorizationRunsBeforeHistoryLookup() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceUseCase.getInvoice(invoiceId)).thenThrow(new UnauthorizedPropertyAccessException("denied"));

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.askAboutInvoice(invoiceId, "Tổng tiền là bao nhiêu?"));

        verify(invoiceRepository, never()).findPriorRentByRoomId(
            org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.any(YearMonth.class), org.mockito.ArgumentMatchers.anyInt());
        verify(completionPort, never()).complete(anyString(), anyString());
        verify(faqSearchPort, never()).search(anyString(), org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void invoiceFaqIsAddedAsContextAndCitedAfterAuthorizedRecordLookup() {
        UUID invoiceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(invoiceId, roomId, UUID.randomUUID(), 11, 2026, "1000000", "1000000", List.of());
        when(invoiceUseCase.getInvoice(invoiceId)).thenReturn(current);
        when(invoiceRepository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3)).thenReturn(List.of());
        when(faqSearchPort.search("Hóa đơn so sánh những kỳ nào?", java.util.Set.of("invoice", "general"), 3))
            .thenReturn(List.of(new FaqMatch("invoice-same-room-history", "invoice", "So sánh cùng phòng",
                "So sánh với kỳ nào?", "Mặc định lấy ba kỳ trước của cùng phòng.", "FAQ nháp", 0.82)));

        AiAssistantUseCase.AiResponse response = service.askAboutInvoice(invoiceId, "Hóa đơn so sánh những kỳ nào?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("Mặc định lấy ba kỳ trước của cùng phòng."));
        assertTrue(response.sources().stream().anyMatch(source -> source.label().contains("FAQ PhongHub nháp: So sánh cùng phòng")));
        verify(faqSearchPort).search("Hóa đơn so sánh những kỳ nào?", java.util.Set.of("invoice", "general"), 3);
    }

    @Test
    void faqEmbeddingFailureFallsBackToExistingGroundedChat() {
        UUID invoiceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(invoiceId, roomId, UUID.randomUUID(), 11, 2026, "1000000", "1000000", List.of());
        when(invoiceUseCase.getInvoice(invoiceId)).thenReturn(current);
        when(invoiceRepository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3)).thenReturn(List.of());
        when(faqSearchPort.search(anyString(), org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyInt()))
            .thenThrow(new AiUnavailableException());

        AiAssistantUseCase.AiResponse response = service.askAboutInvoice(invoiceId, "Tổng hóa đơn là bao nhiêu?");

        assertEquals("Phân tích có nguồn.", response.answer());
        assertEquals(1, response.sources().size());
        verify(completionPort).complete(anyString(), anyString());
    }

    @Test
    void maintenanceInvoiceIsReportedAsRepairAndKeptSeparateFromRent() {
        UUID invoiceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Invoice maintenance = new Invoice(invoiceId, UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026,
            BigDecimal.ZERO, new BigDecimal("250000"), BigDecimal.ZERO, LocalDate.of(2026, 11, 6),
            InvoiceStatus.ISSUED, "MAINTENANCE-CODE", InvoiceType.MAINTENANCE, UUID.randomUUID(), null, now, now);
        when(invoiceUseCase.getInvoice(invoiceId)).thenReturn(maintenance);
        when(invoiceRepository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3)).thenReturn(List.of());

        service.askAboutInvoice(invoiceId, "Giải thích phí sửa chữa");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("\"repairAmountSeparate\":\"250000\""));
        assertTrue(prompt.getValue().contains("\"otherAmount\":\"0\""));
    }

    @Test
    void contractPromptExplicitlyLimitsAnswersToStructuredFields() {
        UUID contractId = UUID.randomUUID();
        Contract contract = mock(Contract.class);
        when(contractUseCase.getContract(contractId)).thenReturn(contract);
        when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(contract.getEndDate()).thenReturn(LocalDate.of(2026, 12, 31));
        when(contract.getRentAmount()).thenReturn(new BigDecimal("2000000"));
        when(contract.getDepositAmount()).thenReturn(new BigDecimal("2000000"));
        when(contract.getPaymentDay()).thenReturn(5);

        service.askAboutContract(contractId, "Có điều khoản phạt nuôi thú cưng không?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("không lưu văn bản điều khoản"));
        assertTrue(prompt.getValue().contains("\"paymentDay\":5"));
        assertTrue(prompt.getValue().contains("phạt nuôi thú cưng"));
    }

    @Test
    void operationsAssistantRejectsTenantBeforeReadingPropertyData() {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(UUID.randomUUID(), "tenant@example.test",
            "Tên người thuê", UserRole.TENANT));

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.askOperations(UUID.randomUUID(), "Bao nhiêu phòng trống?"));

        verify(propertyRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(completionPort, never()).complete(anyString(), anyString());
    }

    @Test
    void operationsAssistantIsReadOnlyAndReceivesOnlyPropertyAggregates() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        CurrentUser owner = new CurrentUser(ownerId, "owner@example.test", "Chủ trọ", UserRole.OWNER);
        Property property = new Property(propertyId, "Cơ sở A", "Địa chỉ riêng", null, 12, ownerId,
            PropertyApprovalStatus.VERIFIED, null, Instant.now());
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(propertyRepository.findById(propertyId)).thenReturn(java.util.Optional.of(property));
        when(contractRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        service.askOperations(propertyId, "Tóm tắt công việc hôm nay");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("\"totalRooms\":0"));
        assertTrue(prompt.getValue().contains("\"totalRemaining\":\"0\""));
        assertTrue(prompt.getValue().contains("\"detailsIncluded\":false"));
        assertTrue(prompt.getValue().contains("\"items\":[]"));
        assertTrue(prompt.getValue().contains("\"roomDetailsIncluded\":false"));
        assertTrue(prompt.getValue().contains("\"byRoom\":[]"));
        assertFalse(prompt.getValue().contains("Địa chỉ riêng"));
        assertFalse(prompt.getValue().contains("owner@example.test"));
        verify(invoiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(contractRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(ticketRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void operationsQuestionAskingForDebtByRoomIncludesTheRoomBreakdown() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        CurrentUser owner = new CurrentUser(ownerId, "owner@example.test", "Chủ trọ", UserRole.OWNER);
        Property property = new Property(propertyId, "Cơ sở A", "Địa chỉ riêng", null, 12, ownerId,
            PropertyApprovalStatus.VERIFIED, null, Instant.now());
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(propertyRepository.findById(propertyId)).thenReturn(java.util.Optional.of(property));
        when(contractRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        service.askOperations(propertyId, "Tổng nợ theo phòng bao nhiêu?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("\"roomDetailsIncluded\":true"));
    }

    @Test
    void operationsAssistantIncludesAtMostTwentyRowsWhenTheQuestionRequestsAList() {
        UUID ownerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        CurrentUser owner = new CurrentUser(ownerId, "owner@example.test", "Chủ trọ", UserRole.OWNER);
        Property property = new Property(propertyId, "Cơ sở A", "Địa chỉ riêng", null, 12, ownerId,
            PropertyApprovalStatus.VERIFIED, null, Instant.now());
        List<Contract> contracts = new java.util.ArrayList<>();
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(propertyRepository.findById(propertyId)).thenReturn(java.util.Optional.of(property));
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        for (int i = 0; i < 25; i++) {
            Contract contract = mock(Contract.class);
            UUID contractId = UUID.randomUUID();
            when(contract.getId()).thenReturn(contractId);
            when(contract.getRoomId()).thenReturn(UUID.randomUUID());
            when(contract.getStatus()).thenReturn(ContractStatus.ACTIVE);
            when(contract.getEndDate()).thenReturn(LocalDate.of(2026, 11, 4).plusDays(i));
            when(invoiceRepository.findByContractId(contractId)).thenReturn(List.of());
            contracts.add(contract);
        }
        when(contractRepository.findByPropertyId(propertyId)).thenReturn(contracts);

        service.askOperations(propertyId, "Liệt kê hợp đồng nào sắp hết hạn?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("\"count\":25"));
        assertTrue(prompt.getValue().contains("\"detailsIncluded\":true"));
        assertTrue(prompt.getValue().contains("\"itemsOmitted\":5"));
        assertEquals(20, occurrences(prompt.getValue(), "daysRemaining"));
    }

    @Test
    void tooLongQuestionDoesNotReadDataOrCallProvider() {
        assertThrows(IllegalArgumentException.class,
            () -> service.askAboutInvoice(UUID.randomUUID(), "x".repeat(1201)));
        verify(invoiceUseCase, never()).getInvoice(org.mockito.ArgumentMatchers.any());
        verify(completionPort, never()).complete(anyString(), anyString());
        verify(faqSearchPort, never()).search(anyString(), org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static Invoice invoice(UUID id, UUID roomId, UUID tenantId, int month, int year,
                                   String rentAmount, String totalAmount, List<InvoiceItem> items) {
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Invoice invoice = new Invoice(id, UUID.randomUUID(), roomId, tenantId, month, year,
            new BigDecimal(rentAmount), new BigDecimal(totalAmount), BigDecimal.ZERO,
            LocalDate.of(year, month, 7), InvoiceStatus.ISSUED, "PAYMENT-SECRET", InvoiceType.RENT,
            null, now, now, now);
        invoice.loadItems(items);
        return invoice;
    }

    private static InvoiceItem utility(String name, String quantity, String price, String amount) {
        return new InvoiceItem(UUID.randomUUID(), name, new BigDecimal(quantity), new BigDecimal(price),
            new BigDecimal(amount), null, UUID.randomUUID(), UUID.randomUUID());
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
