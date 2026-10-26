package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.config.PhongHubCaches;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Quy tắc và câu báo lỗi của các chức năng chủ trọ: sửa nhà trọ, thêm người thuê, thêm/sửa/xóa tài sản phòng.
 * Người không có quyền nhận câu tiếng Việt, không lộ tên/mã nội bộ; dữ liệu vượt giới hạn cột DB bị chặn (400) thay
 * vì để DB báo lỗi (500); tổng số phòng không nhỏ hơn số phòng đã tạo.
 */
class OwnerRulesAndMessagesIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String PROPERTY = DataSeeder.PROP_1_ID.toString();
    private static final String ROOM = DataSeeder.ROOM_104_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private PropertyRepositoryPort properties;
    @Autowired private PhongHubCaches caches;

    @BeforeEach
    void clearCaches() {
        caches.evictAll();
    }

    private static String propertyJson(String name, int totalRooms) {
        return "{\"name\":\"" + name + "\",\"address\":\"123 Nguyễn Thị Thập, Quận 7\",\"totalRooms\":" + totalRooms + "}";
    }

    @Test
    @DisplayName("Chủ trọ khác: giao diện và API báo tiếng Việt, không kèm tên hay mã nhà trọ")
    void foreignOwnerGetsVietnameseMessage() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/edit").header("X-User-Id", OWNER_2)
                .param("name", "X").param("address", "Y"))
            .andExpect(flash().attribute("errorMessage", AuthorizationService.MSG_NOT_OWNER));
        mockMvc.perform(post("/properties/" + PROPERTY + "/tenants").header("X-User-Id", OWNER_2)
                .param("fullName", "Người lạ").param("identityCardNumber", "079301009999")
                .param("phone", "0912000999").param("email", "stranger@example.test"))
            .andExpect(flash().attribute("errorMessage", AuthorizationService.MSG_NOT_OWNER));
        mockMvc.perform(post("/rooms/" + ROOM + "/assets/template").header("X-User-Id", OWNER_2)
                .param("template", "BASIC"))
            .andExpect(flash().attribute("errorMessage", AuthorizationService.MSG_NOT_OWNER));

        mockMvc.perform(patch("/api/properties/" + PROPERTY).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(propertyJson("X", 10)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value(AuthorizationService.MSG_NOT_OWNER))
            .andExpect(jsonPath("$.detail", not(containsString(PROPERTY))));
        // Nhân viên phụ trách nhà khác
        mockMvc.perform(post("/api/rooms/" + ROOM + "/assets/from-template").header("X-User-Id", STAFF_2)
                .param("template", "BASIC"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value(AuthorizationService.MSG_NOT_ASSIGNED));
    }

    @Test
    @DisplayName("Tên nhà trọ quá 200 ký tự bị chặn 400 (trước đây DB báo lỗi, trang trả 500)")
    void propertyNameLength() throws Exception {
        String tooLong = "N".repeat(201);
        mockMvc.perform(patch("/api/properties/" + PROPERTY).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(propertyJson(tooLong, 10)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Tên nhà trọ tối đa 200 ký tự."));
        mockMvc.perform(post("/properties").header("X-User-Id", OWNER_1)
                .param("name", tooLong).param("address", "1 Lê Lợi").param("totalRooms", "1"))
            .andExpect(flash().attribute("errorMessage", "Tên nhà trọ tối đa 200 ký tự."));
        mockMvc.perform(patch("/api/properties/" + PROPERTY).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(propertyJson("N".repeat(200), 10)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tổng số phòng không được nhỏ hơn số phòng đã tạo; bằng hoặc lớn hơn thì lưu được")
    void totalRoomsNotBelowExistingRooms() throws Exception {
        // Nhà trọ mẫu có 4 phòng (P101–P104)
        mockMvc.perform(patch("/api/properties/" + PROPERTY).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Nhà trọ Xanh - Quận 7", 2)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Tổng số phòng không được nhỏ hơn số phòng đã tạo (4 phòng)."));
        mockMvc.perform(post("/properties/" + PROPERTY + "/edit").header("X-User-Id", OWNER_1)
                .param("name", "Nhà trọ Xanh - Quận 7").param("address", "123 Nguyễn Thị Thập, Quận 7")
                .param("totalRooms", "3"))
            .andExpect(flash().attribute("errorMessage", containsString("không được nhỏ hơn số phòng đã tạo")));

        mockMvc.perform(patch("/api/properties/" + PROPERTY).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Nhà trọ Xanh - Quận 7", 4)))
            .andExpect(status().isOk());
        assertEquals(4, properties.findById(UUID.fromString(PROPERTY)).orElseThrow().totalRooms());
    }

    @Test
    @DisplayName("Tài sản: tên/serial/giá trị vượt giới hạn cột bị chặn ở cả API và form; báo lỗi tiếng Việt")
    void assetLimits() throws Exception {
        String assets = "/api/rooms/" + ROOM + "/assets";
        mockMvc.perform(post(assets).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "T".repeat(151) + "\",\"quantity\":1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(assets).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tủ lạnh\",\"quantity\":1,\"serialNumber\":\"" + "S".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());
        // Form giao diện không qua Bean Validation: trước đây để DB báo lỗi (500), nay domain chặn với câu rõ ràng.
        mockMvc.perform(post("/rooms/" + ROOM + "/assets").header("X-User-Id", OWNER_1)
                .param("name", "Tủ lạnh").param("quantity", "1").param("serialNumber", "S".repeat(101)))
            .andExpect(flash().attribute("errorMessage", "Số serial tối đa 100 ký tự."));
        mockMvc.perform(post(assets).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tủ lạnh\",\"quantity\":1,\"estimatedValue\":100000000000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("Giá trị ước tính quá lớn")));
        mockMvc.perform(post("/rooms/" + ROOM + "/assets").header("X-User-Id", OWNER_1)
                .param("name", "T".repeat(151)).param("quantity", "1"))
            .andExpect(flash().attribute("errorMessage", "Tên tài sản tối đa 150 ký tự."));
        mockMvc.perform(post("/rooms/" + ROOM + "/assets/template").header("X-User-Id", OWNER_1)
                .param("template", "KHONG_CO"))
            .andExpect(flash().attribute("errorMessage", "Mẫu tài sản không hợp lệ (chỉ có BASIC hoặc FULL)."));

        mockMvc.perform(get("/api/assets/" + UUID.randomUUID()).header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Không tìm thấy tài sản."));
    }
}
