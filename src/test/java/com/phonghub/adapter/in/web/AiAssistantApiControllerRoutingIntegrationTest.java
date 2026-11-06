package com.phonghub.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Confirms that each public AI API route reaches the expected use-case operation and serializes its response. */
class AiAssistantApiControllerRoutingIntegrationTest extends SeededWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiAssistantUseCase assistant;

    @Test
    void allAiPostRoutesReachTheirMatchingUseCaseMethod() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID contractId = DataSeeder.CONTRACT_1_ID;
        UUID propertyId = DataSeeder.PROP_1_ID;
        AiAssistantUseCase.AiResponse response = new AiAssistantUseCase.AiResponse(
            "Đã đối chiếu dữ liệu được cấp.", List.of(new AiAssistantUseCase.Source("FAQ PhongHub")));
        when(assistant.analyzeInvoice(invoiceId)).thenReturn(response);
        when(assistant.askAboutInvoice(invoiceId, "Vì sao tiền điện thay đổi?")).thenReturn(response);
        when(assistant.askAboutContract(contractId, "Ngày hết hạn là khi nào?")).thenReturn(response);
        when(assistant.askOperations(propertyId, "Có bao nhiêu phòng trống?")).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoiceId + "/analysis")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Đã đối chiếu dữ liệu được cấp."))
            .andExpect(jsonPath("$.sources[0].label").value("FAQ PhongHub"));
        verify(assistant).analyzeInvoice(invoiceId);

        mockMvc.perform(post("/api/v1/ai/invoices/" + invoiceId + "/ask")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Vì sao tiền điện thay đổi?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Đã đối chiếu dữ liệu được cấp."));
        verify(assistant).askAboutInvoice(invoiceId, "Vì sao tiền điện thay đổi?");

        mockMvc.perform(post("/api/v1/ai/contracts/" + contractId + "/ask")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Ngày hết hạn là khi nào?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Đã đối chiếu dữ liệu được cấp."));
        verify(assistant).askAboutContract(contractId, "Ngày hết hạn là khi nào?");

        mockMvc.perform(post("/api/v1/ai/operations/ask")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"" + propertyId + "\",\"question\":\"Có bao nhiêu phòng trống?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Đã đối chiếu dữ liệu được cấp."));
        verify(assistant).askOperations(propertyId, "Có bao nhiêu phòng trống?");
    }
}
