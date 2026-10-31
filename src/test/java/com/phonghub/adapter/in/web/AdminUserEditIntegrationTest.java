package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** #89: admin sửa thông tin, vai trò, trạng thái tài khoản — 403 người không phải admin, 409 khi còn ràng buộc. */
class AdminUserEditIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    /** Chủ trọ chưa sở hữu nhà trọ nào trong dữ liệu mẫu. */
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepositoryPort users;

    private static String body(String fullName, String phone, String role, String status) {
        return "{\"fullName\":\"" + fullName + "\",\"phone\":" + (phone == null ? "null" : "\"" + phone + "\"")
            + ",\"role\":\"" + role + "\",\"status\":\"" + status + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions patchUser(String caller, String target, String json)
        throws Exception {
        return mockMvc.perform(patch("/api/admin/users/" + target).header("X-User-Id", caller)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("API: admin đổi tên, số điện thoại, vai trò và khóa tài khoản không còn ràng buộc")
    void adminUpdatesUser() throws Exception {
        patchUser(ADMIN, OWNER_2, body("Trần Thị C (đã đổi)", "0911222333", "STAFF", "SUSPENDED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.email").value("owner2@phonghub.local"));
        User saved = users.findById(UUID.fromString(OWNER_2)).orElseThrow();
        assertEquals("Trần Thị C (đã đổi)", saved.fullName());
        assertEquals("0911222333", saved.phone());
        assertEquals(UserRole.STAFF, saved.role());
        assertEquals(User.UserStatus.SUSPENDED, saved.status());
    }

    @Test
    @DisplayName("API: không phải admin 403, id lạ 404, thiếu dữ liệu 400")
    void permissionsAndValidation() throws Exception {
        patchUser(OWNER_1, OWNER_2, body("x", null, "OWNER", "ACTIVE")).andExpect(status().isForbidden());
        patchUser(STAFF_1, OWNER_2, body("x", null, "OWNER", "ACTIVE")).andExpect(status().isForbidden());
        patchUser(ADMIN, UUID.randomUUID().toString(), body("x", null, "OWNER", "ACTIVE"))
            .andExpect(status().isNotFound());
        patchUser(ADMIN, OWNER_2, body(" ", null, "OWNER", "ACTIVE")).andExpect(status().isBadRequest());
        patchUser(ADMIN, OWNER_2, body("Tên", "12345", "OWNER", "ACTIVE")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API: 409 khi đổi vai trò còn ràng buộc, sửa admin, tự sửa mình, nâng lên admin, trùng số điện thoại")
    void conflicts() throws Exception {
        patchUser(ADMIN, OWNER_1, body("Chủ trọ Nguyễn Văn B", null, "STAFF", "ACTIVE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đang sở hữu")));
        patchUser(ADMIN, STAFF_1, body("Nhân viên Quận 7", null, "TECHNICIAN", "ACTIVE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("phân công")));
        patchUser(ADMIN, TENANT_1, body("Nguyễn Văn A", null, "OWNER", "ACTIVE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("hồ sơ người thuê")));
        patchUser(ADMIN, ADMIN, body("Quản trị viên", null, "ADMIN", "ACTIVE")).andExpect(status().isConflict());
        patchUser(ADMIN, OWNER_2, body("Trần Thị C", null, "ADMIN", "ACTIVE")).andExpect(status().isConflict());
        String techPhone = users.findById(UUID.fromString(TECH_1)).orElseThrow().phone();
        patchUser(ADMIN, OWNER_2, body("Trần Thị C", techPhone, "OWNER", "ACTIVE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("đã được sử dụng")));

        // Giữ nguyên vai trò thì vẫn sửa được thông tin dù còn ràng buộc.
        patchUser(ADMIN, STAFF_1, body("Nhân viên Quận 7 (ca sáng)", null, "STAFF", "ACTIVE"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Giao diện: nút Sửa ở danh sách, trang sửa lưu được; tài khoản admin không mở trang sửa")
    void ui() throws Exception {
        String list = mockMvc.perform(get("/admin/users").header("X-User-Id", ADMIN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains("/admin/users/" + OWNER_2 + "/edit"));

        String form = mockMvc.perform(get("/admin/users/" + OWNER_2 + "/edit").header("X-User-Id", ADMIN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(form.contains("Sửa tài khoản"));
        assertTrue(!form.contains("value=\"ADMIN\""), "không có lựa chọn vai trò quản trị viên");

        mockMvc.perform(post("/admin/users/" + OWNER_2 + "/edit").header("X-User-Id", ADMIN)
                .param("fullName", "Trần Thị C").param("phone", "0900000006")
                .param("role", "TECHNICIAN").param("status", "INACTIVE"))
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật tài khoản")));
        assertEquals(UserRole.TECHNICIAN, users.findById(UUID.fromString(OWNER_2)).orElseThrow().role());

        mockMvc.perform(post("/admin/users/" + OWNER_1 + "/edit").header("X-User-Id", ADMIN)
                .param("fullName", "Chủ trọ Nguyễn Văn B").param("role", "STAFF").param("status", "ACTIVE"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("admin/user-edit"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                "errorMessage", containsString("đang sở hữu")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                "form", org.hamcrest.Matchers.hasEntry("fullName", "Chủ trọ Nguyễn Văn B")));

        // #247: nhiều trường sai cùng lúc: báo lỗi từng trường, giữ nguyên giá trị đã nhập
        mockMvc.perform(post("/admin/users/" + OWNER_2 + "/edit").header("X-User-Id", ADMIN)
                .param("fullName", " ").param("phone", "12ab").param("role", "STAFF").param("status", "ACTIVE"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("admin/user-edit"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                "fieldErrors", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.hasKey("fullName"),
                    org.hamcrest.Matchers.hasKey("phone"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                "form", org.hamcrest.Matchers.hasEntry("phone", "12ab")));

        mockMvc.perform(get("/admin/users/" + ADMIN + "/edit").header("X-User-Id", ADMIN))
            .andExpect(redirectedUrl("/admin/users"));
        mockMvc.perform(get("/admin/users/" + OWNER_2 + "/edit").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/"));
    }
}
