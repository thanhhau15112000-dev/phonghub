package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MaintenanceAttachmentApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final byte[] MP4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2'};
    private static final byte[] WEBM = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2, 3, 4};

    @Autowired private MockMvc mockMvc;

    /** Tenant báo hỏng ở phòng 101, chưa giao cho ai. */
    private String ticket() throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Rò nước\","
            + "\"description\":\"Vòi rò\",\"priority\":\"MEDIUM\",\"setRoomMaintenance\":false}";
        String response = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions upload(String ticketId, String type, byte[] bytes, String user) throws Exception {
        return mockMvc.perform(multipart("/api/maintenance/" + ticketId + "/attachments")
            .file(new MockMultipartFile("file", "f", type, bytes)).header("X-User-Id", user));
    }

    @Test
    @DisplayName("Tenant attaches up to 5 photos and one short video; extra ones are refused with 409")
    void limits() throws Exception {
        String id = ticket();
        for (int i = 0; i < 5; i++) {
            upload(id, "image/png", PNG, TENANT_1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("IMAGE"));
        }
        upload(id, "image/png", PNG, TENANT_1).andExpect(status().isConflict());
        upload(id, "video/mp4", MP4, TENANT_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("VIDEO"));
        upload(id, "video/webm", WEBM, TENANT_1).andExpect(status().isConflict());

        String json = mockMvc.perform(get("/api/maintenance/" + id + "/attachments").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
            .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("storage"), "storage keys must not be exposed");
    }

    @Test
    @DisplayName("Only real images/videos within the size limit are accepted (400)")
    void contentValidation() throws Exception {
        String id = ticket();
        upload(id, "application/pdf", "%PDF-1.7\nbody".getBytes(), TENANT_1).andExpect(status().isBadRequest());
        upload(id, "text/plain", "hi".getBytes(), TENANT_1).andExpect(status().isBadRequest());
        upload(id, "image/png", "not an image".getBytes(), TENANT_1).andExpect(status().isBadRequest());
        upload(id, "video/mp4", PNG, TENANT_1).andExpect(status().isBadRequest());
        upload(id, "video/mp4", new byte[10 * 1024 * 1024 + 1], TENANT_1).andExpect(status().isBadRequest());
        upload(id, "video/webm", WEBM, TENANT_1).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Viewing: tenant, owner/staff of the property and the assigned technician; everyone else gets 403")
    void viewAuthorization() throws Exception {
        String id = ticket();
        String attachment = JsonPath.read(upload(id, "image/png", PNG, TENANT_1).andReturn().getResponse()
            .getContentAsString(), "$.id");
        String path = "/api/maintenance/" + id + "/attachments";

        for (String ok : new String[]{TENANT_1, OWNER_1, STAFF_1}) {
            mockMvc.perform(get(path).header("X-User-Id", ok)).andExpect(status().isOk());
            mockMvc.perform(get(path + "/" + attachment).header("X-User-Id", ok)).andExpect(status().isFound());
        }
        for (String denied : new String[]{OWNER_2, STAFF_2, TECH_1}) {
            mockMvc.perform(get(path).header("X-User-Id", denied)).andExpect(status().isForbidden());
            mockMvc.perform(get(path + "/" + attachment).header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        // Giao phiếu cho technician thì technician xem được nhưng không tải lên được.
        MaintenanceTestFlow.wearReady(mockMvc, id, OWNER_1);
        mockMvc.perform(post("/api/maintenance/" + id + "/assign").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + TECH_1 + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get(path).header("X-User-Id", TECH_1)).andExpect(status().isOk());
        mockMvc.perform(get(path + "/" + attachment).header("X-User-Id", TECH_1)).andExpect(status().isFound());
        upload(id, "image/png", PNG, TECH_1).andExpect(status().isForbidden());
        upload(id, "image/png", PNG, OWNER_2).andExpect(status().isForbidden());
        // Đường dẫn của phiếu khác không cho tải tệp của phiếu này.
        String other = ticket();
        mockMvc.perform(get("/api/maintenance/" + other + "/attachments/" + attachment).header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Uploader or a manager can delete; a finished ticket accepts no more attachments")
    void deleteAndFinishedTicket() throws Exception {
        String id = ticket();
        String path = "/api/maintenance/" + id + "/attachments";
        String first = JsonPath.read(upload(id, "image/png", PNG, TENANT_1).andReturn().getResponse()
            .getContentAsString(), "$.id");
        String second = JsonPath.read(upload(id, "image/png", PNG, OWNER_1).andReturn().getResponse()
            .getContentAsString(), "$.id");

        mockMvc.perform(delete(path + "/" + second).header("X-User-Id", TENANT_1)).andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/" + first).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/" + first).header("X-User-Id", TENANT_1)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/" + second).header("X-User-Id", STAFF_1)).andExpect(status().isNoContent());
        mockMvc.perform(get(path).header("X-User-Id", OWNER_1)).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/maintenance/" + id + "/reject").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không thuộc phạm vi\"}"))
            .andExpect(status().isOk());
        upload(id, "image/png", PNG, TENANT_1).andExpect(status().isBadRequest());
    }
}
