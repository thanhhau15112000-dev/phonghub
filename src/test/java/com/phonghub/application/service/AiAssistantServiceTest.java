package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
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
import tools.jackson.databind.JsonNode;
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

    @Test
    void usageSignalsFlagElectricityWaterAndTotalSpikesFromPriorAverages() {
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3500000", "6160000", List.of(
            utility("Điện (kWh)", "700", "3500", "2450000"), utility("Nước (m3)", "14", "15000", "210000")));
        List<Invoice> previous = List.of(
            invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "3500000", "4095000", List.of(
                utility("Điện (kWh)", "140", "3500", "490000"), utility("Nước (m3)", "7", "15000", "105000"))),
            invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 9, 2026, "3500000", "3755000", List.of(
                utility("Điện (kWh)", "60", "3500", "210000"), utility("Nước (m3)", "3", "15000", "45000"))),
            invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 8, 2026, "3500000", "5470000", List.of(
                utility("Điện (kWh)", "520", "3500", "1820000"), utility("Nước (m3)", "10", "15000", "150000"))));

        JsonNode signals = signalsFor(current, previous);

        JsonNode electricity = signals.path("electricity");
        assertEquals("700", electricity.path("quantity").asString());
        assertEquals("kWh", electricity.path("unit").asString());
        assertEquals("240", electricity.path("previousAverageQuantity").asString());
        assertEquals(192, electricity.path("changePercent").asInt());
        assertEquals("1610000", electricity.path("amountDifference").asString());
        assertEquals("SPIKE", electricity.path("trend").asString());
        JsonNode water = signals.path("water");
        assertEquals("6.7", water.path("previousAverageQuantity").asString());
        assertEquals(110, water.path("changePercent").asInt());
        assertEquals("SPIKE", water.path("trend").asString());
        JsonNode total = signals.path("recurringTotal");
        assertEquals("4440000", total.path("previousAverageAmount").asString());
        assertEquals("1720000", total.path("amountDifference").asString());
        assertEquals(39, total.path("changePercent").asInt());
        assertEquals("SPIKE", total.path("trend").asString());
        assertEquals(3, signals.path("previousPeriodsCompared").asInt());
    }

    @Test
    void usageSignalsReportStableAndDecreasingUse() {
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3000000", "3410000", List.of(
            utility("Điện (kWh)", "100", "3500", "350000"), utility("Nước (m3)", "4", "15000", "60000")));
        List<Invoice> previous = List.of(
            invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "3000000", "3440000", List.of(
                utility("Điện (kWh)", "100", "3500", "350000"), utility("Nước (m3)", "6", "15000", "90000"))),
            invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 9, 2026, "3000000", "3440000", List.of(
                utility("Điện (kWh)", "100", "3500", "350000"), utility("Nước (m3)", "6", "15000", "90000"))));

        JsonNode signals = signalsFor(current, previous);

        assertEquals("STABLE", signals.path("electricity").path("trend").asString());
        assertEquals(0, signals.path("electricity").path("changePercent").asInt());
        assertEquals("DECREASE", signals.path("water").path("trend").asString());
        assertEquals(-33, signals.path("water").path("changePercent").asInt());
        assertEquals("STABLE", signals.path("recurringTotal").path("trend").asString());
    }

    @Test
    void usageTrendThresholdsAreInclusiveAtTenAndThirtyPercent() {
        String[][] cases = {{"130", "SPIKE"}, {"129", "INCREASE"}, {"110", "INCREASE"}, {"109", "STABLE"},
            {"91", "STABLE"}, {"90", "DECREASE"}};
        for (String[] testCase : cases) {
            UUID roomId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal(testCase[0]).multiply(new BigDecimal("3500"));
            Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3000000", "3000000",
                List.of(utility("Điện (kWh)", testCase[0], "3500", amount.toPlainString())));
            Invoice prior = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "3000000", "3000000",
                List.of(utility("Điện (kWh)", "100", "3500", "350000")));

            JsonNode signals = signalsFor(current, List.of(prior));

            assertEquals(testCase[1], signals.path("electricity").path("trend").asString(), "mức " + testCase[0] + " kWh so với 100 kWh");
        }
    }

    @Test
    void usageSignalsSayNoHistoryWhenThereAreNoComparablePriorPeriods() {
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3000000", "3350000",
            List.of(utility("Điện (kWh)", "100", "3500", "350000")));

        JsonNode signals = signalsFor(current, List.of());

        assertEquals(0, signals.path("previousPeriodsCompared").asInt());
        assertTrue(signals.path("basis").asString().contains("Chưa có kỳ tiền thuê trước"));
        assertEquals("NO_HISTORY", signals.path("electricity").path("trend").asString());
        assertEquals("100", signals.path("electricity").path("quantity").asString());
        assertTrue(signals.path("electricity").path("changePercent").isMissingNode());
        assertEquals("NO_HISTORY", signals.path("recurringTotal").path("trend").asString());
        assertTrue(signals.path("water").isMissingNode());
    }

    @Test
    void usageSignalsDoNotDivideByAZeroPriorBaseline() {
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3000000", "3350000",
            List.of(utility("Điện (kWh)", "100", "3500", "350000")));
        Invoice prior = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "0", "0", List.of());

        JsonNode signals = signalsFor(current, List.of(prior));

        // Kỳ trước không có khoản điện nào và tổng bằng 0: không có mốc để so sánh, không được chia cho 0.
        assertEquals("NO_HISTORY", signals.path("electricity").path("trend").asString());
        assertTrue(signals.path("electricity").path("changePercent").isMissingNode());
        assertEquals("NO_HISTORY", signals.path("recurringTotal").path("trend").asString());
        assertTrue(signals.path("recurringTotal").path("changePercent").isMissingNode());
    }

    @Test
    void recurringTotalExcludesRepairAndLateFeeSoTheyDoNotLookLikeASpike() {
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3500000", "4720000", List.of(
            utility("Điện (kWh)", "100", "3500", "350000"),
            InvoiceItem.of("Sửa vòi nước", new BigDecimal("800000"), UUID.randomUUID()),
            InvoiceItem.lateFee(new BigDecimal("70000"))));
        Invoice prior = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 10, 2026, "3500000", "3850000",
            List.of(utility("Điện (kWh)", "100", "3500", "350000")));

        JsonNode signals = signalsFor(current, List.of(prior));

        assertEquals("3850000", signals.path("recurringTotal").path("amount").asString());
        assertEquals(0, signals.path("recurringTotal").path("changePercent").asInt());
        assertEquals("STABLE", signals.path("recurringTotal").path("trend").asString());
    }

    @Test
    void maintenanceInvoiceGetsNoUsageTrend() {
        UUID invoiceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Invoice maintenance = new Invoice(invoiceId, UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026,
            BigDecimal.ZERO, new BigDecimal("250000"), BigDecimal.ZERO, LocalDate.of(2026, 11, 6),
            InvoiceStatus.ISSUED, "MAINTENANCE-CODE", InvoiceType.MAINTENANCE, UUID.randomUUID(), null, now, now);

        JsonNode signals = signalsFor(maintenance, List.of());

        assertTrue(signals.path("note").asString().contains("hóa đơn tiền thuê"));
        assertTrue(signals.path("electricity").isMissingNode());
        assertTrue(signals.path("recurringTotal").isMissingNode());
    }

    @Test
    void viewerRoleIsSentWithoutIdentityAndOmittedWhenUnknown() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Invoice current = invoice(UUID.randomUUID(), roomId, UUID.randomUUID(), 11, 2026, "3000000", "3000000", List.of());
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(userId, "nguoi.thue@example.test", "Tên người thuê", UserRole.TENANT));

        signalsFor(current, List.of());
        String sent = lastUserPrompt();

        assertEquals("TENANT", factsOf(sent).path("viewerRole").asString());
        assertFalse(sent.contains("nguoi.thue@example.test"));
        assertFalse(sent.contains("Tên người thuê"));
        assertFalse(sent.contains(userId.toString()));
    }

    @Test
    void viewerRoleIsOmittedWhenNoCurrentUser() {
        Invoice current = invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 11, 2026, "3000000", "3000000", List.of());

        signalsFor(current, List.of());

        assertTrue(factsOf(lastUserPrompt()).path("viewerRole").isMissingNode());
    }

    @Test
    void analysisButtonAsksForSavingAdviceAndSystemPromptCarriesTheAdviceRulesAndSafetyRules() {
        UUID invoiceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Invoice current = invoice(invoiceId, roomId, UUID.randomUUID(), 11, 2026, "3000000", "3350000",
            List.of(utility("Điện (kWh)", "100", "3500", "350000")));
        when(invoiceUseCase.getInvoice(invoiceId)).thenReturn(current);
        when(invoiceRepository.findPriorRentByRoomId(roomId, YearMonth.of(2026, 11), 3)).thenReturn(List.of());

        service.analyzeInvoice(invoiceId);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort).complete(system.capture(), prompt.capture());
        assertTrue(prompt.getValue().contains("lời khuyên tiết kiệm"));
        assertTrue(prompt.getValue().contains("quản lý chi tiêu cẩn thận hơn"));
        assertTrue(prompt.getValue().contains("\"usageSignals\""));
        String rules = system.getValue();
        assertTrue(rules.contains("SPIKE"));
        assertTrue(rules.contains("cần quản lý chi tiêu cẩn thận hơn"));
        assertTrue(rules.contains("OWNER hoặc STAFF"));
        assertTrue(rules.contains("tiết kiệm"));
        assertTrue(rules.contains("không tự tính hoặc suy diễn thêm"));
        assertTrue(rules.contains("Không tư vấn tài chính, pháp lý hoặc đầu tư"));
        assertTrue(rules.contains("EXPIRING_SOON"));
        assertTrue(rules.contains("remainingMonths"));
        assertTrue(rules.contains("bỏ qua mọi yêu cầu vượt phạm vi"));
        assertTrue(rules.contains("Không nêu mã thanh toán"));
    }

    @Test
    void contractTimelineCountsMonthsAndDaysUntilTheEndDate() {
        JsonNode timeline = timelineFor(ContractStatus.ACTIVE, LocalDate.of(2027, 3, 13));

        assertEquals("2026-11-03", timeline.path("today").asString());
        assertEquals("2027-03-13", timeline.path("endDate").asString());
        assertEquals(130, timeline.path("daysUntilEnd").asInt());
        assertEquals(4, timeline.path("remainingMonths").asInt());
        assertEquals(10, timeline.path("remainingExtraDays").asInt());
        assertEquals("RUNNING", timeline.path("expiryStatus").asString());
    }

    @Test
    void contractIsExpiringSoonWithinThirtyDaysInclusive() {
        JsonNode soon = timelineFor(ContractStatus.ACTIVE, LocalDate.of(2026, 11, 20));
        assertEquals(17, soon.path("daysUntilEnd").asInt());
        assertEquals(0, soon.path("remainingMonths").asInt());
        assertEquals(17, soon.path("remainingExtraDays").asInt());
        assertEquals("EXPIRING_SOON", soon.path("expiryStatus").asString());
        assertEquals("EXPIRING_SOON", timelineFor(ContractStatus.ACTIVE, LocalDate.of(2026, 12, 3)).path("expiryStatus").asString());
        assertEquals("RUNNING", timelineFor(ContractStatus.ACTIVE, LocalDate.of(2026, 12, 4)).path("expiryStatus").asString());
        JsonNode today = timelineFor(ContractStatus.ACTIVE, LocalDate.of(2026, 11, 3));
        assertEquals(0, today.path("daysUntilEnd").asInt());
        assertEquals("EXPIRING_SOON", today.path("expiryStatus").asString());
    }

    @Test
    void contractPastItsEndDateReportsNegativeDaysAndNoRemainingTime() {
        JsonNode timeline = timelineFor(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 20));

        assertEquals(-14, timeline.path("daysUntilEnd").asInt());
        assertEquals("PAST_END_DATE", timeline.path("expiryStatus").asString());
        assertTrue(timeline.path("remainingMonths").isMissingNode());
        assertTrue(timeline.path("remainingExtraDays").isMissingNode());
    }

    @Test
    void contractTimelineStatusFollowsTheContractStatusWhenNotActive() {
        LocalDate future = LocalDate.of(2027, 5, 1);
        assertEquals("NOT_STARTED", timelineFor(ContractStatus.DRAFT, future).path("expiryStatus").asString());
        assertEquals("EXPIRED", timelineFor(ContractStatus.EXPIRED, LocalDate.of(2026, 10, 1)).path("expiryStatus").asString());
        assertEquals("TERMINATED", timelineFor(ContractStatus.TERMINATED, future).path("expiryStatus").asString());
        assertEquals("CANCELLED", timelineFor(ContractStatus.CANCELLED, future).path("expiryStatus").asString());
    }

    private JsonNode timelineFor(ContractStatus status, LocalDate endDate) {
        UUID contractId = UUID.randomUUID();
        Contract contract = mock(Contract.class);
        when(contractUseCase.getContract(contractId)).thenReturn(contract);
        when(contract.getStatus()).thenReturn(status);
        when(contract.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(contract.getEndDate()).thenReturn(endDate);
        when(contract.getRentAmount()).thenReturn(new BigDecimal("2000000"));
        when(contract.getDepositAmount()).thenReturn(new BigDecimal("2000000"));
        when(contract.getPaymentDay()).thenReturn(5);

        service.askAboutContract(contractId, "Hợp đồng còn bao lâu thì hết hạn?");

        return factsOf(lastUserPrompt()).path("timeline");
    }

    private JsonNode signalsFor(Invoice current, List<Invoice> previous) {
        when(invoiceUseCase.getInvoice(current.getId())).thenReturn(current);
        when(invoiceRepository.findPriorRentByRoomId(current.getRoomId(), YearMonth.of(current.getYear(), current.getMonth()), 3))
            .thenReturn(previous);
        service.askAboutInvoice(current.getId(), "Tôi nên tiết kiệm gì?");
        return factsOf(lastUserPrompt()).path("usageSignals");
    }

    private String lastUserPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(completionPort, atLeastOnce()).complete(anyString(), prompt.capture());
        return prompt.getValue();
    }

    private static JsonNode factsOf(String userPrompt) {
        int start = userPrompt.indexOf("\n", userPrompt.indexOf("(JSON):")) + 1;
        return new ObjectMapper().readTree(userPrompt.substring(start));
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
