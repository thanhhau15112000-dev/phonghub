package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Thanh toán ngoài cổng SePay (#205): người thuê báo, người thu tiền xác nhận / từ chối / ghi nhận trực tiếp. */
class ManualPaymentIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryNotificationRepository notifications;

    /** Hóa đơn tiền thuê 3.500.000 đ của hợp đồng mẫu, chưa trả. */
    private Invoice rentInvoice() {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.now().plusMonths(1), "PHMANUAL01");
        return invoices.save(invoice);
    }

    private ResultActions claim(Invoice invoice, String user, String amount, String method) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/payment-claims").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":" + amount + ",\"method\":\"" + method + "\",\"note\":\"Đã nộp\"}"));
    }

    private ResultActions record(Invoice invoice, String user, String amount) throws Exception {
        return mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/manual-payments").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":" + amount + ",\"method\":\"CASH\"}"));
    }

    private InvoiceStatus statusOf(Invoice invoice) {
        return invoices.findById(invoice.getId()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("Tenant claims a cash payment, the collector confirms it, the invoice is paid and a second confirm is a 409")
    void claimAndConfirm() throws Exception {
        Invoice invoice = rentInvoice();

        String body = claim(invoice, TENANT_1, "3500000", "CASH").andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        String claimId = JsonPath.read(body, "$.id");
        assertEquals(InvoiceStatus.ISSUED, statusOf(invoice), "chưa xác nhận thì chưa cộng tiền");
        claim(invoice, TENANT_1, "100000", "CASH").andExpect(status().isConflict());
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/payment-claims").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + claimId + "')]").exists());

        for (String denied : new String[] {TENANT_1, TECH_1, ADMIN, OWNER_2, STAFF_2}) {
            mockMvc.perform(post("/api/payment-claims/" + claimId + "/confirm").header("X-User-Id", denied))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/payment-claims/" + claimId + "/confirm").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertEquals(InvoiceStatus.PAID, statusOf(invoice));
        assertEquals(0, new BigDecimal("3500000").compareTo(invoices.findById(invoice.getId()).orElseThrow().getPaidAmount()));
        mockMvc.perform(post("/api/payment-claims/" + claimId + "/confirm").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        assertEquals(0, new BigDecimal("3500000").compareTo(invoices.findById(invoice.getId()).orElseThrow().getPaidAmount()),
            "xác nhận lần hai không cộng tiền thêm");
        assertNotNull(notifications.findAll().stream().filter(n -> TENANT_1.equals(String.valueOf(n.targetUserId()))
            && n.title().contains("Thanh toán đã được xác nhận")).findAny().orElse(null));
        claim(invoice, TENANT_1, "1000", "CASH").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A partial confirmation leaves the invoice partly paid; the collector can reject with a reason")
    void partialAndReject() throws Exception {
        Invoice invoice = rentInvoice();

        String first = claim(invoice, TENANT_1, "1000000", "BANK_TRANSFER").andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/payment-claims/" + JsonPath.<String>read(first, "$.id") + "/confirm")
            .header("X-User-Id", STAFF_1)).andExpect(status().isOk());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, statusOf(invoice));

        String second = claim(invoice, TENANT_1, "500000", "CASH").andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String secondId = JsonPath.read(second, "$.id");
        mockMvc.perform(post("/api/payment-claims/" + secondId + "/reject").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payment-claims/" + secondId + "/reject").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Chưa nhận được tiền\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectReason").value("Chưa nhận được tiền"));
        mockMvc.perform(post("/api/payment-claims/" + secondId + "/confirm").header("X-User-Id", STAFF_1))
            .andExpect(status().isConflict());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, statusOf(invoice));
        assertEquals(0, new BigDecimal("2500000").compareTo(invoices.findById(invoice.getId()).orElseThrow().remainingAmount()));
    }

    @Test
    @DisplayName("Amounts above what is owed are refused; only the tenant of the contract can claim")
    void amountAndRoleValidation() throws Exception {
        Invoice invoice = rentInvoice();

        claim(invoice, TENANT_1, "3500001", "CASH").andExpect(status().isConflict());
        claim(invoice, TENANT_1, "0", "CASH").andExpect(status().isBadRequest());
        claim(invoice, OWNER_1, "100000", "CASH").andExpect(status().isForbidden());
        claim(invoice, TECH_1, "100000", "CASH").andExpect(status().isForbidden());
        claim(invoice, OWNER_2, "100000", "CASH").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/" + UUID.randomUUID() + "/payment-claims").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"method\":\"CASH\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("A collector can record a payment directly; staff without the collect permission and admin cannot")
    void directRecording() throws Exception {
        Invoice invoice = rentInvoice();

        record(invoice, TENANT_1, "100000").andExpect(status().isForbidden());
        record(invoice, ADMIN, "100000").andExpect(status().isForbidden());
        record(invoice, TECH_1, "100000").andExpect(status().isForbidden());
        record(invoice, OWNER_2, "100000").andExpect(status().isForbidden());
        record(invoice, OWNER_1, "4000000").andExpect(status().isConflict());

        record(invoice, OWNER_1, "3500000").andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.source").value("MANAGER_RECORD"));
        assertEquals(InvoiceStatus.PAID, statusOf(invoice));
        record(invoice, OWNER_1, "1").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("The invoice page offers the claim form to the tenant and the record form to the collector; the contract page lists pending claims")
    void pagesShowTheRightActions() throws Exception {
        Invoice invoice = rentInvoice();
        claim(invoice, TENANT_1, "500000", "CASH").andExpect(status().isCreated());

        mockMvc.perform(get("/invoices/" + invoice.getId() + "/pay").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"claim-card\"")))
            .andExpect(content().string(containsString("Chờ xác nhận")));
        mockMvc.perform(get("/invoices/" + invoice.getId() + "/pay").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"record-card\"")));
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"pending-claims\"")))
            .andExpect(content().string(containsString("Xác nhận đã nhận tiền")));
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"pending-claims\""))));
    }
}
