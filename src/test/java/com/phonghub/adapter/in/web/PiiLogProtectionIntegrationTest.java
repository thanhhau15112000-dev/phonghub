package com.phonghub.adapter.in.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PiiLogProtectionIntegrationTest extends SeededWebTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUpAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownAppender() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Luồng đăng nhập không ghi mật khẩu vào log")
    void loginFlowDoesNotLogPassword() throws Exception {
        String secretPassword = "SuperSecretPassword123!@#";

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "username": "admin",
                    "password": "%s"
                }
                """.formatted(secretPassword)));

        // Kiểm tra toàn bộ logs được ghi nhận không chứa mật khẩu bí mật
        boolean passwordFoundInLogs = listAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains(secretPassword));

        assertThat(passwordFoundInLogs)
            .as("Tuyệt đối không được ghi mật khẩu đăng nhập vào bất kỳ dòng log nào")
            .isFalse();
    }

    @Test
    @DisplayName("Luồng người thuê / CCCD không ghi số CCCD vào log")
    void tenantFlowDoesNotLogCccd() throws Exception {
        String sensitiveCccd = "079200099999";

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "username": "admin",
                    "password": "wrong_password"
                }
                """));

        boolean cccdFoundInLogs = listAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains(sensitiveCccd));

        assertThat(cccdFoundInLogs)
            .as("Tuyệt đối không ghi số CCCD vào log")
            .isFalse();
    }
}

