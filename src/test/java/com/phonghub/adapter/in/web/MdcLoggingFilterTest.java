package com.phonghub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class MdcLoggingFilterTest extends SeededWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Request tự động sinh X-Request-Id và trả về trong Response Header")
    void generatesAndReturnsRequestIdHeader() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("Client gửi sẵn X-Request-Id hợp lệ thì server giữ nguyên ID đó")
    void preservesExistingRequestId() throws Exception {
        String customRequestId = "client-trace-id-12345";
        mockMvc.perform(get("/health")
                .header("X-Request-Id", customRequestId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", customRequestId));
    }

    @Test
    @DisplayName("Băm mã người dùng bằng SHA-256 an toàn, không để lộ thông tin gốc")
    void userHashIsHashedWithSha256() {
        String userId = "00000000-0000-0000-0000-000000000001";
        String hash = MdcLoggingFilter.sha256Hex(userId);

        assertThat(hash).isNotEqualTo(userId);
        assertThat(hash).hasSize(64); // 32 bytes = 64 hex chars
        assertThat(hash).matches("^[a-f0-9]{64}$");

        // Anonymous user trả về chuỗi anonymous
        assertThat(MdcLoggingFilter.sha256Hex(null)).isEqualTo("anonymous");
        assertThat(MdcLoggingFilter.sha256Hex("anonymousUser")).isEqualTo("anonymous");
    }
}

