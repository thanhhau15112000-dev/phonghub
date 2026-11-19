package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

/** Checks the UI entry points and the real, provider-disabled AI API routes. */
@TestPropertySource(properties = "phonghub.ai.enabled=false")
class AiAssistantRoutingIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryInvoiceRepository invoices;

    @Test
    void navigationOnlyShowsOperationsAssistantToOwnerAndStaff() throws Exception {
        for (String userId : new String[] {OWNER_1, STAFF_1}) {
            mockMvc.perform(get("/dashboard").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"/ai/operations\">Trợ lý vận hành</a>")));
        }

        for (String userId : new String[] {ADMIN, TECH_1, TENANT_1}) {
            mockMvc.perform(get("/dashboard").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/ai/operations\""))));
        }
    }

    @Test
    void operationsPageIsAvailableToOwnerAndStaffAndScopesStaffProperties() throws Exception {
        mockMvc.perform(get("/ai/operations").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/api/v1/ai/operations/ask")))
            .andExpect(content().string(containsString(DataSeeder.PROP_1_ID.toString())))
            .andExpect(content().string(containsString("/js/ai-assistant.js")));

        mockMvc.perform(get("/ai/operations").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(DataSeeder.PROP_1_ID.toString())))
            .andExpect(content().string(not(containsString(DataSeeder.PROP_2_ID.toString()))));

        mockMvc.perform(get("/ai/operations").header("X-User-Id", STAFF_2))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(DataSeeder.PROP_2_ID.toString())))
            .andExpect(content().string(not(containsString(DataSeeder.PROP_1_ID.toString()))));
    }

    @Test
    void unauthorizedRolesAreRedirectedFromOperationsPage() throws Exception {
        for (String userId : new String[] {ADMIN, TECH_1, TENANT_1}) {
            mockMvc.perform(get("/ai/operations").header("X-User-Id", userId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        }
    }

    @Test
    void invoiceAndContractPagesRenderTheirAiRoutesAndSharedClient() throws Exception {
        Invoice invoice = rentInvoice();

        mockMvc.perform(get("/invoices/" + invoice.getId() + "/pay").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/api/v1/ai/invoices/" + invoice.getId() + "/analysis")))
            .andExpect(content().string(containsString("/api/v1/ai/invoices/" + invoice.getId() + "/ask")))
            .andExpect(content().string(containsString("/js/ai-assistant.js")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/api/v1/ai/contracts/" + DataSeeder.CONTRACT_1_ID + "/ask")))
            .andExpect(content().string(containsString("/js/ai-assistant.js")));

        mockMvc.perform(get("/js/ai-assistant.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-ai-form")));
    }

    @Test
    void realAiApiRejectsInvalidInputAndAccessBeforeProviderCall() throws Exception {
        Invoice invoice = rentInvoice();

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/analysis")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"   \"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "x".repeat(1200) + "\"}"))
            .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "x".repeat(1201) + "\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoice.getId() + "/ask")
                .header("X-User-Id", OWNER_2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Giải thích hóa đơn\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ai/contracts/not-a-uuid/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Ngày kết thúc là khi nào?\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/contracts/" + DataSeeder.CONTRACT_1_ID + "/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Ngày kết thúc là khi nào?\"}"))
            .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/ai/operations/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"Bao nhiêu phòng trống?\"}"))
            .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/ai/operations/ask")
                .header("X-User-Id", STAFF_2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"Bao nhiêu phòng trống?\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ai/operations/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/operations/ask")
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Có bao nhiêu phòng trống?\"}"))
            .andExpect(status().isBadRequest());
    }

    private Invoice rentInvoice() {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        return invoices.save(Invoice.issueMonthlyRent(contract, YearMonth.now().plusMonths(1), "PH-AI-ROUTE"));
    }
}
