package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhongHubUiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Dashboard page renders successfully")
    void testDashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Bảng điều khiển quản lý nhà trọ")))
            .andExpect(content().string(containsString("Nhà trọ được truy cập")))
            .andExpect(content().string(containsString("PhongHub")))
            .andExpect(content().string(not(containsString("PhongHub Ops"))))
            .andExpect(content().string(containsString("aria-label=\"Chọn người dùng demo\"")))
            .andExpect(content().string(not(containsString("Người dùng hiện tại"))))
            .andExpect(content().string(not(containsString("style=\"font-weight: 500;\""))));
    }

    @Test
    @DisplayName("Properties list page renders successfully")
    void testPropertiesListRenders() throws Exception {
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ Xanh - Quận 7")));
    }

    @Test
    @DisplayName("Property detail page renders room list and status badges")
    void testPropertyDetailRenders() throws Exception {
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("P101")))
            .andExpect(content().string(containsString("Đang thuê")))
            .andExpect(content().string(containsString("Bảo trì")));
    }

    @Test
    @DisplayName("Room detail page renders status transition options")
    void testRoomDetailRenders() throws Exception {
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thông tin phòng")))
            .andExpect(content().string(containsString("P101")));
    }

    @Test
    @DisplayName("Contracts list and detail pages render successfully")
    void testContractsUiRenders() throws Exception {
        mockMvc.perform(get("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Hợp đồng")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nguyễn Văn A")))
            .andExpect(content().string(containsString("Đang hiệu lực")));

        mockMvc.perform(get("/contracts/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chọn nhà trọ")))
            .andExpect(content().string(containsString("Chọn phòng")));
    }

    @Test
    @DisplayName("Maintenance list and detail pages render successfully")
    void testMaintenanceUiRenders() throws Exception {
        mockMvc.perform(get("/maintenance")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Yêu cầu bảo trì")));

        mockMvc.perform(get("/maintenance/" + DataSeeder.TICKET_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sửa vòi nước và kiểm tra máy lạnh")))
            .andExpect(content().string(containsString("Đang xử lý")));

        mockMvc.perform(get("/maintenance/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Báo hỏng phòng")))
            .andExpect(content().string(containsString("Mức độ")));
    }

    @Test
    @DisplayName("Actor switch endpoint sets session and redirects")
    void testSwitchUser() throws Exception {
        mockMvc.perform(get("/switch-user?userId=" + LocalDemoAuthenticationAdapter.STAFF_1_ID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }
}
