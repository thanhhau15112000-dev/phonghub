package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.domain.model.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** API phòng trả version và bắt buộc gửi lại version khi sửa để không ghi đè lẫn nhau (#314). */
class RoomApiVersionIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String ROOM = "/api/rooms/" + DataSeeder.ROOM_104_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryRoomRepository rooms;

    private static String body(String roomNumber, Long version) {
        return "{\"roomNumber\":\"" + roomNumber + "\",\"floor\":1,\"areaSqm\":20,\"basePrice\":3000000,"
            + "\"maxOccupants\":2" + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private long readVersion() throws Exception {
        String json = mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").isNumber())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    @Test
    @DisplayName("Thiếu version: 400 kèm câu báo tiếng Việt, phòng không đổi")
    void updateWithoutVersionIsRejected() throws Exception {
        mockMvc.perform(put(ROOM).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(body("P104-A", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", containsString("Thiếu version của phòng")));
        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.roomNumber").value("P104"));
    }

    @Test
    @DisplayName("Sửa bằng version đã cũ: 409, không ghi đè; sửa bằng version hiện tại thì thành công")
    void staleVersionConflictsAndCurrentVersionSucceeds() throws Exception {
        long version = readVersion();
        // Người khác vừa lưu phòng: version trong kho tăng (PostgreSQL tự tăng khi lưu, kho in-memory thì không)
        Room room = rooms.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        room.restoreVersion(version + 1);
        rooms.save(room);

        mockMvc.perform(put(ROOM).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(body("P104-B", version)))
            .andExpect(status().isConflict());
        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.roomNumber").value("P104"))
            .andExpect(jsonPath("$.version").value((int) (version + 1)));

        mockMvc.perform(put(ROOM).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(body("P104-A", version + 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomNumber").value("P104-A"));
    }
}
