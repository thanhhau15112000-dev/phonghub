package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
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
class MaintenanceFeeFlowIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

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

    private long webhookSeq = 770000;

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

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String userId) {
        return builder.header("X-User-Id", userId);
    }

    private MaintenanceTicket createTicket(String userId, UUID roomId, String title, String cause) throws Exception {
        MockHttpServletRequestBuilder request = asUser(post("/maintenance"), userId)
            .param("roomId", roomId.toString())
            .param("title", title)
            .param("description", "Mô tả " + title)
            .param("setRoomMaintenance", "false");
        if (cause != null) {
            request.param("cause", cause);
        }
        mockMvc.perform(request);
        return ticketRepository.findAll().stream()
            .filter(t -> t.getTitle().equals(title))
            .findFirst()
            .orElseThrow();
    }

    private void accept(UUID ticketId) throws Exception {
        mockMvc.perform(asUser(post("/maintenance/" + ticketId + "/accept"), ADMIN));
    }

    private void resolve(UUID ticketId, String cost, String liableParty) throws Exception {
        MockHttpServletRequestBuilder request = asUser(post("/maintenance/" + ticketId + "/resolve"), ADMIN)
            .param("resolutionNotes", "Đã sửa xong")
            .param("repairCost", cost)
            .param("releaseRoomToAvailable", "false");
        if (liableParty != null) {
            request.param("liableParty", liableParty);
        }
        mockMvc.perform(request);
    }

    private MaintenanceTicket reload(UUID ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow();
    }

    private void payByWebhook(String code, String amount) throws Exception {
        String payload = """
            {
              "id": %d,
              "gateway": "MBBank",
              "transactionDate": "2026-10-02 08:00:00",
              "accountNumber": "0389999999",
              "content": "THANH TOAN %s",
              "transferType": "in",
              "transferAmount": %s,
              "accumulated": 1000000
            }
            """.formatted(webhookSeq++, code, amount);
        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant-reported usage damage: quote creates a fee invoice, SePay payment completes the ticket")
    void tenantUsageFeeEndToEnd() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Vỡ kính cửa sổ", "TENANT_USAGE");
        assertEquals(LiableParty.TENANT, ticket.getLiableParty());
        accept(ticket.getId());
        resolve(ticket.getId(), "250000", null);

        MaintenanceTicket awaiting = reload(ticket.getId());
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, awaiting.getStatus());
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();
        assertEquals(InvoiceType.MAINTENANCE, fee.getType());
        assertEquals(DataSeeder.CONTRACT_1_ID, fee.getContractId());
        assertEquals(0, fee.getTotalAmount().compareTo(new java.math.BigDecimal("250000")));

        mockMvc.perform(asUser(get("/dashboard"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Phí sửa chữa")))
            .andExpect(content().string(containsString(fee.getPaymentCode())));
        mockMvc.perform(asUser(get("/maintenance/" + ticket.getId()), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chờ thanh toán")))
            .andExpect(content().string(containsString(fee.getPaymentCode())));

        // Trả thiếu: phiếu vẫn chờ thanh toán
        payByWebhook(fee.getPaymentCode(), "100000");
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, reload(ticket.getId()).getStatus());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoiceRepository.findById(fee.getId()).orElseThrow().getStatus());

        // Trả đủ: phiếu hoàn tất
        payByWebhook(fee.getPaymentCode(), "150000");
        assertEquals(MaintenanceStatus.RESOLVED, reload(ticket.getId()).getStatus());
        assertEquals(InvoiceStatus.PAID, invoiceRepository.findById(fee.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Owner-liable causes resolve without any invoice")
    void naturalWearHasNoInvoice() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Bóng đèn cũ", "NATURAL_WEAR");
        assertEquals(LiableParty.OWNER, ticket.getLiableParty());
        accept(ticket.getId());
        resolve(ticket.getId(), "80000", null);

        assertEquals(MaintenanceStatus.RESOLVED, reload(ticket.getId()).getStatus());
        assertTrue(invoiceRepository.findActiveByTicketId(ticket.getId()).isEmpty());
    }

    @Test
    @DisplayName("Technician/admin can override the reported cause: liable party is changed and a fee is issued")
    void liablePartyOverrideIssuesFee() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Hỏng ổ khóa", "NATURAL_WEAR");
        accept(ticket.getId());
        resolve(ticket.getId(), "300000", "TENANT");

        MaintenanceTicket awaiting = reload(ticket.getId());
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, awaiting.getStatus());
        assertEquals(LiableParty.TENANT, awaiting.getLiableParty());
        assertNotNull(invoiceRepository.findActiveByTicketId(ticket.getId()).orElse(null));
    }

    @Test
    @DisplayName("Unknown cause with a cost is rejected until the liable party is decided; nothing is changed")
    void unknownCauseNeedsDecision() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Ổ cắm chập", "UNKNOWN");
        assertEquals(LiableParty.UNDETERMINED, ticket.getLiableParty());
        accept(ticket.getId());

        mockMvc.perform(asUser(post("/maintenance/" + ticket.getId() + "/resolve"), ADMIN)
                .param("resolutionNotes", "Đã sửa")
                .param("repairCost", "120000")
                .param("releaseRoomToAvailable", "false"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(MaintenanceStatus.IN_PROGRESS, reload(ticket.getId()).getStatus());
        assertTrue(invoiceRepository.findActiveByTicketId(ticket.getId()).isEmpty());

        resolve(ticket.getId(), "120000", "OWNER");
        assertEquals(MaintenanceStatus.RESOLVED, reload(ticket.getId()).getStatus());
    }

    @Test
    @DisplayName("Tenant-liable fee on a room without an active contract is rejected and leaves the ticket untouched")
    void feeRequiresActiveContract() throws Exception {
        MaintenanceTicket ticket = createTicket(ADMIN, DataSeeder.ROOM_102_ID, "Hỏng vòi sen", "TENANT_USAGE");
        assertEquals(LiableParty.TENANT, ticket.getLiableParty());
        accept(ticket.getId());

        mockMvc.perform(asUser(post("/maintenance/" + ticket.getId() + "/resolve"), ADMIN)
                .param("resolutionNotes", "Đã sửa")
                .param("repairCost", "150000")
                .param("releaseRoomToAvailable", "false"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(MaintenanceStatus.IN_PROGRESS, reload(ticket.getId()).getStatus());
    }

    @Test
    @DisplayName("Admin can waive a fee (voids invoice, owner pays); tenant cannot; paid-in-part fee cannot be waived")
    void waiveFee() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Gãy tay nắm cửa", "TENANT_USAGE");
        accept(ticket.getId());
        resolve(ticket.getId(), "200000", null);
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();

        mockMvc.perform(asUser(post("/maintenance/" + ticket.getId() + "/waive-fee"), TENANT)
                .param("reason", "Không muốn trả"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, reload(ticket.getId()).getStatus());

        mockMvc.perform(asUser(post("/maintenance/" + ticket.getId() + "/waive-fee"), ADMIN)
                .param("reason", "Lỗi do thiết bị cũ"))
            .andExpect(flash().attributeExists("successMessage"));
        MaintenanceTicket waived = reload(ticket.getId());
        assertEquals(MaintenanceStatus.RESOLVED, waived.getStatus());
        assertEquals(LiableParty.OWNER, waived.getLiableParty());
        assertEquals(InvoiceStatus.VOIDED, invoiceRepository.findById(fee.getId()).orElseThrow().getStatus());

        // Hóa đơn bị hủy không còn nhận tiền
        payByWebhook(fee.getPaymentCode(), "200000");
        assertEquals(0, invoiceRepository.findById(fee.getId()).orElseThrow().getPaidAmount().signum());
    }

    @Test
    @DisplayName("A fee with a partial payment cannot be waived")
    void partiallyPaidFeeCannotBeWaived() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Vỡ gương", "TENANT_USAGE");
        accept(ticket.getId());
        resolve(ticket.getId(), "200000", null);
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();
        payByWebhook(fee.getPaymentCode(), "50000");

        mockMvc.perform(asUser(post("/maintenance/" + ticket.getId() + "/waive-fee"), ADMIN)
                .param("reason", "Miễn"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, reload(ticket.getId()).getStatus());
    }

    @Test
    @DisplayName("Awaiting-payment ticket exposes a Pay button; the pay page shows amount and transfer code, and a paid invoice no longer asks for money")
    void payButtonAndPayPage() throws Exception {
        MaintenanceTicket ticket = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Hỏng vòi sen", "TENANT_USAGE");
        accept(ticket.getId());
        resolve(ticket.getId(), "180000", null);
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();
        String payUrl = "/invoices/" + fee.getId() + "/pay";

        mockMvc.perform(asUser(get("/maintenance"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(payUrl)))
            .andExpect(content().string(containsString("Thanh toán")));
        mockMvc.perform(asUser(get("/maintenance/" + ticket.getId()), TENANT))
            .andExpect(content().string(containsString(payUrl)));
        mockMvc.perform(asUser(get("/contracts/" + DataSeeder.CONTRACT_1_ID), TENANT))
            .andExpect(content().string(containsString(payUrl)));
        mockMvc.perform(asUser(get("/dashboard"), TENANT))
            .andExpect(content().string(containsString(payUrl)));

        mockMvc.perform(asUser(get(payUrl), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Phí sửa chữa")))
            .andExpect(content().string(containsString("Hỏng vòi sen")))
            .andExpect(content().string(containsString("180,000 đ")))
            .andExpect(content().string(containsString(fee.getPaymentCode())));

        payByWebhook(fee.getPaymentCode(), "180000");
        mockMvc.perform(asUser(get(payUrl), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("đã được thanh toán đủ")));
        mockMvc.perform(asUser(get("/maintenance"), TENANT))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(payUrl))));
    }

    @Test
    @DisplayName("Pay page for an unknown invoice redirects with an error")
    void payPageUnknownInvoice() throws Exception {
        mockMvc.perform(asUser(get("/invoices/" + UUID.randomUUID() + "/pay"), TENANT))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Tenant only sees tickets of their own room; other rooms' tickets are hidden and not openable")
    void tenantSeesOnlyOwnRoomTickets() throws Exception {
        // Phiếu seed nằm ở phòng 102, người thuê ở phòng 101
        mockMvc.perform(asUser(get("/maintenance"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Sửa vòi nước và kiểm tra máy lạnh"))));
        mockMvc.perform(asUser(get("/maintenance/" + DataSeeder.TICKET_1_ID), TENANT))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(asUser(get("/maintenance"), ADMIN))
            .andExpect(content().string(containsString("Sửa vòi nước và kiểm tra máy lạnh")));

        MaintenanceTicket own = createTicket(TENANT, DataSeeder.ROOM_101_ID, "Phiếu của phòng mình", "NATURAL_WEAR");
        mockMvc.perform(asUser(get("/maintenance"), TENANT))
            .andExpect(content().string(containsString("Phiếu của phòng mình")));
        mockMvc.perform(asUser(get("/maintenance/" + own.getId()), TENANT))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant must pick a cause when reporting; the form exposes the cause dropdown")
    void tenantMustPickCause() throws Exception {
        mockMvc.perform(asUser(get("/maintenance/new"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Hư hao tự nhiên")))
            .andExpect(content().string(containsString("Do sử dụng của người thuê")));

        mockMvc.perform(asUser(post("/maintenance"), TENANT)
                .param("roomId", DataSeeder.ROOM_101_ID.toString())
                .param("title", "Không chọn nguyên nhân")
                .param("description", "Mô tả"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(ticketRepository.findAll().stream().noneMatch(t -> t.getTitle().equals("Không chọn nguyên nhân")));
    }
}
