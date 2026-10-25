package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.config.PhongHubCaches;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** #89: ngừng sử dụng / dùng lại phòng qua giao diện và API — 403, 404, 409 theo ràng buộc. */
class RoomArchiveIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String PROPERTY = DataSeeder.PROP_1_ID.toString();
    /** Phòng trống, không ràng buộc. */
    private static final String FREE = DataSeeder.ROOM_104_ID.toString();
    /** Phòng có hợp đồng đang hiệu lực. */
    private static final String OCCUPIED = DataSeeder.ROOM_101_ID.toString();
    /** Phòng bảo trì, phiếu đang xử lý. */
    private static final String UNDER_REPAIR = DataSeeder.ROOM_102_ID.toString();
    /** Phòng đang giữ chỗ. */
    private static final String RESERVED = DataSeeder.ROOM_103_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private PhongHubCaches caches;

    @BeforeEach
    void clearCaches() {
        caches.evictAll();
    }

    private String html(String path, String userId) throws Exception {
        return mockMvc.perform(get(path).header("X-User-Id", userId)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Giao diện: chủ trọ ngừng sử dụng phòng, phòng rời danh sách và hiện ở mục đã ngừng; dùng lại được")
    void uiArchiveAndRestore() throws Exception {
        assertTrue(html("/rooms/" + FREE, OWNER_1).contains("Ngừng sử dụng phòng"));

        mockMvc.perform(post("/rooms/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/properties/" + PROPERTY))
            .andExpect(flash().attribute("successMessage", containsString("Đã ngừng sử dụng phòng 'P104'")));

        String property = html("/properties/" + PROPERTY, OWNER_1);
        assertTrue(property.contains("Phòng đã ngừng sử dụng</h3>"));
        assertTrue(property.contains("/rooms/" + FREE + "/restore"));
        String room = html("/rooms/" + FREE, OWNER_1);
        assertTrue(room.contains("Dùng lại phòng"));
        assertFalse(room.contains("editRoomBtn"), "phòng đã ngừng không cho sửa");
        assertFalse(room.contains("+ Tạo hợp đồng"), "phòng đã ngừng không tạo hợp đồng");

        mockMvc.perform(post("/rooms/" + FREE + "/restore").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/rooms/" + FREE))
            .andExpect(flash().attribute("successMessage", containsString("Đã dùng lại phòng")));
        assertFalse(html("/properties/" + PROPERTY, OWNER_1).contains("Phòng đã ngừng sử dụng</h3>"));
    }

    @Test
    @DisplayName("Giao diện: phòng còn hợp đồng hoặc phiếu bảo trì báo lỗi rõ ràng")
    void uiArchiveBlocked() throws Exception {
        mockMvc.perform(post("/rooms/" + OCCUPIED + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/rooms/" + OCCUPIED))
            .andExpect(flash().attribute("errorMessage", containsString("hợp đồng chưa kết thúc")));
        mockMvc.perform(post("/rooms/" + UNDER_REPAIR + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(flash().attribute("errorMessage", containsString("phiếu bảo trì chưa xử lý xong")));
    }

    @Test
    @DisplayName("API: 200 / 409 / 403 / 404, danh sách archived=true và chặn tạo hợp đồng, sửa phòng đã ngừng")
    void apiStatusCodes() throws Exception {
        mockMvc.perform(post("/api/rooms/" + OCCUPIED + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/rooms/" + RESERVED + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đang trống hoặc bảo trì")));
        mockMvc.perform(post("/api/rooms/" + FREE + "/archive").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + FREE + "/archive").header("X-User-Id", STAFF_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + UUID.randomUUID() + "/archive").header("X-User-Id", ADMIN))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/rooms/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", notNullValue()));
        mockMvc.perform(post("/api/rooms/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());

        String rooms = "/api/properties/" + PROPERTY + "/rooms";
        mockMvc.perform(get(rooms).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].id", not(hasItem(FREE))));
        mockMvc.perform(get(rooms).param("archived", "true").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].id", hasItem(FREE)))
            .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put("/api/rooms/" + FREE + "/status").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAINTENANCE\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đã ngừng sử dụng")));
        mockMvc.perform(put("/api/rooms/" + FREE).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P104\",\"floor\":1,\"basePrice\":4100000,\"maxOccupants\":3}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post(rooms).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P104\",\"floor\":1,\"basePrice\":3000000,\"maxOccupants\":2,"
                    + "\"electricMeter\":0,\"waterMeter\":0,\"conditionNote\":\"Hiện trạng ban đầu\",\"noAssets\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("khôi phục phòng đó")));

        mockMvc.perform(post("/api/rooms/" + FREE + "/restore").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + FREE + "/restore").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", nullValue()));
        mockMvc.perform(post("/api/rooms/" + FREE + "/restore").header("X-User-Id", ADMIN))
            .andExpect(status().isConflict());
    }
}
