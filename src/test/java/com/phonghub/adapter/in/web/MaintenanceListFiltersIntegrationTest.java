package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenanceListFiltersIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String SEEDED = "Sửa vòi nước và kiểm tra máy lạnh";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;
    @Autowired private InMemoryPaymentRepository paymentRepository;

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
        paymentRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String userId) {
        return builder.header("X-User-Id", userId);
    }

    private MaintenanceTicket createTicket(String title, String priority) throws Exception {
        mockMvc.perform(as(post("/maintenance"), TENANT)
            .param("roomId", DataSeeder.ROOM_101_ID.toString())
            .param("title", title)
            .param("description", "Mô tả " + title)
            .param("priority", priority)
            .param("setRoomMaintenance", "false")
            .param("cause", "TENANT_USAGE"));
        return ticketRepository.findAll().stream().filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private void resolve(UUID ticketId, String cost) throws Exception {
        mockMvc.perform(as(post("/maintenance/" + ticketId + "/accept"), ADMIN));
        mockMvc.perform(as(post("/maintenance/" + ticketId + "/resolve"), ADMIN)
            .param("resolutionNotes", "Đã sửa")
            .param("repairCost", cost)
            .param("releaseRoomToAvailable", "false"));
    }

    @Test
    @DisplayName("Open tab lists unfinished tickets; Done tab is separate; awaiting payment is a status filter on the open tab")
    void tabsAndAwaitingPaymentFilter() throws Exception {
        MaintenanceTicket awaiting = createTicket("Phiếu chờ thanh toán", "MEDIUM");
        resolve(awaiting.getId(), "200000");
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, ticketRepository.findById(awaiting.getId()).orElseThrow().getStatus());
        MaintenanceTicket done = createTicket("Phiếu đã xong", "LOW");
        resolve(done.getId(), "0");

        mockMvc.perform(as(get("/maintenance"), ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(SEEDED)))
            .andExpect(content().string(containsString("Phiếu chờ thanh toán")))
            .andExpect(content().string(not(containsString("Phiếu đã xong"))));

        mockMvc.perform(as(get("/maintenance?tab=open&status=AWAITING_PAYMENT"), ADMIN))
            .andExpect(content().string(containsString("Phiếu chờ thanh toán")))
            .andExpect(content().string(not(containsString(SEEDED))))
            .andExpect(content().string(not(containsString("Phiếu đã xong"))));

        mockMvc.perform(as(get("/maintenance?tab=done"), ADMIN))
            .andExpect(content().string(containsString("Phiếu đã xong")))
            .andExpect(content().string(not(containsString("Phiếu chờ thanh toán"))))
            .andExpect(content().string(not(containsString(SEEDED))));
    }

    @Test
    @DisplayName("Paying the fee moves the ticket from the open tab to the done tab")
    void paidTicketMovesToDone() throws Exception {
        MaintenanceTicket ticket = createTicket("Phiếu sẽ trả tiền", "HIGH");
        resolve(ticket.getId(), "150000");
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();

        mockMvc.perform(as(get("/maintenance?tab=open&status=AWAITING_PAYMENT"), ADMIN))
            .andExpect(content().string(containsString("Phiếu sẽ trả tiền")));

        String payload = """
            {"id": 660001, "gateway": "MBBank", "transactionDate": "2026-10-02 08:00:00", "accountNumber": "0389999999",
             "content": "TT %s", "transferType": "in", "transferAmount": 150000, "accumulated": 1}
            """.formatted(fee.getPaymentCode());
        mockMvc.perform(post("/api/v1/payments/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());

        mockMvc.perform(as(get("/maintenance?tab=open"), ADMIN))
            .andExpect(content().string(not(containsString("Phiếu sẽ trả tiền"))));
        mockMvc.perform(as(get("/maintenance?tab=done"), ADMIN))
            .andExpect(content().string(containsString("Phiếu sẽ trả tiền")));
    }

    @Test
    @DisplayName("Filters by property, priority and report date range")
    void commonFilters() throws Exception {
        createTicket("Phiếu mức cao", "URGENT");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // mức độ
        mockMvc.perform(as(get("/maintenance?priority=URGENT"), ADMIN))
            .andExpect(content().string(containsString("Phiếu mức cao")))
            .andExpect(content().string(not(containsString(SEEDED))));
        mockMvc.perform(as(get("/maintenance?priority=HIGH"), ADMIN))
            .andExpect(content().string(containsString(SEEDED)))
            .andExpect(content().string(not(containsString("Phiếu mức cao"))));

        // cơ sở: phiếu đều thuộc PROP_1, PROP_2 không có phiếu nào
        mockMvc.perform(as(get("/maintenance?propertyId=" + DataSeeder.PROP_1_ID), ADMIN))
            .andExpect(content().string(containsString("Phiếu mức cao")));
        mockMvc.perform(as(get("/maintenance?propertyId=" + DataSeeder.PROP_2_ID), ADMIN))
            .andExpect(content().string(not(containsString("Phiếu mức cao"))))
            .andExpect(content().string(containsString("Không có yêu cầu bảo trì đang xử lý")));

        // thời gian báo cáo
        mockMvc.perform(as(get("/maintenance?from=" + today + "&to=" + today), ADMIN))
            .andExpect(content().string(containsString("Phiếu mức cao")));
        mockMvc.perform(as(get("/maintenance?from=" + today.plusDays(1)), ADMIN))
            .andExpect(content().string(not(containsString("Phiếu mức cao"))));
        mockMvc.perform(as(get("/maintenance?to=" + today.minusDays(1)), ADMIN))
            .andExpect(content().string(not(containsString("Phiếu mức cao"))));

        // giá trị không hợp lệ bị bỏ qua, không lỗi
        mockMvc.perform(as(get("/maintenance?priority=NOPE&propertyId=abc&from=xx&status=ZZZ&tab=what"), ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Phiếu mức cao")));
    }

    @Test
    @DisplayName("Tenant keeps the tabs but has no manager filters or property column")
    void tenantView() throws Exception {
        createTicket("Phiếu của người thuê", "MEDIUM");
        mockMvc.perform(as(get("/maintenance"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Phiếu của người thuê")))
            .andExpect(content().string(containsString("Đã hoàn thành")))
            .andExpect(content().string(not(containsString("Tất cả cơ sở"))));
    }

    @Test
    @DisplayName("Dashboard shows the maintenance status summary including awaiting payment")
    void dashboardSummary() throws Exception {
        MaintenanceTicket awaiting = createTicket("Chờ trả tiền dashboard", "MEDIUM");
        resolve(awaiting.getId(), "100000");

        mockMvc.perform(as(get("/dashboard"), ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chưa hoàn thành")))
            .andExpect(content().string(containsString("Chờ thanh toán")))
            .andExpect(content().string(containsString("Đã hoàn thành")))
            .andExpect(content().string(containsString("status=AWAITING_PAYMENT")))
            .andExpect(content().string(containsString("Chờ trả tiền dashboard")));
    }

    @Test
    @DisplayName("Filter parsing is lenient and the done group excludes AWAITING_PAYMENT")
    void filterParsing() {
        MaintenanceListFilter filter = MaintenanceListFilter.parse("done", "AWAITING_PAYMENT", "bad", "urgent", "2026-10-01", "oops");
        assertEquals(MaintenanceListFilter.Tab.DONE, filter.tab());
        assertNull(filter.status(), "status filter is ignored on the done tab");
        assertNull(filter.propertyId());
        assertEquals(MaintenancePriority.URGENT, filter.priority());
        assertEquals(LocalDate.of(2026, 10, 1), filter.from());
        assertNull(filter.to());

        MaintenanceTicket ticket = MaintenanceTicket.create(
            UUID.randomUUID(), UUID.randomUUID(), null, "T", "D", MaintenancePriority.LOW, null);
        assertFalse(MaintenanceListFilter.isDone(ticket));
        ticket.accept(UUID.randomUUID());
        ticket.resolve("ok", java.math.BigDecimal.ZERO);
        assertTrue(MaintenanceListFilter.isDone(ticket));
        assertEquals("", MaintenanceListFilter.parse(null, null, null, null, null, null).commonQuery());
    }
}
