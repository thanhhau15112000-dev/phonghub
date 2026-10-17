package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.domain.model.Notification;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** #86: trung tâm thông báo cho mọi vai trò — yêu cầu check-in/out, quyền xem/đánh dấu, tách khỏi việc của admin. */
class NotificationCenterIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String HANDOVER_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover";
    private static final String BODY = "{\"initialElectricMeter\":1500,\"initialWaterMeter\":40,"
        + "\"roomCondition\":\"Phòng sạch\",\"keysProvided\":\"2 chìa khóa\"}";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryNotificationRepository notifications;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @Autowired private InMemoryUserRepository users;

    @BeforeEach
    void reset() {
        notifications.clear();
        handovers.clear();
        photos.clear();
        items.clear();
        documents.clear();
    }

    private List<Notification> to(UUID userId) {
        return notifications.findAll().stream().filter(n -> userId.equals(n.targetUserId())).toList();
    }

    /** Chủ trọ gửi biên bản check-in rồi người thuê xác nhận. */
    private void sendAndConfirmCheckIn() throws Exception {
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(CONTRACT + "/handover/photos")
                    .file(new MockMultipartFile("file", "p.png", "image/png", PNG))
                    .param("kind", kind).header("X-User-Id", OWNER_1))
                .andExpect(status().isCreated());
        }
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());

        assertEquals(List.of("Biên bản check-in cần bạn xác nhận"),
            to(LocalDemoAuthenticationAdapter.TENANT_1_ID).stream().map(Notification::title).toList());
        assertTrue(to(LocalDemoAuthenticationAdapter.OWNER_1_ID).isEmpty(), "chưa có gì chờ chủ trọ duyệt");

        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart("/api/properties/" + DataSeeder.PROP_1_ID
                + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents/CCCD").with(request -> {
                request.setMethod("PUT");
                return request;
            }).file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Gửi biên bản check-in báo người thuê; người thuê xác nhận thì báo chủ trọ và staff quản lý hợp đồng")
    void handoverRequestsNotifyTheRightPeople() throws Exception {
        sendAndConfirmCheckIn();

        Set<UUID> approvers = notifications.findAll().stream()
            .filter(n -> n.title().equals("Biên bản check-in chờ duyệt"))
            .map(Notification::targetUserId).collect(Collectors.toSet());
        assertEquals(Set.of(LocalDemoAuthenticationAdapter.OWNER_1_ID, LocalDemoAuthenticationAdapter.STAFF_1_ID),
            approvers, "kỹ thuật viên và staff nhà khác không nhận");
        assertTrue(notifications.findAll().stream().allMatch(n -> HANDOVER_PAGE.equals(n.link())));
    }

    @Test
    @DisplayName("API: mỗi người chỉ thấy và đánh dấu thông báo của mình (403), id lạ 404, đánh dấu tất cả")
    void apiOwnershipAndReadState() throws Exception {
        sendAndConfirmCheckIn();
        Notification tenantNote = to(LocalDemoAuthenticationAdapter.TENANT_1_ID).getFirst();

        mockMvc.perform(get("/api/notifications").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.unread").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Biên bản check-in cần bạn xác nhận"))
            .andExpect(jsonPath("$.items[0].link").value(HANDOVER_PAGE))
            .andExpect(jsonPath("$.items[0].read").value(false));

        mockMvc.perform(post("/api/notifications/" + tenantNote.id() + "/read").header("X-User-Id", OWNER_1))
            .andExpect(status().isForbidden());
        assertFalse(notifications.findById(tenantNote.id()).orElseThrow().isRead());
        mockMvc.perform(post("/api/notifications/" + UUID.randomUUID() + "/read").header("X-User-Id", TENANT_1))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notifications/" + tenantNote.id() + "/read").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/api/notifications").param("unread", "true").header("X-User-Id", TENANT_1))
            .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(post("/api/notifications/read-all").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(1));
        assertFalse(to(LocalDemoAuthenticationAdapter.OWNER_1_ID).getFirst().isRead(), "không đụng thông báo của chủ trọ");
    }

    @Test
    @DisplayName("Trang thông báo: header có chuông và số chưa đọc; mở thông báo đánh dấu đã đọc rồi chuyển tới biên bản")
    void notificationPageAndOpen() throws Exception {
        sendAndConfirmCheckIn();
        Notification ownerNote = to(LocalDemoAuthenticationAdapter.OWNER_1_ID).getFirst();

        mockMvc.perform(get("/notifications").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Biên bản check-in chờ duyệt")))
            .andExpect(content().string(containsString("title=\"Thông báo của bạn\"")))
            .andExpect(content().string(containsString("Đánh dấu tất cả đã đọc")));

        mockMvc.perform(post("/notifications/" + ownerNote.id() + "/open").header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(HANDOVER_PAGE));
        assertTrue(notifications.findById(ownerNote.id()).orElseThrow().isRead());

        Notification tenantNote = to(LocalDemoAuthenticationAdapter.TENANT_1_ID).getFirst();
        mockMvc.perform(post("/notifications/" + tenantNote.id() + "/open").header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/notifications"));
        assertFalse(notifications.findById(tenantNote.id()).orElseThrow().isRead(), "không mở được thông báo của người khác");
    }

    @Test
    @DisplayName("Trang admin chỉ còn việc của quản trị viên; mật khẩu tạm không bị lưu vào ghi chú xử lý")
    void adminInboxOnlyHasAdminTasks() throws Exception {
        var owner = users.findById(LocalDemoAuthenticationAdapter.OWNER_1_ID).orElseThrow();
        var tenant = users.findById(LocalDemoAuthenticationAdapter.TENANT_1_ID).orElseThrow();
        Notification ownerPayment = notifications.save(
            Notification.createForUser("Nhận thanh toán SePay", "Riêng của chủ trọ", owner, null));
        Notification reset = notifications.save(Notification.createPasswordResetRequest(tenant));

        mockMvc.perform(get("/admin/notifications").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Yêu cầu cấp lại mật khẩu")))
            .andExpect(content().string(not(containsString("Riêng của chủ trọ"))));

        mockMvc.perform(post("/admin/notifications/" + ownerPayment.id() + "/reset-password").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection());
        assertFalse(notifications.findById(ownerPayment.id()).orElseThrow().isResolved(),
            "thông báo cá nhân không dùng để cấp lại mật khẩu");

        AuthUseCase.PasswordResetResult result = (AuthUseCase.PasswordResetResult) mockMvc.perform(
                post("/admin/notifications/" + reset.id() + "/reset-password").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andReturn().getFlashMap().get("resetResult");
        Notification resolved = notifications.findById(reset.id()).orElseThrow();
        assertTrue(resolved.isResolved());
        assertFalse(resolved.resolutionNote().contains(result.temporaryPassword()), resolved.resolutionNote());
    }
}
