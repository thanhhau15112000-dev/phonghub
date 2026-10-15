package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** #84: lọc phòng qua API và trang nhà trọ trên dữ liệu mẫu (P101 OCCUPIED, P102 MAINTENANCE, P103 RESERVED, P104 AVAILABLE). */
class RoomSearchWebIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String API = "/api/properties/" + DataSeeder.PROP_1_ID + "/rooms";
    private static final String OTHER_API = "/api/properties/" + DataSeeder.PROP_2_ID + "/rooms";
    private static final String PAGE = "/properties/" + DataSeeder.PROP_1_ID;

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("API không tham số trả toàn bộ như cũ và không có header phân trang")
    void unfilteredKeepsOldBehaviour() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(header().doesNotExist("X-Total-Count"));
    }

    @Test
    @DisplayName("API lọc theo trạng thái, khoảng giá, tầng và phân trang")
    void apiFilters() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("status", "AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].roomNumber").value("P104"))
            .andExpect(header().string("X-Total-Count", "1"));

        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("minPrice", "3800000"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(header().string("X-Total-Count", "2"));

        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("floor", "2"))
            .andExpect(jsonPath("$", hasSize(0)))
            .andExpect(header().string("X-Total-Count", "0"));

        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("page", "1").param("size", "3"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(header().string("X-Total-Count", "4"))
            .andExpect(header().string("X-Page", "1"));
    }

    @Test
    @DisplayName("API từ chối tham số sai bằng 400 và vẫn giữ phân quyền")
    void apiValidationAndAuthorization() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("minPrice", "5").param("maxPrice", "1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("size", "1000"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("status", "NOPE"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_2).param("status", "AVAILABLE"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("trang nhà trọ lọc theo trạng thái và giữ giá trị lọc trong form")
    void uiFilters() throws Exception {
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("status", "AVAILABLE"))
            .andExpect(status().isOk()).andExpect(view().name("properties/detail"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("P104"));
        assertFalse(html.contains(">P101<"));
        assertTrue(html.contains("Xóa lọc"));

        String empty = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("floor", "9"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(empty.contains("Không có phòng nào khớp bộ lọc."));
    }

    @Test
    @DisplayName("bộ lọc sai trên trang nhà trọ hiển thị lỗi và vẫn liệt kê phòng")
    void uiInvalidFilter() throws Exception {
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("minPrice", "9000000")
                .param("maxPrice", "1000"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Giá tối thiểu không được lớn hơn giá tối đa."));
        assertTrue(html.contains("P101"));
    }

    @Test
    @DisplayName("người thuê lọc qua API vẫn chỉ thấy phòng của mình")
    void tenantScoped() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", TENANT_1).param("status", "AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(API).header("X-User-Id", TENANT_1).param("status", "OCCUPIED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].roomNumber").value("P101"))
            .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get(API).header("X-User-Id", TENANT_1).param("page", "0").param("size", "10"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    @DisplayName("người thuê không lọc được phòng ở nhà trọ không có hợp đồng")
    void tenantForbiddenOnOtherProperty() throws Exception {
        mockMvc.perform(get(OTHER_API).header("X-User-Id", TENANT_1).param("status", "AVAILABLE"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff/kỹ thuật viên được phân công thì lọc được, staff nhà trọ khác bị 403")
    void staffAndTechnicianScope() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", STAFF_1).param("status", "AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(API).header("X-User-Id", TECH_1).param("maxPrice", "3500000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get(API).header("X-User-Id", STAFF_2).param("status", "AVAILABLE"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(API).header("X-User-Id", STAFF_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin xem được mọi nhà trọ")
    void adminSeesAnyProperty() throws Exception {
        mockMvc.perform(get(OTHER_API).header("X-User-Id", ADMIN).param("status", "AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].roomNumber").value("P201"));
    }

    @Test
    @DisplayName("API chọn trang mà thiếu size, trang âm hoặc giá âm trả 400")
    void apiPagingValidation() throws Exception {
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("page", "1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("page", "-1").param("size", "2"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("minPrice", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(API).header("X-User-Id", OWNER_1).param("floor", "abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("trang nhà trọ: staff không được phân công bị chặn, link trang vượt quá về trang cuối")
    void uiAuthorizationAndPageClamp() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", STAFF_2).param("status", "AVAILABLE"))
            .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("page", "9"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("P101"));
        assertFalse(html.contains("Nhà trọ này chưa có phòng nào."));
    }
}
