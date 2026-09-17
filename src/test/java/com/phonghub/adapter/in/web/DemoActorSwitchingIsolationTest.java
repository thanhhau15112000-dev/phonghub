package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "phonghub.demo.enabled=false")
class DemoActorSwitchingIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void switchUserEndpointReturnsForbiddenWhenDemoDisabled() throws Exception {
        mockMvc.perform(get("/switch-user")
                .param("userId", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void demoActorSwitcherUiIsNotRenderedWhenDemoDisabled() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("actor-switcher-box"))))
            .andExpect(content().string(not(containsString("switch-user"))));
    }

    @Test
    void demoHeadersDoNotOverrideIdentityWhenDemoDisabled() throws Exception {
        // When demo is enabled, X-User-Id: STAFF_1 scopes properties to only 1 property (Property A)
        // When demo is disabled, header is ignored, fallback is Admin, seeing both properties (Property A & B)
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nha Tro Xanh - Quan 7")))
            .andExpect(content().string(containsString("Khu Tro Tan Binh")));
    }
}
