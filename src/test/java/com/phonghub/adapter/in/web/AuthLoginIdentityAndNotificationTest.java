package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLoginIdentityAndNotificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private InMemoryPropertyRepository propertyRepository;

    @Autowired
    private InMemoryStaffPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemoryTenantRepository tenantRepository;

    @Autowired
    private InMemoryContractRepository contractRepository;

    @Autowired
    private InMemoryMaintenanceTicketRepository ticketRepository;

    @Autowired
    private NotificationUseCase notificationUseCase;

    @Autowired
    private InMemoryNotificationRepository notificationRepository;

    @BeforeEach
    void setup() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        notificationRepository.clear();

        DataSeeder.seedAll(
            userRepository,
            propertyRepository,
            assignmentRepository,
            roomRepository,
            tenantRepository,
            contractRepository,
            ticketRepository
        );
    }

    @Test
    @DisplayName("Login page contains password visibility toggle eye icon")
    void testLoginPageHasEyeToggle() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"togglePasswordBtn\"")))
            .andExpect(content().string(containsString("id=\"eyeIconOpen\"")))
            .andExpect(content().string(containsString("id=\"eyeIconClosed\"")));
    }

    @Test
    @DisplayName("Tenant1 login correctly reflects tenant role and identity, not admin")
    void testTenant1LoginIdentity() throws Exception {
        // tenant1 in LocalDemoIdentityProviderAdapter authenticates with secret123
        MvcResult loginResult = mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "tenant1")
                .param("password", "secret123"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "Session must not be null after login");
        assertEquals(LocalDemoAuthenticationAdapter.TENANT_1_ID, session.getAttribute("currentUserId"));

        // Access dashboard using the authenticated session
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().isOk())
            // Role display in header should be 'Người thuê', NOT 'Quản trị viên'
            .andExpect(content().string(containsString("Người thuê")))
            // Should not show Admin-only "Tài khoản" link
            .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
            // Should not show Admin notification bell
            .andExpect(content().string(not(containsString("id=\"notifBellBtn\""))));
    }

    @Test
    @DisplayName("Forgot password creates password reset notification for Admin")
    void testForgotPasswordCreatesNotification() throws Exception {
        assertEquals(0, notificationUseCase.countUnreadAdminNotifications());

        mockMvc.perform(post("/forgot-password")
                .with(csrf())
                .param("identifier", "tenant1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đã gửi yêu cầu cấp lại mật khẩu cho tài khoản <strong>tenant1</strong>")));

        assertEquals(1, notificationUseCase.countUnreadAdminNotifications());
        List<Notification> list = notificationRepository.findAll();
        assertEquals(1, list.size());
        Notification notif = list.get(0);
        assertEquals(NotificationType.PASSWORD_RESET_REQUEST, notif.type());
        assertEquals("tenant1", notif.targetUsername());
        assertFalse(notif.isResolved());
    }

    @Test
    @DisplayName("Admin sees notification bell with unread badge and can manage notifications")
    void testAdminNotificationManagement() throws Exception {
        // Submit forgot password as tenant1 to trigger notification
        mockMvc.perform(post("/forgot-password")
                .with(csrf())
                .param("identifier", "tenant1"))
            .andExpect(status().isOk());

        // Admin checks dashboard -> bell button exists with badge '1'
        mockMvc.perform(get("/dashboard")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"notifBellBtn\"")))
            .andExpect(content().string(containsString("class=\"notif-badge\"")))
            .andExpect(content().string(containsString("href=\"/admin/notifications\"")));

        // Admin visits notifications page
        mockMvc.perform(get("/admin/notifications")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Danh sách thông báo")))
            .andExpect(content().string(containsString("tenant1")))
            .andExpect(content().string(containsString("Cấp lại mật khẩu")));

        Notification notif = notificationRepository.findAll().get(0);

        // Admin resets password via notification
        mockMvc.perform(post("/admin/notifications/" + notif.id() + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/notifications"))
            .andExpect(flash().attributeExists("resetResult"))
            .andExpect(flash().attributeExists("successMessage"));

        // Verify notification is marked resolved
        Notification updatedNotif = notificationRepository.findById(notif.id()).orElseThrow();
        assertTrue(updatedNotif.isResolved());
        assertNotNull(updatedNotif.resolutionNote());
        assertEquals(0, notificationUseCase.countUnreadAdminNotifications());
    }

    @Test
    @DisplayName("Non-admin user cannot access admin notifications")
    void testNonAdminCannotAccessNotifications() throws Exception {
        mockMvc.perform(get("/admin/notifications")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }
}
