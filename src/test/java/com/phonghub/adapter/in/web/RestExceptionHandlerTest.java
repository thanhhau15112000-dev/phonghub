package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.in.web.api.StatsApiController;
import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** #164: lỗi ngoài dự kiến của API trả ProblemDetail 500; lỗi chuẩn của Spring MVC và lỗi nghiệp vụ giữ nguyên mã. */
class RestExceptionHandlerTest {

    private StatisticsUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(StatisticsUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StatsApiController(useCase))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("lỗi ngoài dự kiến: 500 dạng ProblemDetail, thông điệp chung, không lộ chi tiết lỗi")
    void unexpectedErrorIsProblemDetail() throws Exception {
        when(useCase.ownerSummary(any())).thenThrow(new IllegalStateException("connection refused, password=hunter2"));

        mockMvc.perform(get("/api/stats/owner/summary"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("Hệ thống gặp lỗi, vui lòng thử lại sau."))
            .andExpect(content().string(not(containsString("hunter2"))))
            .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("lỗi nghiệp vụ vẫn theo ánh xạ riêng (403)")
    void domainErrorKeepsMapping() throws Exception {
        when(useCase.ownerSummary(any())).thenThrow(new UnauthorizedPropertyAccessException("Không có quyền"));

        mockMvc.perform(get("/api/stats/owner/summary"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Không có quyền"));
    }

    @Test
    @DisplayName("lỗi chuẩn của Spring MVC giữ đúng mã: sai kiểu tham số 400, sai phương thức 405")
    void springMvcErrorsKeepStatus() throws Exception {
        mockMvc.perform(get("/api/stats/owner/revenue").param("groupBy", "WEEK"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stats/owner/summary").param("from", "2026-13"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/stats/owner/summary"))
            .andExpect(status().isMethodNotAllowed());
    }
}
