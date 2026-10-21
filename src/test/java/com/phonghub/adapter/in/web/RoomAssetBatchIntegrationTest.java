package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.domain.model.RoomAsset;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Thêm nhiều tài sản một lần (Jira "Add Facilities to Room in Batch"): chủ trọ, áp cho nhiều phòng, tất cả hoặc không. */
class RoomAssetBatchIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final UUID P104 = DataSeeder.ROOM_104_ID;
    private static final UUID P103 = DataSeeder.ROOM_103_ID;
    /** Phòng của nhà trọ khác. */
    private static final UUID P201 = DataSeeder.ROOM_201_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomAssetRepositoryPort assets;

    private int before104;
    private int before103;

    @BeforeEach
    void countAssets() {
        before104 = count(P104);
        before103 = count(P103);
    }

    private int count(UUID roomId) {
        return assets.findActiveByRoomId(roomId).size();
    }

    private void assertNothingAdded() {
        assertEquals(before104, count(P104));
        assertEquals(before103, count(P103));
    }

    private static MockHttpServletRequestBuilder form(String user, UUID roomId) {
        return post("/rooms/" + roomId + "/assets/batch").header("X-User-Id", user);
    }

    @Test
    @DisplayName("Giao diện: chủ trọ thêm 3 tài sản cho phòng hiện tại và một phòng khác; dòng trống bị bỏ qua")
    void ownerAddsBatchToTwoRooms() throws Exception {
        String page = mockMvc.perform(get("/rooms/" + P104).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Thêm nhiều tài sản"));
        assertTrue(page.contains("value=\"" + P103 + "\""), "có ô chọn phòng khác cùng nhà trọ");
        assertFalse(page.contains("name=\"alsoRoomIds\" value=\"" + P104 + "\""), "không tự liệt kê phòng hiện tại");

        mockMvc.perform(form(OWNER_1, P104)
                .param("itemName", "Giường", "Quạt trần", "", "Tủ lạnh")
                .param("itemQuantity", "1", "", "1", "1")
                .param("itemValue", "", "", "", "5000000")
                .param("alsoRoomIds", P103.toString()))
            .andExpect(flash().attribute("successMessage", "Đã thêm 3 tài sản cho mỗi phòng (2 phòng)."));

        assertEquals(before104 + 3, count(P104));
        assertEquals(before103 + 3, count(P103));
        RoomAsset fridge = assets.findActiveByRoomId(P103).stream().filter(a -> a.name().equals("Tủ lạnh"))
            .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("5000000").compareTo(fridge.estimatedValue()));
        assertEquals(1, assets.findActiveByRoomId(P104).stream().filter(a -> a.name().equals("Quạt trần"))
            .findFirst().orElseThrow().quantity(), "số lượng trống tính là 1");
    }

    @Test
    @DisplayName("Một dòng sai, phòng khác nhà trọ, danh sách trống: báo lỗi và không thêm gì")
    void invalidBatchAddsNothing() throws Exception {
        mockMvc.perform(form(OWNER_1, P104).param("itemName", "Giường", "Quạt").param("itemQuantity", "1", "abc")
                .param("alsoRoomIds", P103.toString()))
            .andExpect(flash().attribute("errorMessage", "Số lượng ở dòng 2 không hợp lệ."));
        mockMvc.perform(form(OWNER_1, P104).param("itemName", "Giường", "Quạt").param("itemQuantity", "1", "0")
                .param("alsoRoomIds", P103.toString()))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(form(OWNER_1, P104).param("itemName", "Giường").param("alsoRoomIds", P201.toString()))
            .andExpect(flash().attribute("errorMessage", "Chỉ áp dụng được cho các phòng cùng một nhà trọ."));
        mockMvc.perform(form(OWNER_1, P104).param("itemName", " ", ""))
            .andExpect(flash().attribute("errorMessage", "Cần nhập ít nhất một tài sản."));
        assertNothingAdded();
    }

    @Test
    @DisplayName("Chủ trọ khác, nhân viên nhà khác, người thuê bị chặn; không thêm gì")
    void othersAreBlocked() throws Exception {
        mockMvc.perform(form(OWNER_2, P104).param("itemName", "Giường"))
            .andExpect(flash().attributeExists("errorMessage"));
        String body = "{\"items\":[{\"name\":\"Giường\"}]}";
        for (String user : List.of(OWNER_2, STAFF_2, TENANT_1)) {
            mockMvc.perform(post("/api/rooms/" + P104 + "/assets/batch").header("X-User-Id", user)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        }
        assertNothingAdded();
    }

    @Test
    @DisplayName("API: 201 trả tài sản của mọi phòng; danh sách trống hoặc tên quá dài 400; phòng lạ 404")
    void api() throws Exception {
        mockMvc.perform(post("/api/rooms/" + P104 + "/assets/batch").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"name\":\"Bàn học\",\"quantity\":2},{\"name\":\"Ghế\"}],"
                    + "\"alsoRoomIds\":[\"" + P103 + "\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(4));
        assertEquals(before104 + 2, count(P104));
        assertEquals(before103 + 2, count(P103));

        mockMvc.perform(post("/api/rooms/" + P104 + "/assets/batch").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rooms/" + P104 + "/assets/batch").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"name\":\"" + "x".repeat(151) + "\"}]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rooms/" + UUID.randomUUID() + "/assets/batch").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"name\":\"Ghế\"}]}"))
            .andExpect(status().isNotFound());
    }
}
