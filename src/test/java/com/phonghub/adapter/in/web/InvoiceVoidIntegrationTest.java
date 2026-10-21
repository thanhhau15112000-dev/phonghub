package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** #89: hủy hóa đơn tiền thuê (VOIDED) qua API và giao diện — 400/403/404/409, sau khi hủy phát hành lại được kỳ đó. */
class InvoiceVoidIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT = DataSeeder.CONTRACT_1_ID.toString();
    private static final String REASON = "{\"reason\":\"Lập nhầm tiền thuê\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private InvoiceRepositoryPort invoices;
    @Autowired private ContractRepositoryPort contracts;

    private Contract contract;
    private YearMonth period;

    @BeforeEach
    void pickFreePeriod() {
        contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        // Kho hóa đơn in-memory dùng chung giữa các lớp test: chọn kỳ còn trống trong thời hạn hợp đồng.
        period = YearMonth.from(contract.getStartDate()).plusMonths(3);
        while (invoices.findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(), period.getMonthValue())
            .isPresent()) {
            period = period.plusMonths(1);
        }
    }

    private Invoice rent() {
        return invoices.save(Invoice.issueMonthlyRent(contract, period, invoices.newUniquePaymentCode()));
    }

    private String voidUrl(Invoice invoice) {
        return "/api/invoices/" + invoice.getId() + "/void";
    }

    @Test
    @DisplayName("API: chủ trọ hủy hóa đơn chưa có tiền; người khác 403, id lạ 404, thiếu lý do 400, hủy lần hai 409; phát hành lại được kỳ đó")
    void apiVoidAndReissue() throws Exception {
        Invoice invoice = rent();
        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isForbidden());
        // Admin không đụng tới số liệu tiền của nhà trọ (#163), kể cả hủy hóa đơn.
        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/" + UUID.randomUUID() + "/void").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isNotFound());
        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VOIDED"));
        assertEquals(InvoiceStatus.VOIDED, invoices.findById(invoice.getId()).orElseThrow().getStatus());
        mockMvc.perform(post(voidUrl(invoice)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/contracts/" + CONTRACT + "/invoices").header("X-User-Id", OWNER_1)
                .param("period", period.toString()))
            .andExpect(flash().attribute("successMessage", containsString("Đã tạo kỳ thanh toán")));
        assertTrue(invoices.findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(),
            period.getMonthValue()).isPresent(), "kỳ đã hủy được phát hành lại");
    }

    @Test
    @DisplayName("API: hóa đơn đã có tiền, phí sửa chữa, phí đã gộp hoặc hóa đơn đang gộp phí đều 409")
    void apiConflicts() throws Exception {
        Invoice paid = rent();
        paid.applyPayment(new BigDecimal("100000"), Instant.now());
        invoices.save(paid);
        mockMvc.perform(post(voidUrl(paid)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đã nhận")));

        Invoice fee = invoices.save(Invoice.issueMaintenanceFee(contract, UUID.randomUUID(), new BigDecimal("200000"),
            invoices.newUniquePaymentCode(), LocalDate.now()));
        mockMvc.perform(post(voidUrl(fee)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("miễn phí ở phiếu bảo trì")));

        period = period.plusMonths(1);
        while (invoices.findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(), period.getMonthValue())
            .isPresent()) {
            period = period.plusMonths(1);
        }
        Invoice monthly = rent();
        fee.consolidateInto(monthly.getId());
        invoices.save(fee);
        mockMvc.perform(post(voidUrl(monthly)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đang gộp 1 khoản phí")));
        mockMvc.perform(post(voidUrl(fee)).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(REASON))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Giao diện: hóa đơn chưa có tiền có nút hủy kèm lý do; hủy xong hiện trạng thái 'Đã hủy'")
    void uiVoid() throws Exception {
        Invoice invoice = rent();
        String page = mockMvc.perform(get("/contracts/" + CONTRACT).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("/invoices/" + invoice.getId() + "/void"));
        String tenantPage = mockMvc.perform(get("/contracts/" + CONTRACT).header("X-User-Id", TENANT_1))
            .andReturn().getResponse().getContentAsString();
        assertTrue(!tenantPage.contains("/invoices/" + invoice.getId() + "/void"), "người thuê không thấy nút hủy");

        mockMvc.perform(post("/contracts/" + CONTRACT + "/invoices/" + invoice.getId() + "/void")
                .header("X-User-Id", OWNER_1).param("reason", "Sai đơn giá"))
            .andExpect(redirectedUrl("/contracts/" + CONTRACT))
            .andExpect(flash().attribute("successMessage", containsString("Đã hủy hóa đơn kỳ")));
        mockMvc.perform(post("/contracts/" + CONTRACT + "/invoices/" + invoice.getId() + "/void")
                .header("X-User-Id", OWNER_1).param("reason", "lần hai"))
            .andExpect(flash().attribute("errorMessage", containsString("Không thể hủy hóa đơn ở trạng thái VOIDED")));
    }
}
