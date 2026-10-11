package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterSubmissionRepository;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class MeterSubmissionUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/rooms/" + DataSeeder.ROOM_101_ID + "/meter-submissions";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMeterSubmissionRepository submissions;

    @BeforeEach
    void reset() {
        submissions.clear();
    }

    @Test
    @DisplayName("Tenant submits from the page, the owner sees it and can review; outsiders are bounced")
    void uiFlow() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(view().name("rooms/meter-submissions"));
        mockMvc.perform(multipart(PAGE).file(new MockMultipartFile("file", "m.png", "image/png", PNG))
                .param("meterType", "ELECTRIC").param("declaredValue", "1500").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection());

        String ownerView = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(ownerView.contains("Chờ duyệt"));
        assertTrue(ownerView.contains("Từ chối"));
        String tenantView = mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1)).andReturn().getResponse()
            .getContentAsString();
        assertTrue(!tenantView.contains("Từ chối"), "tenants have no review controls");

        mockMvc.perform(get(PAGE).header("X-User-Id", STAFF_2)).andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart(PAGE).file(new MockMultipartFile("file", "m.png", "image/png", PNG))
                .param("meterType", "WATER").param("declaredValue", "5").header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }
}
