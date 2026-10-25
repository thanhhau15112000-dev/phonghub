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

/** #89: ẩn/khôi phục nhà trọ qua giao diện và API — 403 người ngoài, 409 còn ràng buộc, 404 id lạ. */
class PropertyArchiveIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    /** Nhà trọ chờ duyệt của owner 1, chưa có hợp đồng nên ẩn được. */
    private static final String FREE = DataSeeder.PROP_2_ID.toString();
    /** Nhà trọ có hợp đồng đang hiệu lực. */
    private static final String BUSY = DataSeeder.PROP_1_ID.toString();

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
    @DisplayName("Giao diện: chủ trọ ẩn nhà trọ, nhà trọ chuyển sang mục đã ẩn, khôi phục thì hiện lại")
    void uiArchiveAndRestore() throws Exception {
        assertTrue(html("/properties/" + FREE, OWNER_1).contains("Ẩn nhà trọ"));

        mockMvc.perform(post("/properties/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/properties"))
            .andExpect(flash().attribute("successMessage", containsString("Đã ẩn nhà trọ")));

        String list = html("/properties", OWNER_1);
        assertTrue(list.contains("<h2>Nhà trọ đã ẩn</h2>"));
        assertTrue(list.contains("/properties/" + FREE + "/restore"));
        String detail = html("/properties/" + FREE, OWNER_1);
        assertTrue(detail.contains("Khôi phục nhà trọ"));
        assertFalse(detail.contains("editPropertyBtn"), "nhà đã ẩn không cho sửa");

        mockMvc.perform(post("/properties/" + FREE + "/restore").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/properties/" + FREE))
            .andExpect(flash().attribute("successMessage", containsString("Đã khôi phục")));
        assertFalse(html("/properties", OWNER_1).contains("<h2>Nhà trọ đã ẩn</h2>"));
    }

    @Test
    @DisplayName("Giao diện: nhà trọ còn hợp đồng hiệu lực báo lỗi rõ ràng, không bị ẩn")
    void uiArchiveBlocked() throws Exception {
        mockMvc.perform(post("/properties/" + BUSY + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/properties/" + BUSY))
            .andExpect(flash().attribute("errorMessage", containsString("hợp đồng chưa kết thúc")));
        assertFalse(html("/properties", OWNER_1).contains("<h2>Nhà trọ đã ẩn</h2>"));
    }

    @Test
    @DisplayName("API: 200 / 409 / 403 / 404 và danh sách archived=true")
    void apiStatusCodes() throws Exception {
        mockMvc.perform(post("/api/properties/" + BUSY + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("hợp đồng chưa kết thúc")));
        mockMvc.perform(post("/api/properties/" + FREE + "/archive").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/properties/" + FREE + "/archive").header("X-User-Id", STAFF_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/properties/" + UUID.randomUUID() + "/archive").header("X-User-Id", ADMIN))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/properties/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", notNullValue()));
        mockMvc.perform(post("/api/properties/" + FREE + "/archive").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/api/properties").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].id", not(hasItem(FREE))));
        mockMvc.perform(get("/api/properties").param("archived", "true").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].id", hasItem(FREE)));
        mockMvc.perform(get("/api/properties").param("archived", "true").header("X-User-Id", OWNER_2))
            .andExpect(jsonPath("$[*].id", not(hasItem(FREE))));

        mockMvc.perform(post("/api/properties/" + FREE + "/rooms").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"X1\",\"floor\":1,\"basePrice\":3000000,\"maxOccupants\":2,"
                    + "\"electricMeter\":0,\"waterMeter\":0,\"conditionNote\":\"Hiện trạng ban đầu\",\"noAssets\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đã bị ẩn")));

        mockMvc.perform(post("/api/properties/" + FREE + "/restore").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/properties/" + FREE + "/restore").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", nullValue()));
        mockMvc.perform(post("/api/properties/" + FREE + "/restore").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
    }
}
