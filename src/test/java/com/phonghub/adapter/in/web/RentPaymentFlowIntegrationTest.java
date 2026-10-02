package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    @DisplayName("Admin issues invoice, tenant sees transfer code, SePay webhook settles it")
    void issueAndSettleInvoice() throws Exception {
        String period = YearMonth.now().toString();

        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", ADMIN).param("period", period))
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("successMessage"));

        // Trùng kỳ bị từ chối
        mockMvc.perform(post(CONTRACT_URL + "/invoices").header("X-User-Id", ADMIN).param("period", period))
            .andExpect(redirectedUrl(CONTRACT_URL))
            .andExpect(flash().attributeExists("errorMessage"));

        List<Invoice> invoices = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID);
        assertEquals(1, invoices.size());
        Invoice invoice = invoices.get(0);

        mockMvc.perform(get(CONTRACT_URL).header("X-User-Id", TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nội dung chuyển khoản")))
            .andExpect(content().string(containsString(invoice.getPaymentCode())))
            .andExpect(content().string(containsString("3,500,000 đ")));

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
    @DisplayName("Raw SePay transaction listing is forbidden for tenants")
    void tenantCannotListTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/payments/transactions").header("X-User-Id", TENANT))
            .andExpect(status().isForbidden());
    }
}
