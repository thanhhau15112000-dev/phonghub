package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.in.AiAssistantUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API trợ lý AI bị giới hạn số lần gọi theo từng tài khoản (#311). */
@TestPropertySource(properties = {
    "phonghub.rate-limit.enabled=true",
    "phonghub.rate-limit.ai.max-requests=2",
    "phonghub.rate-limit.ai.window-seconds=60"
})
class AiRateLimitIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiAssistantUseCase assistant;

    @Test
    void callsAboveTheLimitAreRejectedPerUserWith429() throws Exception {
        when(assistant.askOperations(any(UUID.class), anyString()))
            .thenReturn(new AiAssistantUseCase.AiResponse("Có 2 phòng trống.", List.of()));

        mockMvc.perform(ask(OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(ask(OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(ask(OWNER_1))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.detail", containsString("thử lại sau")));
        // Lần bị chặn không gọi tới mô hình
        verify(assistant, times(2)).askOperations(any(UUID.class), anyString());

        // Bộ đếm theo tài khoản: người khác vẫn hỏi được
        mockMvc.perform(ask(STAFF_1)).andExpect(status().isOk());
    }

    @Test
    void everyAiEndpointSharesTheSameCounter() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(assistant.analyzeInvoice(invoiceId)).thenReturn(new AiAssistantUseCase.AiResponse("Ổn.", List.of()));
        when(assistant.askAboutContract(any(UUID.class), anyString()))
            .thenReturn(new AiAssistantUseCase.AiResponse("Còn 3 tháng.", List.of()));
        String tenant = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoiceId + "/analysis").header("X-User-Id", tenant))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/contracts/" + DataSeeder.CONTRACT_1_ID + "/ask").header("X-User-Id", tenant)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Còn bao lâu?\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/invoices/" + invoiceId + "/analysis").header("X-User-Id", tenant))
            .andExpect(status().isTooManyRequests());
    }

    private static MockHttpServletRequestBuilder ask(String userId) {
        return post("/api/v1/ai/operations/ask").header("X-User-Id", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"propertyId\":\"" + DataSeeder.PROP_1_ID + "\",\"question\":\"Có bao nhiêu phòng trống?\"}");
    }
}
