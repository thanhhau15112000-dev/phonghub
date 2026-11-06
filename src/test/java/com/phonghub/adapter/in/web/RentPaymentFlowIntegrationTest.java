package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RentPaymentFlowIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
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

    @Test
    @DisplayName("Tenant sees empty payment section and cannot issue invoices")
    void tenantCannotIssueInvoice() throws Exception {
        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thanh toán tiền thuê")))
            .andExpect(content().string(containsString("Chưa có kỳ thanh toán nào")))
            .andExpect(content().string(not(containsString("Tạo kỳ thanh toán"))));

        mockMvc.perform(post(CONTRACT_URL + "/invoices")
                .header("X-User-Id", TENANT)
                .param("period", YearMonth.now().toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("errorMessage"));

        assertTrue(invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).isEmpty());
    }

    @Test
    @DisplayName("Owner issues invoice, tenant sees transfer code, SePay webhook settles it")
    void issueAndSettleInvoice() throws Exception {
        String period = YearMonth.now().toString();

        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", OWNER).param("period", period))
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("successMessage"));

        // Trùng kỳ bị từ chối
        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", OWNER).param("period", period))
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("errorMessage"));

        List<Invoice> invoices = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID);
        assertEquals(1, invoices.size());
        Invoice invoice = invoices.get(0);

        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nội dung chuyển khoản")))
            .andExpect(content().string(containsString(invoice.getPaymentCode())))
            .andExpect(content().string(containsString("3.500.000 đ")));

        String payload = """
            {
              "id": 880001,
              "gateway": "MBBank",
              "transactionDate": "2026-10-02 08:00:00",
              "accountNumber": "0389999999",
              "code": null,
              "content": "NGUYEN VAN A CHUYEN TIEN %s",
              "transferType": "in",
              "transferAmount": 3500000,
              "accumulated": 9000000,
              "referenceCode": "MB.880001"
            }
            """.formatted(invoice.getPaymentCode());

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        Invoice settled = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, settled.getStatus());

        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đã thanh toán")))
            .andExpect(content().string(not(containsString("Nội dung chuyển khoản:"))));
    }

    @Test
    @DisplayName("Tenant dashboard shows the next unpaid invoice, own residence, and hides other rooms' tickets and manager table")
    void tenantDashboardShowsInvoice() throws Exception {
        mockMvc.perform(get("/dashboard").header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nơi ở của tôi")))
            .andExpect(content().string(containsString("Hiện không có khoản nào cần thanh toán")))
            .andExpect(content().string(not(containsString("Nhà trọ trong phạm vi"))))
            .andExpect(content().string(not(containsString("Sửa vòi nước và kiểm tra máy lạnh"))));

        mockMvc.perform(post(CONTRACT_URL + "/invoices")
                .header("X-User-Id", OWNER)
                .param("period", YearMonth.now().toString()))
            .andExpect(redirectedUrl(CONTRACT_URL));
        String code = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0).getPaymentCode();

        mockMvc.perform(get("/dashboard").header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thanh toán tiền thuê")))
            .andExpect(content().string(containsString(code)))
            .andExpect(content().string(containsString("3.500.000 đ")))
            .andExpect(content().string(containsString("#payments")));
    }

    @Test
    @DisplayName("#163: admin không lập/xem hóa đơn; trang hợp đồng của admin không có phần thanh toán")
    void adminHasNoAccessToInvoices() throws Exception {
        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", ADMIN)
                .param("period", YearMonth.now().toString()))
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).isEmpty());

        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", OWNER)
            .param("period", YearMonth.now().toString()));
        Invoice invoice = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);

        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"payments\""))))
            .andExpect(content().string(not(containsString(invoice.getPaymentCode()))))
            .andExpect(content().string(not(containsString("Tạo kỳ thanh toán"))));
        mockMvc.perform(get("/invoices/" + invoice.getId() + "/pay").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", OWNER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"payments\"")))
            .andExpect(content().string(containsString(invoice.getPaymentCode())));
    }

    @Test
    @DisplayName("Admin dashboard keeps the property table and does not show tenant payment card")
    void adminDashboardUnchanged() throws Exception {
        mockMvc.perform(get("/dashboard").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ trong phạm vi")))
            .andExpect(content().string(not(containsString("Nơi ở của tôi"))))
            .andExpect(content().string(containsString("Sửa vòi nước và kiểm tra máy lạnh")));
    }

    @Test
    @DisplayName("Issue #199: Partial then full payment via SePay webhook updates live status and prevents duplicate credit")
    void partialThenFullPaymentUpdatesStatusAndPreventsDuplicate() throws Exception {
        String period = YearMonth.now().toString();
        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", OWNER).param("period", period))
            .andExpect(redirectedUrl(CONTRACT_URL));

        Invoice invoice = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);
        String payUrl = "/invoices/" + invoice.getId() + "/pay";
        String statusUrl = "/invoices/" + invoice.getId() + "/status";

        // Tenant accesses pay page: both live alerts and polling logic are rendered
        mockMvc.perform(get(payUrl).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"paid-live\"")))
            .andExpect(content().string(containsString("id=\"partially-paid-live\"")))
            .andExpect(content().string(containsString("fetch(statusUrl")));

        // Initial status: unpaid
        mockMvc.perform(get(statusUrl).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"))
            .andExpect(jsonPath("$.paid").value(false))
            .andExpect(jsonPath("$.partiallyPaid").value(false))
            .andExpect(jsonPath("$.paidAmount").value(0))
            .andExpect(jsonPath("$.remaining").value(3500000));

        // 1. Partial payment (1.000.000)
        String partialPayload = """
            {
              "id": 880010,
              "gateway": "MBBank",
              "transactionDate": "2026-10-02 08:00:00",
              "accountNumber": "0389999999",
              "code": null,
              "content": "Chuyen tien %s dot 1",
              "transferType": "in",
              "transferAmount": 1000000,
              "accumulated": 5000000,
              "referenceCode": "MB.880010"
            }
            """.formatted(invoice.getPaymentCode());

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(partialPayload))
            .andExpect(status().isOk());

        // Status must be PARTIALLY_PAID with correct remaining amount
        mockMvc.perform(get(statusUrl).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andExpect(jsonPath("$.paid").value(false))
            .andExpect(jsonPath("$.partiallyPaid").value(true))
            .andExpect(jsonPath("$.paidAmount").value(1000000))
            .andExpect(jsonPath("$.remaining").value(2500000));

        // 2. Duplicate webhook with same ID does not double credit
        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(partialPayload))
            .andExpect(status().isOk());

        Invoice afterDuplicate = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(0, new java.math.BigDecimal("1000000").compareTo(afterDuplicate.getPaidAmount()));

        // 3. Remaining payment (2.500.000) where code is in description field
        String fullPayload = """
            {
              "id": 880011,
              "gateway": "MBBank",
              "transactionDate": "2026-10-02 08:30:00",
              "accountNumber": "0389999999",
              "code": null,
              "content": "Thanh toan tien phong",
              "description": "Hop dong phong P101 %s",
              "transferType": "in",
              "transferAmount": 2500000,
              "accumulated": 7500000,
              "referenceCode": "MB.880011"
            }
            """.formatted(invoice.getPaymentCode());

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullPayload))
            .andExpect(status().isOk());

        // Status is now PAID and remaining amount is 0
        mockMvc.perform(get(statusUrl).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paid").value(true))
            .andExpect(jsonPath("$.partiallyPaid").value(false))
            .andExpect(jsonPath("$.paidAmount").value(3500000))
            .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    @DisplayName("Issue #199: Unmatched webhook payment is saved with review reason UNMATCHED and does not credit invoice")
    void unmatchedWebhookIsSavedForReview() throws Exception {
        String unmatchedPayload = """
            {
              "id": 880099,
              "gateway": "MBBank",
              "transactionDate": "2026-10-02 09:00:00",
              "accountNumber": "0389999999",
              "code": null,
              "content": "Chuyen tien khong kem ma hop dong",
              "transferType": "in",
              "transferAmount": 500000,
              "accumulated": 8000000,
              "referenceCode": "MB.880099"
            }
            """;

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(unmatchedPayload))
            .andExpect(status().isOk());

        var tx = paymentRepository.findBySepayId(880099L).orElseThrow();
        assertEquals(com.phonghub.domain.model.PaymentReviewReason.UNMATCHED, tx.reviewReason());
        assertEquals(0, tx.appliedAmount().signum());
        assertEquals(0, new java.math.BigDecimal("500000").compareTo(tx.excessAmount()));
    }
}
