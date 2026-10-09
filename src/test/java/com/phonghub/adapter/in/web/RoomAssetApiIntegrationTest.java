package com.phonghub.adapter.in.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomAssetApiIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ROOM_ASSETS = "/api/rooms/" + DataSeeder.ROOM_101_ID + "/assets";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String assetJson(String code, String name, int quantity) {
        String codePart = code == null ? "" : "\"assetCode\":\"" + code + "\",";
        return "{" + codePart + "\"name\":\"" + name + "\",\"quantity\":" + quantity + ",\"estimatedValue\":1500000}";
    }

    private UUID createAsset(String actor, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", actor)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(code, name, 1)))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    @Test
    @DisplayName("Owner and staff of the property create assets; a code is generated when omitted")
    void ownerAndStaffCreateAssets() throws Exception {
        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Giường gỗ", 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.assetCode").value(org.hamcrest.Matchers.startsWith("TS-")))
            .andExpect(jsonPath("$.status").value("GOOD"));
        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Tủ lạnh", 1)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Created asset appears in the room list")
    void createdAssetAppearsInList() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Kệ sách danh sách");

        MvcResult list = mockMvc.perform(get(ROOM_ASSETS).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn();
        assertTrue(list.getResponse().getContentAsString().contains(assetId.toString()));
    }

    @Test
    @DisplayName("Owner of another property gets 403 on every asset operation")
    void foreignOwnerGets403() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Bàn học");

        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Trộm", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(ROOM_ASSETS).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/room-assets/" + assetId).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/room-assets/" + assetId).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Sửa", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/room-assets/" + assetId).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/room-assets/" + assetId + "/events").header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DAMAGED\",\"description\":\"hỏng\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/room-assets/" + assetId + "/qr").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tenant of the room can view assets but cannot change them")
    void tenantCanViewButNotModify() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Điều hòa");

        mockMvc.perform(get(ROOM_ASSETS).header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(get("/api/room-assets/" + assetId).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Tự thêm", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/room-assets/" + assetId).header("X-User-Id", TENANT_1))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tenant cannot view assets of a room without an active occupancy")
    void tenantCannotViewOtherRoom() throws Exception {
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_102_ID + "/assets").header("X-User-Id", TENANT_1))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Duplicate asset code returns 409, invalid input returns 400, unknown asset returns 404")
    void conflictValidationAndNotFound() throws Exception {
        String code = "QR-" + UUID.randomUUID().toString().substring(0, 8);
        createAsset(OWNER_1, code, "Quạt");

        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(code, "Quạt khác", 1)))
            .andExpect(status().isConflict());
        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, "Sai số lượng", 0)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(ROOM_ASSETS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(assetJson(null, " ", 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/room-assets/" + UUID.randomUUID()).header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Template adds a batch of assets; unknown template returns 400")
    void templateCreatesBatch() throws Exception {
        String roomId = DataSeeder.ROOM_201_ID.toString();
        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomId + "/assets/from-template")
                .param("template", "basic").header("X-User-Id", OWNER_1))
            .andExpect(status().isCreated())
            .andReturn();
        assertEquals(5, objectMapper.readTree(result.getResponse().getContentAsString()).size());

        mockMvc.perform(post("/api/rooms/" + roomId + "/assets/from-template")
                .param("template", "UNKNOWN").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Damage and repair events update the status and keep a history with cost")
    void eventsUpdateStatusAndHistory() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Máy nước nóng");

        mockMvc.perform(post("/api/room-assets/" + assetId + "/events").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DAMAGED\",\"description\":\"Không nóng\",\"cost\":0}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/room-assets/" + assetId).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("DAMAGED"));

        mockMvc.perform(post("/api/room-assets/" + assetId + "/events").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"REPAIRED\",\"description\":\"Thay điện trở\",\"cost\":250000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/room-assets/" + assetId).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("GOOD"));

        mockMvc.perform(get("/api/room-assets/" + assetId + "/events").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/room-assets/" + assetId + "/events").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"REPAIRED\",\"description\":\"Âm\",\"cost\":-1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Soft-deleted asset disappears from the list and returns 404")
    void softDelete() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Ghế cũ");

        mockMvc.perform(delete("/api/room-assets/" + assetId).header("X-User-Id", OWNER_1))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/room-assets/" + assetId).header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
        MvcResult list = mockMvc.perform(get(ROOM_ASSETS).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn();
        assertTrue(!list.getResponse().getContentAsString().contains(assetId.toString()));
    }

    @Test
    @DisplayName("QR endpoint returns a PNG generated on demand")
    void qrIsGeneratedOnDemand() throws Exception {
        UUID assetId = createAsset(OWNER_1, null, "Tủ quần áo");

        MvcResult result = mockMvc.perform(get("/api/room-assets/" + assetId + "/qr").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn();
        byte[] png = result.getResponse().getContentAsByteArray();
        assertEquals("image/png", result.getResponse().getContentType());
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
    }
}
