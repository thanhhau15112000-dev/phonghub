package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gửi và xem ảnh/video trong chat qua API (#251): kho riêng tư, quyền xem, Range, SSE không lộ khóa tệp. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMediaApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("localDemoAuthenticationAdapter") private LocalDemoAuthenticationAdapter authAdapter;
    @Autowired private InMemoryChatRoomRepository chatRoomRepository;
    @Autowired private InMemoryChatMessageRepository chatMessageRepository;
    @Autowired private InMemoryUserRepository userRepository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chatRoomRepository.clear();
        chatMessageRepository.clear();
        saveUser(LocalDemoAuthenticationAdapter.ADMIN_ID, "admin", "Quản trị viên", UserRole.ADMIN);
        saveUser(LocalDemoAuthenticationAdapter.OWNER_1_ID, "owner1", "Chủ trọ Nguyễn Văn B", UserRole.OWNER);
        saveUser(LocalDemoAuthenticationAdapter.TENANT_1_ID, "tenant1", "Nguyễn Văn A", UserRole.TENANT);
        saveUser(LocalDemoAuthenticationAdapter.STAFF_1_ID, "staff1", "Nhân viên Quận 7", UserRole.STAFF);
    }

    @AfterEach
    void restoreDefaultUser() {
        authAdapter.setGlobalDefaultUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
    }

    private void saveUser(UUID id, String username, String name, UserRole role) {
        userRepository.save(new User(id, username, username + "@phonghub.local", name, "0900000000", role,
            User.UserStatus.ACTIVE, false, Instant.now()));
    }

    private static byte[] image(String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 8, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private static byte[] mp4(int size) {
        byte[] bytes = new byte[size];
        byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(header, 0, bytes, 0, header.length);
        for (int i = header.length; i < size; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("files", name, type, content);
    }

    /** Owner 1 mở phòng 1-1 với Tenant 1; người dùng hiện tại vẫn là owner. */
    private String directRoom() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        String response = mockMvc.perform(post("/api/chat/rooms/direct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.TENANT_1_ID + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private JsonNode send(String roomId, String content, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/chat/rooms/" + roomId + "/attachments");
        for (MockMultipartFile f : files) {
            builder.file(f);
        }
        if (content != null) {
            builder.param("content", content);
        }
        String body = mockMvc.perform(builder).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("objectKey"), "phản hồi không lộ khóa tệp");
        assertFalse(body.contains("\"chat/" + roomId), "phản hồi không lộ khóa tệp");
        return json.readTree(body);
    }

    @Test
    @DisplayName("Owner sends two images with a caption; the tenant sees them in the room and opens them; no object key leaks")
    void sendAndOpenImages() throws Exception {
        String roomId = directRoom();
        JsonNode sent = send(roomId, "Ảnh phòng 101", file("a.jpg", "image/jpeg", image("jpg")),
            file("b.png", "image/png", image("png")));
        assertEquals("Ảnh phòng 101", sent.get("content").asText());
        assertEquals(2, sent.get("attachments").size());
        String url = sent.get("attachments").get(0).get("url").asText();
        assertTrue(url.startsWith("/api/chat/rooms/" + roomId + "/messages/" + sent.get("id").asText() + "/attachments/"));
        assertEquals("IMAGE", sent.get("attachments").get(1).get("mediaType").asText());

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String list = mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(list.contains(url));
        assertFalse(list.contains("objectKey"));

        byte[] content = mockMvc.perform(get(url)).andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0xff, content[0]);
        assertEquals((byte) 0xd8, content[1]);
    }

    @Test
    @DisplayName("Someone outside the direct room gets 403 on the attachment and cannot post into it; anonymous gets 401")
    void outsiderAndAnonymousAreRefused() throws Exception {
        String roomId = directRoom();
        String url = send(roomId, null, file("a.jpg", "image/jpeg", image("jpg"))).get("attachments").get(0).get("url").asText();

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/attachments")
                .file(file("a.jpg", "image/jpeg", image("jpg"))))
            .andExpect(status().isForbidden());

        authAdapter.setNoUser();
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/attachments")
                .file(file("a.jpg", "image/jpeg", image("jpg"))))
            .andExpect(status().isUnauthorized());

        // Sai id tệp trong đúng phòng: không tìm thấy
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        mockMvc.perform(get(url.substring(0, url.lastIndexOf('/') + 1) + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Video is served through the server with Range support so it can be seeked (206)")
    void videoSupportsRange() throws Exception {
        String roomId = directRoom();
        byte[] video = mp4(4000);
        String url = send(roomId, null, file("clip.mp4", "video/mp4", video)).get("attachments").get(0).get("url").asText();

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        MvcResult full = mockMvc.perform(get(url)).andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4")).andReturn();
        assertEquals(4000, full.getResponse().getContentAsByteArray().length);

        byte[] part = mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=100-199"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 100-199/4000"))
            .andReturn().getResponse().getContentAsByteArray();
        assertEquals(100, part.length);
        assertEquals(video[100], part[0]);
        assertEquals(video[199], part[99]);
    }

    @Test
    @DisplayName("Wrong types, renamed files, too many files or two videos are refused with a Vietnamese message and create no message")
    void invalidUploadsAreRefused() throws Exception {
        String roomId = directRoom();
        MockMultipartFile[][] invalid = {
            {file("a.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII))},
            {file("a.avi", "video/x-msvideo", mp4(500))},
            {file("a.mkv", "video/x-matroska", new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2})},
            {file("doi-duoi.jpg", "image/jpeg", "day la van ban".getBytes(StandardCharsets.UTF_8))},
            {file("a.mp4", "video/mp4", mp4(500)), file("b.mp4", "video/mp4", mp4(500))},
            {file("1.jpg", "image/jpeg", image("jpg")), file("2.jpg", "image/jpeg", image("jpg")),
                file("3.jpg", "image/jpeg", image("jpg")), file("4.jpg", "image/jpeg", image("jpg")),
                file("5.jpg", "image/jpeg", image("jpg")), file("6.jpg", "image/jpeg", image("jpg"))},
        };
        for (MockMultipartFile[] files : invalid) {
            MockMultipartHttpServletRequestBuilder builder = multipart("/api/chat/rooms/" + roomId + "/attachments");
            for (MockMultipartFile f : files) {
                builder.file(f);
            }
            mockMvc.perform(builder.param("content", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        mockMvc.perform(multipart("/api/chat/rooms/" + roomId + "/attachments").param("content", "chỉ có chữ"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Cần chọn ít nhất một ảnh hoặc video."));
        mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Public channel: a member posts an image and another member opens it")
    void globalChannel() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        mockMvc.perform(get("/api/chat/rooms")).andExpect(status().isOk());
        String url = send(ChatRoom.GLOBAL_ROOM_ID.toString(), "Thông báo", file("a.png", "image/png", image("png")))
            .get("attachments").get(0).get("url").asText();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
    }

    @Test
    @DisplayName("A media message reaches the other member over SSE in realtime, with links but no object key")
    void mediaMessageOverSse() throws Exception {
        String roomId = directRoom();
        MvcResult tenantStream = openStream(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        MvcResult outsiderStream = openStream(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        JsonNode sent = send(roomId, "realtime-anh", file("a.jpg", "image/jpeg", image("jpg")));

        String body = tenantStream.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("realtime-anh"));
        assertTrue(body.contains(sent.get("attachments").get(0).get("url").asText()));
        assertFalse(body.contains("objectKey"));
        assertFalse(body.contains("\"chat/" + roomId));
        assertFalse(outsiderStream.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("realtime-anh"));
    }

    private MvcResult openStream(CurrentUser user) throws Exception {
        authAdapter.setCurrentUser(user);
        return mockMvc.perform(get("/api/chat/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();
    }
}
