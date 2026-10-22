package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Giới hạn đăng nhập API theo tài khoản chạy trong ứng dụng thật mà controller vẫn nhận đủ body JSON. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "phonghub.rate-limit.enabled=true",
    "phonghub.rate-limit.login.max-requests=100",
    "phonghub.rate-limit.login-account.max-requests=2",
    "phonghub.rate-limit.login-account.window-seconds=300"
})
class ApiLoginAccountRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private int login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Sai mật khẩu nhiều lần cùng tài khoản: 2 lần đầu tới controller (401, không phải 400), lần 3 bị 429")
    void sameAccountIsThrottledAndBodyReachesController() throws Exception {
        assertEquals(401, login("ratelimit-target"));
        assertEquals(401, login("RateLimit-Target"));
        assertEquals(429, login("ratelimit-target"));
        assertEquals(401, login("someone-else"));
    }
}
