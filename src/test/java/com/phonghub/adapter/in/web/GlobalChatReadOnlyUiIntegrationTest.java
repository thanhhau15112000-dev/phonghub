package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(html.contains("return { label: 'Người thuê'"));
        assertTrue(html.contains("Kênh thông báo của ban quản lý nhà trọ"));
        assertTrue(html.contains("Không gửi được tin nhắn, vui lòng thử lại."));
        assertTrue(html.contains("Không mở được cuộc trò chuyện, vui lòng thử lại."));
        assertTrue(html.contains("Không tải được tin nhắn, vui lòng đóng và thử lại."));
        assertFalse(html.contains("Kênh thông báo & trao đổi toàn hệ thống"));
        assertFalse(html.contains("meta ai"));
        assertFalse(html.contains("contactsLoaded"));
    }

    @Test
    @DisplayName("Chủ trọ: khung chat mang vai trò OWNER nên vẫn gửi được Kênh chung")
    void ownerWidgetKeepsComposer() throws Exception {
        String html = page(LocalDemoAuthenticationAdapter.OWNER_1_ID.toString());
        assertTrue(html.contains("data-user-role=\"OWNER\""));
    }
}
