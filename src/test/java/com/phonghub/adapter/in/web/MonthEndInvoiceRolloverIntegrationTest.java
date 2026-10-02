package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.YearMonth;
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
class MonthEndInvoiceRolloverIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT_URL = "/contracts/" + DataSeeder.CONTRACT_1_ID;

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

    private long webhookSeq = 550000;

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

    /** Phiếu do người thuê chịu phí, đã sửa xong và báo giá: sinh hóa đơn phí chờ thanh toán. */
    private Invoice feeInvoice(String title, String cost) throws Exception {
        mockMvc.perform(as(post("/maintenance"), TENANT)
            .param("roomId", DataSeeder.ROOM_101_ID.toString())
            .param("title", title)
            .param("description", "Mô tả " + title)
            .param("setRoomMaintenance", "false")
            .param("cause", "TENANT_USAGE"));
        MaintenanceTicket ticket = ticketRepository.findAll().stream()
            .filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow();
        mockMvc.perform(as(post("/maintenance/" + ticket.getId() + "/accept"), ADMIN));
        mockMvc.perform(as(post("/maintenance/" + ticket.getId() + "/resolve"), ADMIN)
            .param("resolutionNotes", "Đã sửa")
            .param("repairCost", cost)
            .param("releaseRoomToAvailable", "false"));
        return invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();
    }

    private void pay(String code, String amount) throws Exception {
        String payload = """
            {"id": %d, "gateway": "MBBank", "transactionDate": "2026-10-02 08:00:00", "accountNumber": "0389999999",
             "content": "TT %s", "transferType": "in", "transferAmount": %s, "accumulated": 1}
            """.formatted(webhookSeq++, code, amount);
        mockMvc.perform(post("/api/v1/payments/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());
    }

    private Invoice issueMonthly() throws Exception {
        mockMvc.perform(as(post(CONTRACT_URL + "/invoices"), ADMIN).param("period", YearMonth.now().toString()))
            .andExpect(flash().attributeExists("successMessage"));
        return invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).stream()
            .filter(i -> i.getType() == InvoiceType.RENT).findFirst().orElseThrow();
    }

    private MaintenanceStatus ticketStatus(Invoice fee) {
        return ticketRepository.findById(fee.getTicketId()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("Unpaid fees are itemized into the month-end invoice; the paid fee is not; paying the month completes the rolled tickets")
    void monthEndInvoiceItemizesUnpaidFees() throws Exception {
        Invoice unpaidA = feeInvoice("Vỡ kính cửa sổ", "450000");
        Invoice unpaidB = feeInvoice("Hỏng ổ khóa", "300000");
        Invoice paid = feeInvoice("Hỏng vòi sen", "180000");
        pay(paid.getPaymentCode(), "180000");
        assertEquals(MaintenanceStatus.RESOLVED, ticketStatus(paid));

        Invoice monthly = issueMonthly();

        // 3.500.000 tiền thuê + 450.000 + 300.000; khoản đã trả không có mặt
        assertEquals(0, new java.math.BigDecimal("4250000").compareTo(monthly.getTotalAmount()));
        assertEquals(3, monthly.getItems().size());
        assertTrue(monthly.getItems().stream().anyMatch(i -> i.name().equals("Phí sửa chữa: Vỡ kính cửa sổ")));
        assertTrue(monthly.getItems().stream().anyMatch(i -> i.name().equals("Phí sửa chữa: Hỏng ổ khóa")));
        assertFalse(monthly.getItems().stream().anyMatch(i -> i.name().contains("Hỏng vòi sen")));

        // Hai khoản đã gộp không còn thanh toán riêng; khoản đã trả không bị đụng tới
        assertTrue(invoiceRepository.findById(unpaidA.getId()).orElseThrow().isConsolidated());
        assertTrue(invoiceRepository.findById(unpaidB.getId()).orElseThrow().isConsolidated());
        assertFalse(invoiceRepository.findById(paid.getId()).orElseThrow().isConsolidated());
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, ticketStatus(unpaidA));

        // Thanh toán riêng bằng mã cũ không còn được ghi nhận
        pay(unpaidA.getPaymentCode(), "450000");
        assertEquals(0, invoiceRepository.findById(unpaidA.getId()).orElseThrow().getPaidAmount().signum());
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, ticketStatus(unpaidA));

        // Hóa đơn tháng hiển thị chi tiết các mục
        mockMvc.perform(as(get("/invoices/" + monthly.getId() + "/pay"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chi tiết hóa đơn")))
            .andExpect(content().string(containsString("Phí sửa chữa: Vỡ kính cửa sổ")))
            .andExpect(content().string(containsString("Phí sửa chữa: Hỏng ổ khóa")))
            .andExpect(content().string(containsString("4,250,000 đ")))
            .andExpect(content().string(not(containsString("Hỏng vòi sen"))));
        // Khoản đã gộp trỏ về hóa đơn tháng
        mockMvc.perform(as(get("/invoices/" + unpaidA.getId() + "/pay"), TENANT))
            .andExpect(content().string(containsString("đã được gộp vào hóa đơn tháng")))
            .andExpect(content().string(containsString("/invoices/" + monthly.getId() + "/pay")));
        mockMvc.perform(as(get("/maintenance"), TENANT))
            .andExpect(content().string(containsString("Thanh toán (hóa đơn tháng)")));

        // Trả đủ hóa đơn tháng: các phiếu đã gộp hoàn tất và sang tab "Đã hoàn thành"
        pay(monthly.getPaymentCode(), "4250000");
        assertEquals(InvoiceStatus.PAID, invoiceRepository.findById(monthly.getId()).orElseThrow().getStatus());
        assertEquals(MaintenanceStatus.RESOLVED, ticketStatus(unpaidA));
        assertEquals(MaintenanceStatus.RESOLVED, ticketStatus(unpaidB));
        mockMvc.perform(as(get("/maintenance?tab=open"), ADMIN))
            .andExpect(content().string(not(containsString("Vỡ kính cửa sổ"))))
            .andExpect(content().string(not(containsString("Hỏng ổ khóa"))));
        mockMvc.perform(as(get("/maintenance?tab=done"), ADMIN))
            .andExpect(content().string(containsString("Vỡ kính cửa sổ")))
            .andExpect(content().string(containsString("Hỏng ổ khóa")));
    }

    @Test
    @DisplayName("A fee that is already part-paid stays standalone and is not rolled into the month invoice")
    void partPaidFeeStaysStandalone() throws Exception {
        Invoice fee = feeInvoice("Vỡ gương", "200000");
        pay(fee.getPaymentCode(), "50000");

        Invoice monthly = issueMonthly();
        assertEquals(0, new java.math.BigDecimal("3500000").compareTo(monthly.getTotalAmount()));
        assertEquals(1, monthly.getItems().size());
        assertFalse(invoiceRepository.findById(fee.getId()).orElseThrow().isConsolidated());

        pay(fee.getPaymentCode(), "150000");
        assertEquals(MaintenanceStatus.RESOLVED, ticketStatus(fee));
    }

    @Test
    @DisplayName("A rolled-in fee cannot be waived from the ticket; the month invoice without fees has a single rent line")
    void waiveRolledFeeRefused() throws Exception {
        Invoice fee = feeInvoice("Gãy tay nắm", "120000");
        issueMonthly();

        mockMvc.perform(as(post("/maintenance/" + fee.getTicketId() + "/waive-fee"), ADMIN)
                .param("reason", "Miễn"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(MaintenanceStatus.AWAITING_PAYMENT, ticketStatus(fee));
        assertTrue(invoiceRepository.findById(fee.getId()).orElseThrow().isConsolidated());
    }

    @Test
    @DisplayName("Fees created after the month invoice are not in it")
    void laterFeeNotIncluded() throws Exception {
        Invoice monthly = issueMonthly();
        assertEquals(1, monthly.getItems().size());

        Invoice later = feeInvoice("Hỏng quạt", "90000");
        assertFalse(later.isConsolidated());
        assertTrue(later.isPayable());
        assertEquals(1, invoiceRepository.findById(monthly.getId()).orElseThrow().getItems().size());
        assertEquals(UUID.class, later.getId().getClass());
    }
}
