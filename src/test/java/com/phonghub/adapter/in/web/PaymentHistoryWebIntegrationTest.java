package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoicePaymentRecordRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentClaimRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoicePaymentRecord;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.PaymentClaim;
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

/** #248: lịch sử thanh toán của người thuê — gom SePay, báo chuyển khoản, ghi nhận tay, cấn trừ cọc; phân quyền theo vai trò. */
class PaymentHistoryWebIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String MINE = "/api/me/payments";
    private static final String OF_TENANT = "/api/tenants/" + DataSeeder.TENANT_RECORD_ID + "/payments";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryPaymentRepository payments;
    @Autowired private InMemoryPaymentClaimRepository claims;
    @Autowired private InMemoryInvoicePaymentRecordRepository records;

    private Invoice invoice;

    @BeforeEach
    void seed() {
        clear();
        invoice = invoices.save(new Invoice(UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, DataSeeder.ROOM_101_ID,
            DataSeeder.TENANT_RECORD_ID, 8, 2026, new BigDecimal("3500000"), new BigDecimal("3500000"),
            new BigDecimal("3500000"), LocalDate.of(2026, 8, 5), InvoiceStatus.PAID, "PHHIST0001", InvoiceType.RENT, null,
            Instant.parse("2026-08-02T01:00:00Z"), Instant.now(), Instant.now()));
        // 1 giao dịch SePay đã khớp, 1 giao dịch chưa gắn hóa đơn (không được hiện)
        payments.saveIfNew(tx(920001, DataSeeder.CONTRACT_1_ID, invoice.getId(), "3500000", "3500000", null));
        payments.saveIfNew(tx(920002, DataSeeder.CONTRACT_1_ID, null, "100000", "0", PaymentReviewReason.UNMATCHED));
        // báo chuyển khoản: 1 chờ, 1 bị từ chối, 1 chủ trọ ghi nhận
        PaymentClaim pending = PaymentClaim.submitted(invoice, DataSeeder.PROP_1_ID, new BigDecimal("100000"),
            PaymentClaim.Method.BANK_TRANSFER, "chuyển lúc 8h", UUID.fromString(TENANT_1));
        claims.save(pending);
        PaymentClaim rejected = PaymentClaim.submitted(invoice, DataSeeder.PROP_1_ID, new BigDecimal("200000"),
            PaymentClaim.Method.CASH, null, UUID.fromString(TENANT_1)).rejected(UUID.fromString(OWNER_1), "Chưa nhận được tiền");
        claims.save(rejected);
        claims.save(PaymentClaim.recorded(invoice, DataSeeder.PROP_1_ID, new BigDecimal("300000"),
            PaymentClaim.Method.CASH, null, UUID.fromString(OWNER_1)));
        records.save(new InvoicePaymentRecord(UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, invoice.getId(),
            new BigDecimal("500000"), InvoicePaymentRecord.Type.DEPOSIT_OFFSET, Instant.parse("2026-08-03T01:00:00Z"),
            UUID.fromString(OWNER_1), null));
    }

    @AfterEach
    void clear() {
        invoices.clear();
        payments.clear();
        claims.clear();
        records.clear();
    }

    private static PaymentTransaction tx(long sepayId, UUID contractId, UUID invoiceId, String amount, String applied,
                                         PaymentReviewReason reason) {
        return new PaymentTransaction(UUID.randomUUID(), sepayId, "MBBank", "2026-08-02 08:00:00", "0389999999", null,
            "in", new BigDecimal(amount), null, null, "noi dung", "REF" + sepayId, null, PaymentStatus.SUCCESS,
            contractId, invoiceId, Instant.parse("2026-08-02T01:00:00Z"), new BigDecimal(applied), BigDecimal.ZERO, reason);
    }

    @Test
    @DisplayName("Người thuê thấy đủ 5 dòng: SePay, báo chuyển khoản chờ, bị từ chối kèm lý do, chủ trọ ghi nhận, cấn trừ cọc; không thấy giao dịch chưa gắn hóa đơn")
    void tenantSeesAllSources() throws Exception {
        mockMvc.perform(get(MINE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(5)))
            .andExpect(jsonPath("$.totalRows").value(5))
            .andExpect(jsonPath("$.totalRecorded").value(4300000))
            .andExpect(jsonPath("$.rows[?(@.method=='SEPAY')]", hasSize(1)))
            .andExpect(jsonPath("$.rows[?(@.method=='DEPOSIT_OFFSET')]", hasSize(1)))
            .andExpect(jsonPath("$.rows[?(@.status=='REJECTED')].rejectReason",
                org.hamcrest.Matchers.contains("Chưa nhận được tiền")))
            .andExpect(jsonPath("$.rows[?(@.status=='PENDING')]", hasSize(1)));
        mockMvc.perform(get(MINE).header("X-User-Id", TENANT_1).param("status", "REJECTED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows", hasSize(1)));
        mockMvc.perform(get(MINE).header("X-User-Id", TENANT_1).param("from", "2026-08-03").param("to", "2026-08-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[?(@.method=='DEPOSIT_OFFSET')]", hasSize(1)));
    }

    @Test
    @DisplayName("Trang web của người thuê hiển thị lịch sử")
    void tenantPage() throws Exception {
        mockMvc.perform(get("/account/payments").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lịch sử thanh toán")))
            .andExpect(content().string(containsString("Cấn trừ cọc")))
            .andExpect(content().string(containsString("Chưa nhận được tiền")));
    }

    @Test
    @DisplayName("Chủ trọ và nhân viên có quyền thu tiền xem của người thuê trong nhà trọ mình; chủ trọ khác 403")
    void managersSeeOwnPropertyTenants() throws Exception {
        for (String user : new String[] {OWNER_1, STAFF_1}) {
            mockMvc.perform(get(OF_TENANT).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(5)));
        }
        mockMvc.perform(get(OF_TENANT).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(get("/tenants/" + DataSeeder.TENANT_RECORD_ID + "/payments").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lịch sử thanh toán")));
    }

    @Test
    @DisplayName("Quản trị viên và kỹ thuật viên bị 403 (admin không xem tiền, #163); người thuê không xem của người khác")
    void forbiddenRoles() throws Exception {
        mockMvc.perform(get(OF_TENANT).header("X-User-Id", ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get(MINE).header("X-User-Id", ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get(OF_TENANT).header("X-User-Id", TECH_1)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants/" + UUID.randomUUID() + "/payments").header("X-User-Id", TENANT_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/tenants/" + DataSeeder.TENANT_RECORD_ID + "/payments").header("X-User-Id", ADMIN))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Tham số lọc sai trả 400")
    void badFilter() throws Exception {
        mockMvc.perform(get(MINE).header("X-User-Id", TENANT_1).param("status", "NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(get(MINE).header("X-User-Id", TENANT_1).param("from", "2026-09-02").param("to", "2026-09-01"))
            .andExpect(status().isBadRequest());
    }
}
