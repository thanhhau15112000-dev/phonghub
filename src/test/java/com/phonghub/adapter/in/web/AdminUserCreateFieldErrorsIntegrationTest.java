package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** #247: tạo tài khoản lỗi thì báo theo từng trường, giữ nguyên dữ liệu đã nhập, không redirect. */
class AdminUserCreateFieldErrorsIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Nhiều trường sai cùng lúc: trả lại trang với lỗi từng trường và toàn bộ giá trị đã nhập, kể cả CCCD")
    void allFieldErrorsAtOnceKeepInput() throws Exception {
        String html = mockMvc.perform(post("/admin/users").header("X-User-Id", ADMIN)
                .param("username", "AB").param("email", "khong-hop-le").param("fullName", "Nguyễn Văn A")
                .param("phone", "123").param("role", "TENANT").param("identityCardNumber", "1234567890"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users"))
            .andExpect(model().attribute("createFieldErrors", allOf(hasKey("username"), hasKey("email"),
                hasKey("phone"), hasKey("identityCardNumber"), not(hasKey("fullName")))))
            .andExpect(model().attribute("createForm", allOf(hasEntry("username", "AB"),
                hasEntry("fullName", "Nguyễn Văn A"), hasEntry("role", "TENANT"),
                hasEntry("identityCardNumber", "1234567890"))))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"Nguyễn Văn A\""), "họ tên được điền lại");
        assertTrue(html.contains("value=\"1234567890\""), "CCCD được điền lại");
        assertTrue(html.contains("CCCD gồm 9 hoặc 12 chữ số."), "lỗi hiện ngay ở trường");
        assertTrue(html.contains("is-invalid"));
    }

    @Test
    @DisplayName("Email trùng: chỉ ô email báo lỗi, các ô khác giữ nguyên")
    void duplicateEmailOnlyEmailField() throws Exception {
        mockMvc.perform(post("/admin/users").header("X-User-Id", ADMIN)
                .param("username", "nguoi.thue.moi").param("email", "tenant1@phonghub.local")
                .param("fullName", "Người thuê mới").param("phone", "0912345678").param("role", "STAFF"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("createFieldErrors", allOf(hasKey("email"), not(hasKey("username")),
                not(hasKey("fullName")), not(hasKey("phone")))))
            .andExpect(model().attribute("createErrorMessage", containsString("sửa các trường")));
    }

    @Test
    @DisplayName("Thiếu thông tin người thuê: báo thiếu SĐT và CCCD cùng lúc")
    void tenantRequiresPhoneAndIdentityCard() throws Exception {
        mockMvc.perform(post("/admin/users").header("X-User-Id", ADMIN)
                .param("username", "nguoi.thue.moi").param("email", "moi@example.com")
                .param("fullName", "Người thuê mới").param("role", "TENANT"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("createFieldErrors", allOf(hasKey("phone"), hasKey("identityCardNumber"))));
    }
}
