package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
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
class SepayWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.clear();
    }

    @Test
    @DisplayName("POST /api/v1/payments/sepay/webhook successfully records transaction and responds 200 OK")
    void testReceiveSepayWebhookSuccessfully() throws Exception {
        String payload = """
            {
              "id": 99001,
              "gateway": "Vietcombank",
              "transactionDate": "2026-10-01 23:45:00",
              "accountNumber": "1012345678",
              "code": null,
              "content": "PHONGHUB COC PHONG 201",
              "transferType": "in",
              "transferAmount": 2000000,
              "accumulated": 12000000,
              "subAccount": null,
              "referenceCode": "VCB.20261001.99001",
              "description": "Chuyen khoan tien coc phong"
            }
            """;

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.message", is("Xử lý giao dịch SePay thành công")))
            .andExpect(jsonPath("$.paymentId").isNotEmpty());

        mockMvc.perform(get("/api/v1/payments/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].sepayId", is(99001)))
            .andExpect(jsonPath("$[0].gateway", is("Vietcombank")))
            .andExpect(jsonPath("$[0].transferAmount", is(2000000)))
            .andExpect(jsonPath("$[0].status", is("SUCCESS")));
    }

    @Test
    @DisplayName("POST /api/v1/payments/sepay/webhook is idempotent for duplicate transaction calls")
    void testIdempotentDuplicateWebhook() throws Exception {
        String payload = """
            {
              "id": 99002,
              "gateway": "MBBank",
              "transactionDate": "2026-10-01 23:46:00",
              "accountNumber": "0389999999",
              "content": "TIEN PHONG T10",
              "transferType": "in",
              "transferAmount": 3500000,
              "accumulated": 25000000,
              "referenceCode": "MB.99002"
            }
            """;

        // Call 1
        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.message", is("Xử lý giao dịch SePay thành công")));

        // Call 2 (Duplicate from SePay retry)
        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.message", is("Giao dịch đã được ghi nhận trước đó")));

        // Exactly 1 record in repository
        mockMvc.perform(get("/api/v1/payments/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }
}
