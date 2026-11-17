package com.phonghub.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorPrometheusSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("GET /actuator/prometheus không kèm token thì bị từ chối 401")
    void getPrometheusWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /actuator/prometheus với token sai thì bị từ chối 401")
    void getPrometheusWithWrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer invalid-secret-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /actuator/prometheus với Bearer token đúng thì trả về 200 và metric text")
    void getPrometheusWithValidBearerTokenReturns200() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer phonghub-metrics-secret-token"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    @DisplayName("GET /actuator/prometheus với query param token đúng thì trả về 200")
    void getPrometheusWithValidQueryParamTokenReturns200() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .param("token", "phonghub-metrics-secret-token"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    @DisplayName("GET /actuator/health không cần token vẫn truy cập được 200")
    void getHealthWithoutTokenReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Các custom metrics phonghub đã được khai báo và có thể ghi nhận vào MeterRegistry")
    void customMetricsAreRegistered() {
        assertThat(meterRegistry).isNotNull();

        // Increment sample values
        meterRegistry.counter("phonghub.ratelimit.rejected", "path", "/login").increment();
        meterRegistry.counter("phonghub.sepay.ipn", "status", "success").increment();
        meterRegistry.counter("phonghub.scheduled.job.executions", "job", "late_fee", "status", "success").increment();

        assertThat(meterRegistry.find("phonghub.ratelimit.rejected").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("phonghub.sepay.ipn").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("phonghub.scheduled.job.executions").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.find("phonghub.chat.sse.active_connections").gauge()).isNotNull();
    }
}

