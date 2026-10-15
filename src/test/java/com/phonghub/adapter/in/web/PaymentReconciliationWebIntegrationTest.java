package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** #82: API và trang đối soát, biên lai trên dữ liệu mẫu (hợp đồng mẫu ở PROP_1 của OWNER_1, người thuê TENANT_1). */
class PaymentReconciliationWebIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String API = "/api/v1/payments/reconciliation";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryPaymentRepository payments;

    private Invoice paid;
    private Invoice unpaid;

    @BeforeEach
    void addPayments() {
        // Context Spring dùng chung giữa các lớp test: dọn giao dịch/hóa đơn do lớp khác để lại.
        clear();
        paid = invoices.save(invoice(8, "3500000", "3500000", InvoiceStatus.PAID, "PHRCPT0001"));
        unpaid = invoices.save(invoice(9, "3500000", "0", InvoiceStatus.ISSUED, "PHRCPT0002"));
        payments.saveIfNew(tx(910001, DataSeeder.CONTRACT_1_ID, paid.getId(), "3500000", "3500000", "0", null));
        payments.saveIfNew(tx(910002, DataSeeder.CONTRACT_1_ID, paid.getId(), "200000", "0", "200000",
            PaymentReviewReason.INVOICE_NOT_PAYABLE));
        payments.saveIfNew(tx(910003, null, null, "100000", "0", "100000", PaymentReviewReason.UNMATCHED));
    }

    @AfterEach
    void clear() {
        invoices.clear();
        payments.clear();
    }

    private static Invoice invoice(int month, String total, String paidAmount, InvoiceStatus status, String code) {
        return new Invoice(UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, DataSeeder.ROOM_101_ID, DataSeeder.TENANT_RECORD_ID,
            month, 2026, new BigDecimal(total), new BigDecimal(total), new BigDecimal(paidAmount),
            LocalDate.of(2026, month, 5), status, code, InvoiceType.RENT, null,
            status == InvoiceStatus.PAID ? Instant.parse("2026-08-02T01:00:00Z") : null, Instant.now(), Instant.now());
    }

    private static PaymentTransaction tx(long sepayId, UUID contractId, UUID invoiceId, String amount, String applied,
                                         String excess, PaymentReviewReason reason) {
        return new PaymentTransaction(UUID.randomUUID(), sepayId, "MBBank", "2026-08-02 08:00:00", "0389999999", null,
            "in", new BigDecimal(amount), null, null, "noi dung", "REF" + sepayId, null, PaymentStatus.SUCCESS,
            contractId, invoiceId, Instant.parse("2026-08-02T01:00:00Z").plusSeconds(sepayId % 10), new BigDecimal(applied),
            new BigDecimal(excess), reason);
    }

    @Test
    @DisplayName("API đối soát: owner/staff có quyền thu tiền thấy giao dịch nhà mình, admin thấy cả giao dịch chưa khớp")
    void reconciliationScopes() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(2)))
            .andExpect(jsonPath("$.counts.MATCHED").value(1))
            .andExpect(jsonPath("$.counts.NEEDS_REVIEW").value(1))
            .andExpect(jsonPath("$.rows[0].propertyName").value("Nhà trọ Xanh - Quận 7"));
        mockMvc.perform(get(API).header("X-User-Id", STAFF_1).param("status", "MATCHED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(1)))
            .andExpect(jsonPath("$.rows[0].sepayId").value(910001));
        mockMvc.perform(get(API).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(3)))
            .andExpect(jsonPath("$.counts.UNMATCHED").value(1));
        mockMvc.perform(get(API).header("X-User-Id", OWNER_2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(0)));
    }

    @Test
    @DisplayName("API đối soát: 403 cho kỹ thuật viên, người thuê, owner lọc nhà người khác; 400 tham số sai")
    void reconciliationRejections() throws Exception {
        for (String user : new String[] {TECH_1, TENANT_1}) {
            mockMvc.perform(get(API).header("X-User-Id", user)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(API).header("X-User-Id", OWNER_2).param("propertyId", DataSeeder.PROP_1_ID.toString()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("status", "NOPE"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("from", "2026-09-02").param("to", "2026-09-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API biên lai: người thuê và owner xem được; 403 người ngoài/kỹ thuật viên; 409 chưa trả; 404 không có")
    void receiptApi() throws Exception {
        String receipt = "/api/invoices/" + paid.getId() + "/receipt";
        mockMvc.perform(get(receipt).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.receiptNumber").value("BL-PHRCPT0001"))
            .andExpect(jsonPath("$.payments", hasSize(1)))
            .andExpect(jsonPath("$.payments[0].appliedAmount").value(3500000))
            .andExpect(jsonPath("$.payments[0].accountNumber").doesNotExist());
        mockMvc.perform(get(receipt).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        for (String user : new String[] {OWNER_2, TECH_1}) {
            mockMvc.perform(get(receipt).header("X-User-Id", user)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/invoices/" + unpaid.getId() + "/receipt").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/receipt").header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("trang giao dịch: owner thấy bảng đối soát và link biên lai; người thuê bị chuyển về trang chủ")
    void transactionsPage() throws Exception {
        String html = mockMvc.perform(get("/payments").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("id=\"transactionTable\""));
        assertTrue(html.contains("REF910001"));
        assertTrue(html.contains("Hóa đơn không còn nhận tiền"));
        assertTrue(html.contains("/invoices/" + paid.getId() + "/receipt"));
        assertFalse(html.contains("REF910003"), "giao dịch chưa khớp không thuộc phạm vi owner");

        String filtered = mockMvc.perform(get("/payments").header("X-User-Id", OWNER_1).param("status", "NEEDS_REVIEW"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(filtered.contains("REF910002"));
        assertFalse(filtered.contains("REF910001"));

        String foreign = mockMvc.perform(get("/payments").header("X-User-Id", OWNER_2)
                .param("propertyId", DataSeeder.PROP_1_ID.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(foreign.contains("REF910001"), "lọc nhà người khác không lộ giao dịch");

        mockMvc.perform(get("/payments").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("trang biên lai: người thuê xem/in được; chưa trả quay về trang thanh toán; người ngoài bị chặn")
    void receiptPage() throws Exception {
        String html = mockMvc.perform(get("/invoices/" + paid.getId() + "/receipt").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("BIÊN LAI THANH TOÁN"));
        assertTrue(html.contains("BL-PHRCPT0001"));
        assertTrue(html.contains("window.print()"));
        assertFalse(html.contains("0389999999"), "không in số tài khoản người chuyển");

        mockMvc.perform(get("/invoices/" + unpaid.getId() + "/receipt").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/invoices/" + unpaid.getId() + "/pay"))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(get("/invoices/" + paid.getId() + "/receipt").header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts"));

        String pay = mockMvc.perform(get("/invoices/" + paid.getId() + "/pay").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(pay.contains("id=\"receipt-link\""));
    }

    @Test
    @DisplayName("menu Giao dịch chỉ hiện với admin, owner, staff")
    void navigationLink() throws Exception {
        for (String user : new String[] {ADMIN, OWNER_1, STAFF_1}) {
            String html = mockMvc.perform(get("/").header("X-User-Id", user))
                .andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("href=\"/payments\""), user);
        }
        for (String user : new String[] {TENANT_1, TECH_1}) {
            String html = mockMvc.perform(get("/").header("X-User-Id", user))
                .andReturn().getResponse().getContentAsString();
            assertFalse(html.contains("href=\"/payments\""), user);
        }
    }
}
