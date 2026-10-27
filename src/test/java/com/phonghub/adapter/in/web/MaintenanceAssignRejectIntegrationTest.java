package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MaintenanceAssignRejectIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryNotificationRepository notifications;

    /** Tenant báo hỏng ở phòng 101: phiếu mới ở trạng thái REPORTED, chưa giao cho ai. */
    private String reportTicket() throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Vòi nước rò\","
            + "\"description\":\"Vòi bồn rửa chảy nhỏ giọt\",\"priority\":\"MEDIUM\",\"setRoomMaintenance\":false}";
        String response = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private ResultActions assign(String id, String technicianId, String user) throws Exception {
        return mockMvc.perform(post("/api/maintenance/" + id + "/assign").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + technicianId + "\"}"));
    }

    private ResultActions reject(String id, String reason, String user) throws Exception {
        return mockMvc.perform(post("/api/maintenance/" + id + "/reject").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    @Test
    @DisplayName("Owner/staff assign a ticket to a technician of that property; technician then sees and accepts it")
    void assignThenTechnicianWorks() throws Exception {
        String id = reportTicket();
        // Phiếu của người thuê phải được chốt nhánh và (nhánh hao mòn) chủ trọ duyệt rồi mới giao việc được.
        assign(id, TECH_1, OWNER_1).andExpect(status().isConflict());
        MaintenanceTestFlow.wearReady(mockMvc, id, OWNER_1);

        assign(id, TECH_1, OWNER_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignedTechnicianId").value(TECH_1));
        mockMvc.perform(get("/api/maintenance/" + id).header("X-User-Id", TECH_1)).andExpect(status().isOk());
        mockMvc.perform(post("/api/maintenance/" + id + "/accept").header("X-User-Id", TECH_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        // Đã bắt đầu xử lý thì không giao lại hay từ chối được nữa.
        assign(id, TECH_1, STAFF_1).andExpect(status().isBadRequest());
        reject(id, "Không cần nữa", OWNER_1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A technician cannot see or take a ticket that is not assigned to them")
    void technicianOnlySeesAssignedTickets() throws Exception {
        String id = reportTicket();

        mockMvc.perform(get("/api/maintenance/" + id).header("X-User-Id", TECH_1)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/maintenance/" + id + "/accept").header("X-User-Id", TECH_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/maintenance/" + id + "/resolve").header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNotes\":\"done\",\"releaseRoomToAvailable\":false}"))
            .andExpect(status().isForbidden());
        String list = mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/maintenance")
                .header("X-User-Id", TECH_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(!list.contains(id), "unassigned ticket must not be listed for the technician");
        // Chủ trọ vẫn thấy phiếu.
        mockMvc.perform(get("/api/maintenance/" + id).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Assigning is limited to managers of the property and to technicians assigned to it")
    void assignAuthorization() throws Exception {
        String id = reportTicket();

        assign(id, TECH_1, OWNER_2).andExpect(status().isForbidden());
        assign(id, TECH_1, TENANT_1).andExpect(status().isForbidden());
        assign(id, TECH_1, TECH_1).andExpect(status().isForbidden());
        // Người được giao không phải kỹ thuật viên của nhà trọ này.
        assign(id, STAFF_1, OWNER_1).andExpect(status().isBadRequest());
        assign(id, UUID.randomUUID().toString(), OWNER_1).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/maintenance/" + id + "/assign").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejecting needs a reason, is manager-only, and notifies the tenant who reported it")
    void rejectNotifiesTenant() throws Exception {
        String id = reportTicket();

        reject(id, "   ", OWNER_1).andExpect(status().isBadRequest());
        reject(id, "Không thuộc phạm vi", OWNER_2).andExpect(status().isForbidden());
        reject(id, "Không thuộc phạm vi", TENANT_1).andExpect(status().isForbidden());
        reject(id, "Không thuộc phạm vi", TECH_1).andExpect(status().isForbidden());

        int before = notifications.findAll().size();
        reject(id, "Hư hỏng do người thuê, không thuộc phạm vi bảo trì", STAFF_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Hư hỏng do người thuê, không thuộc phạm vi bảo trì"));

        assertEquals(before + 1, notifications.findAll().size());
        assertTrue(notifications.findAll().stream().anyMatch(n ->
            TENANT_1.equals(String.valueOf(n.targetUserId())) && n.message().contains("không thuộc phạm vi bảo trì")));
        // Phiếu đã từ chối thì không từ chối hay giao lại được.
        reject(id, "Lần hai", OWNER_1).andExpect(status().isBadRequest());
        assign(id, TECH_1, OWNER_1).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/maintenance/" + id).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").exists());
    }
}
