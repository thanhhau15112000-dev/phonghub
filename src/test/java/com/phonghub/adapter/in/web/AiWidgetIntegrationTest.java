package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "phonghub.ai.enabled=false")
class AiWidgetIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryInvoiceRepository invoices;

    @Test
    void widgetContextWorksForAllFiveRolesAndFloatingButtonRenders() throws Exception {
        for (String[] pair : new String[][] {
            {ADMIN, "ADMIN"}, {OWNER_1, "OWNER"}, {STAFF_1, "STAFF"}, {TECH_1, "TECHNICIAN"}, {TENANT_1, "TENANT"}
        }) {
            mockMvc.perform(get("/api/v1/ai/widget-context").header("X-User-Id", pair[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(pair[1]))
                .andExpect(jsonPath("$.contexts").isArray())
                .andExpect(jsonPath("$.quickPrompts").isArray());

            mockMvc.perform(get("/dashboard").header("X-User-Id", pair[0]))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"phonghubAiToggleBtn\"")));
        }
    }

    @Test
    void tenantDashboardShowsAnalyzeButtonForPayableInvoiceAndContractHidesForAdmin() throws Exception {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        Invoice inv = invoices.save(Invoice.issueMonthlyRent(contract, YearMonth.now().plusMonths(2), "PH-WIDGET-1"));

        mockMvc.perform(get("/dashboard").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-ai-invoice-id=\"" + inv.getId() + "\"")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-ai-invoice-id=\"" + inv.getId() + "\"")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-ai-invoice-id="))));
    }

    @Test
    void generalAskEnforcesRoleBoundariesBeforeCallingProvider() throws Exception {
        mockMvc.perform(post("/api/v1/ai/ask")
                .header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextType\":\"PROPERTY\",\"targetId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"Công nợ?\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ai/ask")
                .header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextType\":\"PROPERTY\",\"targetId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"Công nợ?\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ai/ask")
                .header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextType\":\"CONTRACT\",\"targetId\":\"" + DataSeeder.CONTRACT_1_ID + "\",\"question\":\"Tiền thuê?\"}"))
            .andExpect(status().isForbidden());

        // Role hợp lệ (AUTO): đi qua kiểm tra quyền, tới adapter (disabled -> 503)
        mockMvc.perform(post("/api/v1/ai/ask")
                .header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextType\":\"AUTO\",\"question\":\"Tổng quan hệ thống?\"}"))
            .andExpect(status().isServiceUnavailable());
    }
}
