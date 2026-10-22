package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.CheckOutSettlementUseCase;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quyết toán tiền cọc trên trang check-out và chỉ số điện nước đầu kỳ của khách mới (tính từ biên bản check-in, không
 * từ lần ghi của khách trước).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckOutSettlementIntegrationTest {

    private static final UUID OWNER_ID = LocalDemoAuthenticationAdapter.OWNER_1_ID;
    private static final String OWNER_1 = OWNER_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private CheckOutSettlementUseCase settlementUseCase;
    @Autowired private UtilityUseCase utilityUseCase;
    @Autowired private DemoActorPort demoActorPort;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;
    @Autowired private InMemoryHandoverRepository handoverRepository;
    @Autowired private InMemoryHandoverItemRepository itemRepository;
    @Autowired private InMemoryMeterReadingRepository readingRepository;
    @Autowired private InMemoryServiceRateRepository rateRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        invoiceRepository.clear();
        handoverRepository.clear();
        itemRepository.clear();
        readingRepository.clear();
        rateRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
        Instant now = Instant.now();
        rateRepository.save(new ServiceRate(UUID.randomUUID(), DataSeeder.PROP_1_ID, "Điện", ChargeType.PER_KWH,
            new BigDecimal("3500"), now, OWNER_ID, now, OWNER_ID));
    }

    private Handover approvedCheckIn(UUID contractId, String electric) {
        Handover handover = Handover.draft(contractId, HandoverType.CHECK_IN, new BigDecimal(electric),
            new BigDecimal("10"), "Phòng sạch", "2 chìa", OWNER_ID)
            .send("terms", null, null, OWNER_ID).tenantConfirm(OWNER_ID).approve(OWNER_ID);
        return handoverRepository.save(handover);
    }

    @Test
    @DisplayName("Quyết toán = cọc − khấu trừ − hóa đơn chưa thu − điện chưa tính; kỹ thuật viên không có, người thuê có")
    void settlementOnCheckOutPage() throws Exception {
        mockMvc.perform(post("/contracts/" + DataSeeder.CONTRACT_1_ID + "/invoices").header("X-User-Id", OWNER_1)
            .param("period", YearMonth.now().toString()));
        readingRepository.save(MeterReading.record(DataSeeder.ROOM_101_ID, DataSeeder.CONTRACT_1_ID,
            MeterType.ELECTRIC, LocalDate.now().minusMonths(1), new BigDecimal("100"), new BigDecimal("120"), null,
            OWNER_ID));
        Handover checkOut = handoverRepository.save(Handover.draft(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_OUT,
            new BigDecimal("150"), new BigDecimal("10"), "Tường bẩn", "2 chìa", OWNER_ID));
        itemRepository.replaceAll(checkOut.id(), List.of(new HandoverItem(UUID.randomUUID(), checkOut.id(),
            UUID.randomUUID(), "Rèm cửa", 1, AssetStatus.DAMAGED, "Rách", new BigDecimal("200000"))), OWNER_ID);

        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        CheckOutSettlementUseCase.Settlement settlement = settlementUseCase.settlement(DataSeeder.CONTRACT_1_ID)
            .orElseThrow();
        assertEquals(0, new BigDecimal("3500000").compareTo(settlement.deposit()));
        assertEquals(0, new BigDecimal("200000").compareTo(settlement.deduction()));
        assertFalse(settlement.deductionFinal());
        assertEquals(1, settlement.unpaidInvoices());
        assertEquals(0, new BigDecimal("3500000").compareTo(settlement.outstanding()));
        assertEquals(1, settlement.utilities().size());
        assertEquals(0, new BigDecimal("30").compareTo(settlement.utilities().get(0).consumption()));
        assertEquals(0, new BigDecimal("105000").compareTo(settlement.utilityTotal()));
        assertEquals(0, new BigDecimal("-305000").compareTo(settlement.balance()));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID + "/check-out").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Quyết toán tiền cọc")))
            .andExpect(content().string(containsString("Người thuê cần trả thêm")));

        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TECH_1);
        assertTrue(settlementUseCase.settlement(DataSeeder.CONTRACT_1_ID).isEmpty());
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        assertTrue(settlementUseCase.settlement(DataSeeder.CONTRACT_1_ID).isPresent());
    }

    @Test
    @DisplayName("Khách mới: kỳ đầu tính từ chỉ số check-in, không từ lần ghi của khách trước")
    void firstReadingOfNewTenantStartsFromCheckIn() {
        // Phòng 104 có lần ghi của khách cũ (500), khách mới nhận phòng lúc đồng hồ ở 520
        readingRepository.save(MeterReading.record(DataSeeder.ROOM_104_ID, UUID.randomUUID(), MeterType.ELECTRIC,
            LocalDate.now().minusMonths(1), new BigDecimal("450"), new BigDecimal("500"), null, OWNER_ID));
        Contract contract = Contract.create(DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, UUID.randomUUID(),
            new BigDecimal("4000000"), new BigDecimal("4000000"), LocalDate.now().withDayOfMonth(1),
            LocalDate.now().plusYears(1), 5);
        contract.activate();
        contractRepository.save(contract);
        Room room = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        room.occupy();
        roomRepository.save(room);
        approvedCheckIn(contract.getId(), "520");

        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        MeterReading reading = utilityUseCase.recordReading(new UtilityUseCase.RecordReadingCommand(
            DataSeeder.ROOM_104_ID, MeterType.ELECTRIC, new BigDecimal("560"), LocalDate.now(), null, null, null,
            null));

        assertEquals(0, new BigDecimal("520").compareTo(reading.previousValue()));
        assertEquals(0, new BigDecimal("40").compareTo(reading.consumption()));
    }
}
