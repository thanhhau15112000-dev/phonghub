package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.model.BillingCycle;
import com.phonghub.domain.model.Contract;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** #292: phát hành hóa đơn thủ công để lại dấu vết trong nhật ký của chủ trọ. */
class InvoiceIssueAuditIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InvoiceRepositoryPort invoices;
    @Autowired private ContractRepositoryPort contracts;

    @Test
    @DisplayName("Chủ trọ phát hành hóa đơn thì nhật ký có dòng \"Phát hành hóa đơn\" kèm kỳ")
    void issuingInvoiceIsAudited() throws Exception {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        YearMonth period = contract.billingCycles().stream()
            .filter(cycle -> !cycle.startDate().isAfter(LocalDate.now()))
            .map(BillingCycle::period)
            .filter(p -> invoices.findActiveRentByContractIdAndPeriod(contract.getId(), p.getYear(), p.getMonthValue())
                .isEmpty())
            .max(YearMonth::compareTo)
            .orElseThrow(() -> new AssertionError("Không có kỳ thanh toán còn trống."));

        mockMvc.perform(post("/contracts/" + contract.getId() + "/invoices").header("X-User-Id", OWNER_1)
                .param("period", period.toString()))
            .andExpect(flash().attribute("successMessage", containsString("Đã tạo kỳ thanh toán")));

        mockMvc.perform(get("/audit").header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("Phát hành hóa đơn")));
    }
}
