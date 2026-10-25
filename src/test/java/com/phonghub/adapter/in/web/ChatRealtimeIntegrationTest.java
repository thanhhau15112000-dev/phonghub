package com.phonghub.adapter.in.web;

import com.phonghub.adapter.in.scheduling.ChatSseHeartbeatScheduler;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sự kiện SSE của chat (#201): chỉ người được đọc phòng mới nhận tin và trạng thái đã xem. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRealtimeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("localDemoAuthenticationAdapter")
    private LocalDemoAuthenticationAdapter authAdapter;

    @Autowired
    private InMemoryChatRoomRepository chatRoomRepository;

    @Autowired
    private InMemoryChatMessageRepository chatMessageRepository;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private ChatSseHeartbeatScheduler heartbeatScheduler;

    @BeforeEach
    void setUp() {
        chatRoomRepository.clear();
        chatMessageRepository.clear();
        userRepository.save(new User(
            LocalDemoAuthenticationAdapter.ADMIN_ID, "admin", "admin@phonghub.local",
            "Quản trị viên", "0900000000", UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        ));
        userRepository.save(new User(
            LocalDemoAuthenticationAdapter.OWNER_1_ID, "owner1", "owner1@phonghub.local",
            "Chủ trọ Nguyễn Văn B", "0900000001", UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.now()
        ));
        userRepository.save(new User(
            LocalDemoAuthenticationAdapter.TENANT_1_ID, "tenant1", "tenant1@phonghub.local",
            "Nguyễn Văn A", "0900000002", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        ));
        userRepository.save(new User(
            LocalDemoAuthenticationAdapter.STAFF_1_ID, "staff1", "staff1@phonghub.local",
            "Nhân viên Quận 7", "0900000003", UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        ));
    }

    @Test
    @DisplayName("Tin nhắn 1-1 chỉ đẩy qua SSE tới hai thành viên, người ngoài phòng không nhận")
    void directMessageReachesOnlyRoomMembers() throws Exception {
        MvcResult outsiderStream = openStream(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        MvcResult tenantStream = openStream(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String roomId = directRoomWithTenant(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        String marker = "direct-" + UUID.randomUUID();

        sendMessage(roomId, marker);

        assertTrue(body(tenantStream).contains(marker), "thành viên phòng nhận tin ngay qua SSE");
        assertFalse(body(outsiderStream).contains(marker), "người ngoài phòng 1-1 không được nhận nội dung tin");
    }

    @Test
    @DisplayName("Tin nhắn Kênh Chung đẩy qua SSE tới mọi người đang kết nối")
    void globalMessageReachesEveryone() throws Exception {
        MvcResult staffStream = openStream(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        MvcResult tenantStream = openStream(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
        String marker = "global-" + UUID.randomUUID();

        sendMessage(ChatRoom.GLOBAL_ROOM_ID.toString(), marker);

        assertTrue(body(staffStream).contains(marker));
        assertTrue(body(tenantStream).contains(marker));
    }

    @Test
    @DisplayName("Trạng thái đã xem của phòng 1-1 chỉ gửi cho thành viên phòng")
    void readReceiptReachesOnlyRoomMembers() throws Exception {
        String roomId = directRoomWithTenant(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        MvcResult outsiderStream = openStream(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        MvcResult ownerStream = openStream(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/read")).andExpect(status().isOk());

        assertTrue(body(ownerStream).contains(roomId), "người kia trong phòng thấy trạng thái đã xem");
        assertFalse(body(outsiderStream).contains(roomId), "người ngoài phòng không biết phòng này tồn tại");
    }

    @Test
    @DisplayName("Luồng SSE không cho proxy gom/nén dữ liệu và có heartbeat giữ kết nối")
    void streamDisablesProxyBufferingAndSendsHeartbeat() throws Exception {
        MvcResult stream = openStream(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);

        heartbeatScheduler.heartbeat();

        assertTrue(stream.getResponse().getHeader("Cache-Control").contains("no-transform"));
        assertTrue("no".equals(stream.getResponse().getHeader("X-Accel-Buffering")));
        assertTrue(body(stream).contains("retry:3000"), "trình duyệt tự kết nối lại sau 3 giây");
        assertTrue(body(stream).contains(":ping"), "heartbeat được gửi xuống luồng đang mở");
    }

    @Test
    @DisplayName("Người ngoài phòng 1-1 đánh dấu đã đọc phải bị chặn 403")
    void outsiderCannotMarkDirectRoomAsRead() throws Exception {
        String roomId = directRoomWithTenant(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/read")).andExpect(status().isForbidden());
    }

    private MvcResult openStream(CurrentUser user) throws Exception {
        authAdapter.setCurrentUser(user);
        return mockMvc.perform(get("/api/chat/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();
    }

    /** Owner {@code owner} mở phòng 1-1 với Tenant 1 và trả id phòng; người dùng hiện tại vẫn là owner. */
    private String directRoomWithTenant(CurrentUser owner) throws Exception {
        authAdapter.setCurrentUser(owner);
        String response = mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.TENANT_1_ID + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new tools.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
    }

    private void sendMessage(String roomId, String content) throws Exception {
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"))
            .andExpect(status().isOk());
    }

    private static String body(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
