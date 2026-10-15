package com.phonghub.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Xác nhận filter giới hạn tần suất được đăng ký trong ứng dụng thật, chạy trước lớp bảo mật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "phonghub.rate-limit.enabled=true",
    "phonghub.rate-limit.login.max-requests=2",
    "phonghub.rate-limit.login.window-seconds=60"
})
class RateLimitWiringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/auth/login vượt ngưỡng cấu hình qua properties thì nhận 429 và Retry-After")
    void loginIsThrottled() throws Exception {
        String body = "{\"username\":\"nobody\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 2; i++) {
            int status = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
            assertNotEquals(429, status);
        }
        var response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse();
        assertEquals(429, response.getStatus());
        assertEquals(true, Long.parseLong(response.getHeader("Retry-After")) > 0);
    }
}
