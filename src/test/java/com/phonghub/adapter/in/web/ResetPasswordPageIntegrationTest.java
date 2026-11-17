package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Trang đặt lại mật khẩu khi chạy không có Supabase (profile test): báo chưa bật, không đổi gì (#330). */
class ResetPasswordPageIntegrationTest extends SeededWebTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Trang đặt lại mật khẩu mở được, đọc token từ phần sau dấu # và xóa khỏi thanh địa chỉ")
    void pageReadsTokenFromFragment() throws Exception {
        mockMvc.perform(get("/reset-password"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("emailRecoveryAvailable", false))
            .andExpect(content().string(containsString("id=\"resetPasswordForm\"")))
            .andExpect(content().string(containsString("params.get('type') === 'recovery'")))
            .andExpect(content().string(containsString("history.replaceState")));
    }

    @Test
    @DisplayName("Không có Supabase: gửi form báo chưa bật đặt lại qua email")
    void postWithoutProviderShowsNotEnabled() throws Exception {
        mockMvc.perform(post("/reset-password")
                .param("recoveryToken", "tok")
                .param("newPassword", "MatKhauMoi#2026")
                .param("confirmPassword", "MatKhauMoi#2026"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage",
                "Hệ thống chưa bật đặt lại mật khẩu qua email. Hãy liên hệ Quản trị viên."));
    }

    @Test
    @DisplayName("Trang đăng nhập chuyển liên kết khôi phục (Site URL) sang trang đặt lại mật khẩu")
    void loginForwardsRecoveryFragment() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("window.location.replace('/reset-password' + window.location.hash)")));
    }
}
