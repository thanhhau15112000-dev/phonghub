package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhãn tiếng Việt và kiểu bảng chung trên các trang trước đây hiện mã enum hoặc bảng không định dạng. */
class DisplayLabelsUiIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();

    @Autowired private MockMvc mockMvc;

    private String page(String path, String userId) throws Exception {
        return mockMvc.perform(get(path).header("X-User-Id", userId)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Nhật ký: bộ lọc đối tượng hiện nhãn tiếng Việt, gồm cả hóa đơn và xuất dữ liệu")
    void auditTargetLabels() throws Exception {
        String audit = page("/audit", ADMIN);
        assertTrue(audit.contains("value=\"MAINTENANCE_TICKET\">Phiếu bảo trì</option>"), audit);
        assertTrue(audit.contains("value=\"INVOICE\">Hóa đơn</option>"));
        assertTrue(audit.contains("value=\"EXPORT\">Xuất dữ liệu</option>"));
    }

    @Test
    @DisplayName("Trạng thái tài khoản hiện tiếng Việt trên trang quản lý tài khoản và hồ sơ")
    void userStatusLabels() throws Exception {
        String users = page("/admin/users", ADMIN);
        assertTrue(users.contains("Đang hoạt động"));
        assertFalse(users.contains(">ACTIVE</span>"));

        String profile = page("/account/profile", OWNER_1);
        assertTrue(profile.contains("Đang hoạt động"));
        assertFalse(profile.contains(">ACTIVE</span>"));
    }

    @Test
    @DisplayName("Menu tài khoản có avatar, hồ sơ và đăng xuất; thông báo nằm ngoài menu")
    void accountActionsAreGroupedInProfileMenu() throws Exception {
        String html = page("/dashboard", OWNER_1);
        int notification = html.indexOf("title=\"Thông báo của bạn\"");
        int accountMenu = html.indexOf("id=\"accountMenu\"");
        int profile = html.indexOf("Chi tiết hồ sơ", accountMenu);
        int password = html.indexOf("Đổi mật khẩu", accountMenu);
        int logout = html.indexOf("Đăng xuất", accountMenu);

        assertTrue(notification >= 0, "Chuông thông báo cá nhân phải được giữ lại");
        assertTrue(accountMenu > notification, "Chuông thông báo phải nằm ngoài menu hồ sơ");
        assertTrue(html.contains("id=\"accountMenuButton\""));
        assertTrue(html.contains("class=\"account-menu-avatar\""));
        assertTrue(html.contains("class=\"account-menu-trigger-name\""));
        assertTrue(html.contains("class=\"account-menu-name\""));
        assertTrue(html.contains("class=\"account-menu-role\">Chủ trọ</span>"));
        assertTrue(profile >= accountMenu, "Chi tiết hồ sơ phải là mục đầu tiên trong menu");
        assertTrue(password > profile, "Đổi mật khẩu phải nằm sau chi tiết hồ sơ");
        assertTrue(logout > password, "Đăng xuất phải nằm cuối menu");
        assertFalse(html.contains("id=\"demoUserSwitcher\""), "Menu hồ sơ không được cho chuyển sang tài khoản khác");
        assertFalse(html.contains("action=\"/switch-user\""));
        assertTrue(html.contains("action=\"/session/logout\""));
        assertTrue(html.contains("class=\"account-menu-logout-button\""));
    }

    @Test
    @DisplayName("Bảng người thuê và nhân sự dùng kiểu data-table chung")
    void tablesUseSharedStyle() throws Exception {
        for (String path : new String[] {
            "/properties/" + DataSeeder.PROP_1_ID + "/tenants",
            "/properties/" + DataSeeder.PROP_1_ID + "/staff"}) {
            String html = page(path, OWNER_1);
            assertTrue(html.contains("class=\"data-table\""), path);
            assertFalse(html.contains("class=\"table\""), path);
        }
    }

    @Test
    @DisplayName("UiText: nhãn trạng thái tài khoản và đối tượng nhật ký, mã lạ giữ nguyên")
    void uiText() {
        assertEquals("Tạm khóa", UiText.INSTANCE.userStatus(User.UserStatus.SUSPENDED));
        assertEquals("Ngừng hoạt động", UiText.INSTANCE.userStatus(User.UserStatus.INACTIVE));
        assertEquals("Chưa xác định", UiText.INSTANCE.userStatus(null));
        assertEquals("Phòng", UiText.INSTANCE.auditTarget("ROOM"));
        assertEquals("NEW_KIND", UiText.INSTANCE.auditTarget("NEW_KIND"));
        assertEquals("—", UiText.INSTANCE.auditTarget(null));
    }
}
