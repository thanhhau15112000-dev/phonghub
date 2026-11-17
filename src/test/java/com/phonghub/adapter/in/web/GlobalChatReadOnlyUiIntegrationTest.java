package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Khung chat biết vai trò người dùng để Kênh chung chỉ đọc với người thuê (#320). */
class GlobalChatReadOnlyUiIntegrationTest extends SeededWebTest {

    @Autowired private MockMvc mockMvc;

    private String page(String userId) throws Exception {
        return mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Người thuê: khung chat mang vai trò TENANT và có dòng báo Kênh chung chỉ đọc")
    void tenantWidgetIsReadOnlyForGlobalChannel() throws Exception {
        String html = page(LocalDemoAuthenticationAdapter.TENANT_1_ID.toString());
        assertTrue(html.contains("data-user-role=\"TENANT\""));
        assertTrue(html.contains("id=\"phonghubDockedReadOnlyNote\""));
        assertTrue(html.contains("roomType === 'GLOBAL' && currentUserRole === 'TENANT'"));
    }

    @Test
    @DisplayName("Chủ trọ: khung chat mang vai trò OWNER nên vẫn gửi được Kênh chung")
    void ownerWidgetKeepsComposer() throws Exception {
        String html = page(LocalDemoAuthenticationAdapter.OWNER_1_ID.toString());
        assertTrue(html.contains("data-user-role=\"OWNER\""));
    }
}
