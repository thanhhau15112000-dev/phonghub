package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** #89: sửa/hủy phiếu bảo trì chưa xử lý — người báo phiếu, người quản lý; 403 người khác, 409 khi đã xử lý. */
class MaintenanceEditCancelIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    /** Phiếu mẫu đang xử lý (IN_PROGRESS). */
    private static final String IN_PROGRESS = DataSeeder.TICKET_1_ID.toString();

    @Autowired private MockMvc mockMvc;

    private String ticketId;

    @BeforeEach
    void tenantReportsTicket() throws Exception {
        String body = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Vòi nước rỉ\","
                    + "\"description\":\"Vòi bồn rửa rỉ nước\",\"priority\":\"LOW\",\"setRoomMaintenance\":false}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        ticketId = JsonPath.read(body, "$.id");
    }

    private static String edit(String title, String priority) {
        return "{\"title\":\"" + title + "\",\"description\":\"Rỉ cả ban đêm\",\"priority\":\"" + priority + "\"}";
    }

    @Test
    @DisplayName("API: người báo phiếu sửa được; chủ trọ khác, kỹ thuật viên bị 403; phiếu đang xử lý 409")
    void apiEdit() throws Exception {
        mockMvc.perform(patch("/api/maintenance/" + ticketId).header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(edit("Vòi nước rỉ mạnh", "HIGH")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Vòi nước rỉ mạnh"))
            .andExpect(jsonPath("$.priority").value("HIGH"));
        mockMvc.perform(patch("/api/maintenance/" + ticketId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content(edit("Vòi nước rỉ (staff sửa)", "MEDIUM")))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/maintenance/" + ticketId).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(edit("x", "LOW")))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/maintenance/" + ticketId).header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON).content(edit("x", "LOW")))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/maintenance/" + ticketId).header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \",\"description\":\"d\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/maintenance/" + IN_PROGRESS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(edit("Đổi tiêu đề", "LOW")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("chưa xử lý")));
    }

    @Test
    @DisplayName("API: hủy phiếu ghi mốc vào dòng thời gian; hủy lần nữa hoặc phiếu đang xử lý thì 409")
    void apiCancel() throws Exception {
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/cancel").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/cancel").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Đã tự sửa được\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/maintenance/" + ticketId + "/timeline").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].note", hasItem("Hủy phiếu: Đã tự sửa được")));
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/maintenance/" + IN_PROGRESS + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/assign").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":\"" + TECH_1 + "\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Giao diện: phiếu chưa xử lý có mục sửa/hủy; sau khi hủy hiện 'Yêu cầu đã bị hủy', không còn mục xử lý")
    void uiEditAndCancel() throws Exception {
        String page = mockMvc.perform(get("/maintenance/" + ticketId).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Sửa hoặc hủy yêu cầu"));
        String techView = mockMvc.perform(get("/maintenance/" + IN_PROGRESS).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(techView.contains("Sửa hoặc hủy yêu cầu"), "phiếu đang xử lý không còn sửa/hủy");

        mockMvc.perform(post("/maintenance/" + ticketId + "/edit").header("X-User-Id", TENANT_1)
                .param("title", "Vòi nước rỉ (sửa)").param("description", "Rỉ nhiều hơn").param("priority", "URGENT"))
            .andExpect(redirectedUrl("/maintenance/" + ticketId))
            .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật")));

        mockMvc.perform(post("/maintenance/" + ticketId + "/cancel").header("X-User-Id", OWNER_2))
            .andExpect(flash().attribute("errorMessage", containsString("không có quyền")));
        mockMvc.perform(post("/maintenance/" + ticketId + "/cancel").header("X-User-Id", OWNER_1).param("reason", "Trùng phiếu"))
            .andExpect(flash().attribute("successMessage", containsString("Đã hủy phiếu")));

        String cancelled = mockMvc.perform(get("/maintenance/" + ticketId).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(cancelled.contains("Yêu cầu đã bị hủy"));
        assertTrue(cancelled.contains("Đã hủy"));
        assertFalse(cancelled.contains("Sửa hoặc hủy yêu cầu"));
        assertFalse(cancelled.contains("<h2>Xử lý yêu cầu bảo trì</h2>"));
    }
}
