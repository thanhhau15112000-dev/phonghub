package com.phonghub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.in.web.api.ChatApiController;
import com.phonghub.adapter.in.web.api.StatsApiController;
import com.phonghub.application.port.in.ChatMessageUseCase;
import com.phonghub.application.port.in.ChatRoomUseCase;
import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.out.ChatSubscriptionPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    void disconnectedChatStreamDoesNotAttemptToWriteProblemDetail() throws Exception {
        CurrentUser user = new CurrentUser(UUID.randomUUID(), "tenant@example.test", "Tenant", UserRole.TENANT);
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().name("init").data("connected"));
        ChatSubscriptionPort subscriptions = mock(ChatSubscriptionPort.class);
        when(subscriptions.<SseEmitter>subscribe(user.id())).thenReturn(emitter);
        MockMvc chatMvc = MockMvcBuilders.standaloneSetup(new ChatApiController(
                mock(ChatRoomUseCase.class), mock(ChatMessageUseCase.class), () -> user, subscriptions, ""))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
        MvcResult stream = chatMvc.perform(get("/api/chat/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andReturn();
        String initialBody = stream.getResponse().getContentAsString();
        assertThat(initialBody).contains("event:init", "data:connected");

        emitter.completeWithError(new AsyncRequestNotUsableException("Client disconnected"));

        chatMvc.perform(asyncDispatch(stream))
            .andExpect(status().isOk())
            .andExpect(content().string(initialBody));
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
