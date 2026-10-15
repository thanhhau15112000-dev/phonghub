package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.config.PhongHubCaches;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** #88: cache bật thật — không dùng chung giữa các chủ trọ và không trả dữ liệu cũ sau khi dữ liệu đổi. */
@TestPropertySource(properties = "phonghub.cache.enabled=true")
class CacheIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String ROOMS = "/api/properties/" + DataSeeder.PROP_1_ID + "/rooms";

    @Autowired private MockMvc mockMvc;
    @Autowired private PhongHubCaches caches;

    /** Dữ liệu mẫu được nạp lại thẳng vào repository (không qua service) nên phải xóa cache giữa các test. */
    @BeforeEach
    void clearCaches() {
        caches.evictAll();
    }

    private String roomStatus(String user, String roomNumber) throws Exception {
        String body = mockMvc.perform(get(ROOMS).header("X-User-Id", user)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$[?(@.roomNumber == '" + roomNumber + "')].status").getFirst();
    }

    private int occupied(String user) throws Exception {
        String body = mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", user))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.occupancy.occupied");
    }

    @Test
    @DisplayName("Hai chủ trọ không dùng chung cache; người không có quyền vẫn 403 sau khi người khác đã cache")
    void ownersNeverShareEntries() throws Exception {
        mockMvc.perform(get("/api/properties").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem("Nhà trọ Xanh - Quận 7")));
        mockMvc.perform(get(ROOMS).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        assertTrue(caches.size() >= 2, "danh sách đã được cache");

        mockMvc.perform(get("/api/properties").header("X-User-Id", OWNER_2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", not(hasItem("Nhà trọ Xanh - Quận 7"))));
        mockMvc.perform(get(ROOMS).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(get(ROOMS).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sửa nhà trọ và thêm phòng hiện ngay, không trả bản cũ trong cache")
    void writesThroughUseCasesEvict() throws Exception {
        mockMvc.perform(get("/api/properties").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].name", hasItem("Nhà trọ Xanh - Quận 7")));
        mockMvc.perform(patch("/api/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nhà trọ Xanh Mới\",\"address\":\"123 Nguyễn Thị Thập, Quận 7\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/properties").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].name", hasItem("Nhà trọ Xanh Mới")))
            .andExpect(jsonPath("$[*].name", not(hasItem("Nhà trọ Xanh - Quận 7"))));

        int before = JsonPath.<java.util.List<?>>read(mockMvc.perform(get(ROOMS).header("X-User-Id", OWNER_1))
            .andReturn().getResponse().getContentAsString(), "$").size();
        mockMvc.perform(post(ROOMS).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P999\",\"floor\":9,\"areaSqm\":20,\"basePrice\":3000000,\"maxOccupants\":2}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get(ROOMS).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(before + 1));
    }

    @Test
    @DisplayName("Kích hoạt hợp đồng (ghi trong transaction ở service khác) làm mới danh sách phòng và dashboard owner")
    void committedTransactionEvicts() throws Exception {
        assertEquals("AVAILABLE", roomStatus(OWNER_1, "P104"));
        int occupiedBefore = occupied(OWNER_1);
        assertEquals("AVAILABLE", roomStatus(OWNER_1, "P104"), "đọc lại từ cache");
        mockMvc.perform(get(ROOMS).param("status", "AVAILABLE").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].roomNumber", hasItem("P104")));

        String created = mockMvc.perform(post("/api/contracts").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"propertyId": "%s", "roomId": "%s", "primaryTenantFullName": "Lê Mới",
                     "primaryTenantIdCard": "079201009999", "primaryTenantPhone": "0901112223",
                     "depositAmount": 1000000, "rentAmount": 4000000, "startDate": "%s", "endDate": "%s",
                     "paymentDay": 5}
                    """.formatted(DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, LocalDate.now(),
                    LocalDate.now().plusMonths(6))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/" + JsonPath.read(created, "$.id") + "/activate")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());

        assertEquals("OCCUPIED", roomStatus(OWNER_1, "P104"));
        assertEquals(occupiedBefore + 1, occupied(OWNER_1));
        mockMvc.perform(get(ROOMS).param("status", "AVAILABLE").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$[*].roomNumber", not(hasItem("P104"))));
    }
}
