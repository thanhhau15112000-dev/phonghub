package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("localDemoAuthenticationAdapter")
    private LocalDemoAuthenticationAdapter authAdapter;

    @Autowired
    private InMemoryChatRoomRepository chatRoomRepository;

    @Autowired
    private InMemoryChatMessageRepository chatMessageRepository;

    @Autowired
    private InMemoryUserRepository userRepository;

    @BeforeEach
    void setUp() {
        chatRoomRepository.clear();
        chatMessageRepository.clear();

        // Seed demo users in InMemoryUserRepository
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

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
    }

    @Test
    @DisplayName("DoD: Yêu cầu chưa đăng nhập truy cập API Chat phải trả về 401 Unauthorized")
    void unauthenticatedAccessReturns401() throws Exception {
        authAdapter.setNoUser();

        mockMvc.perform(get("/api/chat/rooms"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chat/stream"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.OWNER_1_ID + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DoD: Người dùng cố tạo phòng chat 1-1 với chính mình phải trả về 409 Conflict")
    void directChatWithSelfReturns409Conflict() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);

        mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.TENANT_1_ID + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", is("Không thể tạo cuộc trò chuyện riêng với chính mình")));
    }

    @Test
    @DisplayName("DoD: Người ngoài phòng chat 1-1 truy cập hoặc gửi tin nhắn phải bị chặn 403 Forbidden")
    void nonMemberAccessDirectChatReturns403Forbidden() throws Exception {
        // Tenant 1 tạo phòng 1-1 với Owner 1
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String responseContent = mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.OWNER_1_ID + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Trích xuất roomId
        tools.jackson.databind.JsonNode rootNode = new tools.jackson.databind.ObjectMapper().readTree(responseContent);
        String roomId = rootNode.get("id").asText();

        // Đổi người dùng sang Staff 1 (người ngoài phòng)
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);

        // Staff 1 cố tình đọc tin nhắn của Tenant 1 và Owner 1 -> 403 Forbidden
        mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail", is("Bạn không có quyền truy cập vào phòng chat riêng này")));

        // Staff 1 cố tình gửi tin nhắn vào phòng này -> 403 Forbidden
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Tin nhắn lén\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail", is("Bạn không có quyền truy cập vào phòng chat riêng này")));
    }

    @Test
    @DisplayName("Luồng gửi và nhận tin nhắn Kênh Chung Ban Quản Lý thành công (200 OK)")
    void normalChatFlowInGlobalRoomReturns200() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);

        // Admin gửi tin nhắn vào Kênh Chung
        mockMvc.perform(post("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Thông báo lịch bảo trì điện tuần tới\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", is("Thông báo lịch bảo trì điện tuần tới")))
            .andExpect(jsonPath("$.senderName", is("Quản trị viên")));

        // Tenant 1 lấy danh sách tin nhắn
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        mockMvc.perform(get("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].content", is("Thông báo lịch bảo trì điện tuần tới")));

        // Kiểm tra danh sách liên hệ
        mockMvc.perform(get("/api/chat/contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)));

        // Kiểm tra số lượng tin chưa đọc
        mockMvc.perform(get("/api/chat/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").isNumber());
    }

    @Test
    @DisplayName("Kiểm tra endpoint /seen và header X-Peer-Last-Read-At trong phòng chat 1-1")
    void directChatSeenStatusTracking() throws Exception {
        // Tenant 1 tạo phòng 1-1 với Owner 1
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String responseContent = mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.OWNER_1_ID + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode rootNode = new tools.jackson.databind.ObjectMapper().readTree(responseContent);
        String roomId = rootNode.get("id").asText();

        // Tenant 1 gửi tin nhắn
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Chào chủ trọ\"}"))
            .andExpect(status().isOk());

        // Owner 1 đọc tin nhắn
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/read"))
            .andExpect(status().isOk());

        // Tenant 1 kiểm tra endpoint seen
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        mockMvc.perform(get("/api/chat/rooms/" + roomId + "/seen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.peerLastReadAt").isNotEmpty());

        mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Peer-Last-Read-At"));
    }

    @Test
    @DisplayName("Upload ảnh qua POST /api/chat/rooms/{roomId}/media trả URL endpoint media, không lộ khóa kho")
    void uploadMediaSuccess() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);

        String body = mockMvc.perform(multipart("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/media")
                .file(new MockMultipartFile("file", "room_sample.png", "image/png", pngBytes()))
                .param("caption", "Ảnh phòng thực tế"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.mediaUrl", matchesPattern(
                "/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages/[0-9a-f-]{36}/media")))
            .andExpect(jsonPath("$.mediaKey").doesNotExist())
            .andExpect(jsonPath("$.mediaType", is("IMAGE")))
            .andExpect(jsonPath("$.content", is("Ảnh phòng thực tế")))
            .andReturn().getResponse().getContentAsString();
        String mediaUrl = new tools.jackson.databind.ObjectMapper().readTree(body).get("mediaUrl").asText();

        mockMvc.perform(get(mediaUrl))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith("memory://private-files/")))
            .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(get("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].mediaUrl", is(mediaUrl)))
            .andExpect(jsonPath("$[0].mediaKey").doesNotExist());
    }

    @Test
    @DisplayName("Sự kiện SSE new_message của tin media mang URL endpoint, không mang khóa kho")
    void mediaSseEventDoesNotExposeStorageKey() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        MvcResult stream = mockMvc.perform(get("/api/chat/stream"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(multipart("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/media")
                .file(new MockMultipartFile("file", "room_sample.png", "image/png", pngBytes())))
            .andExpect(status().isOk());

        String events = stream.getResponse().getContentAsString();
        assertTrue(events.contains("event:new_message"), events);
        assertTrue(events.contains("\"mediaUrl\":\"/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages/"), events);
        assertFalse(events.contains("mediaKey"), events);
        assertFalse(events.contains("chat/" + ChatRoom.GLOBAL_ROOM_ID), events);
    }

    @Test
    @DisplayName("Tải media: người ngoài phòng 1-1 bị 403, sai phòng bị 404, tệp sai định dạng bị 400")
    void mediaEndpointEnforcesRoomAccess() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String room = mockMvc.perform(post("/api/chat/rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.OWNER_1_ID + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String roomId = new tools.jackson.databind.ObjectMapper().readTree(room).get("id").asText();
        String body = mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/media")
                .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String messageId = new tools.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/media")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", "image-content".getBytes())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/chat/rooms/" + ChatRoom.GLOBAL_ROOM_ID + "/messages/" + messageId + "/media"))
            .andExpect(status().isNotFound());

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages/" + messageId + "/media"))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/media")
                .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes())))
            .andExpect(status().isForbidden());
    }

    private static byte[] pngBytes() throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
            "png", out);
        return out.toByteArray();
    }
}
