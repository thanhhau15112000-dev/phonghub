package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** #78: trang nhật ký — owner chỉ thấy nhà mình, admin thấy tất cả, vai trò khác bị chặn. */
class AuditLogUiIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryAuditAdapter audit;

    private String page(String userId, String... queryParams) throws Exception {
        var request = get("/audit").header("X-User-Id", userId);
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            request = request.param(queryParams[i], queryParams[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andExpect(view().name("audit/list"))
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("sửa phòng được ghi nhật ký; owner chủ nhà thấy, owner nhà khác không thấy, admin thấy")
    void ownerScope() throws Exception {
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/edit").header("X-User-Id", OWNER_1)
                .param("roomNumber", "P104-AUDIT").param("floor", "1").param("basePrice", "4100000")
                .param("maxOccupants", "3"))
            .andExpect(status().is3xxRedirection());

        String ownerOne = page(OWNER_1);
        assertTrue(ownerOne.contains("<code>ROOM_UPDATED</code>"));
        assertTrue(ownerOne.contains("<span>Sửa phòng</span>"), "hành động có nhãn tiếng Việt");
        assertTrue(ownerOne.contains("P104-AUDIT"));

        assertFalse(page(OWNER_2).contains("P104-AUDIT"), "owner nhà khác không được thấy");
        assertTrue(page(ADMIN).contains("P104-AUDIT"));
    }

    @Test
    @DisplayName("lọc theo hành động và đối tượng")
    void filters() throws Exception {
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/edit").header("X-User-Id", OWNER_1)
                .param("roomNumber", "P104-F").param("floor", "1").param("basePrice", "4100000")
                .param("maxOccupants", "3"))
            .andExpect(status().is3xxRedirection());

        assertTrue(page(OWNER_1, "action", "ROOM_UPDATED", "targetType", "ROOM").contains("P104-F"));
        assertTrue(page(OWNER_1, "action", " room_updated ").contains("P104-F"), "mã hành động không phân biệt hoa thường");
        assertFalse(page(OWNER_1, "action", "CONTRACT_CREATED").contains("P104-F"));
        assertFalse(page(OWNER_1, "targetType", "USER").contains("P104-F"));
    }

    @Test
    @DisplayName("bộ lọc hành động là danh sách chọn có nhãn; mã lạ trên URL vẫn được giữ trong ô chọn")
    void actionDropdown() throws Exception {
        String html = page(OWNER_1);
        assertTrue(html.contains("<option value=\"ROOM_UPDATED\">Sửa phòng</option>"));
        assertTrue(html.contains("<optgroup label=\"Người thuê và giấy tờ\">"));

        assertTrue(page(OWNER_1, "action", "future_action").contains("<option value=\"FUTURE_ACTION\" selected>"));
    }

    @Test
    @DisplayName("owner thấy lần xuất dữ liệu của chính mình trong nhật ký")
    void ownerSeesOwnExport() throws Exception {
        audit.clear(); // nhật ký bộ nhớ dùng chung giữa các lớp test; bỏ lần xuất của test khác
        mockMvc.perform(get("/exports/download").header("X-User-Id", OWNER_1)
                .param("dataset", "contracts").param("format", "xlsx"))
            .andExpect(status().isOk());

        String exports = page(OWNER_1, "targetType", "EXPORT");
        assertTrue(exports.contains("<code>DATA_EXPORTED</code>"));
        assertTrue(exports.contains("<span>Xuất Excel/PDF</span>"));
        assertFalse(page(OWNER_2, "targetType", "EXPORT").contains("<code>DATA_EXPORTED</code>"), "owner khác không thấy");
    }

    @Test
    @DisplayName("owner chọn nhà trọ của người khác thì bị chuyển hướng, người thuê bị chặn")
    void accessControl() throws Exception {
        mockMvc.perform(get("/audit").header("X-User-Id", OWNER_2).param("propertyId", DataSeeder.PROP_1_ID.toString()))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/audit").header("X-User-Id", TENANT_1)).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("bộ lọc sai định dạng hiển thị thông báo thay vì lỗi 500")
    void invalidFilter() throws Exception {
        assertTrue(page(ADMIN, "from", "not-a-date").contains("Bộ lọc không hợp lệ"));
        assertTrue(page(ADMIN, "actorId", "not-a-uuid").contains("Bộ lọc không hợp lệ"));
    }
}
