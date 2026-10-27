package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Tin cũ trước #201 vẫn xem được tệp (#251): chỉ qua endpoint kiểm tra quyền, chỉ chuyển tới bucket cũ của hệ thống. */
@SpringBootTest(properties = "supabase.url=https://demo.supabase.co/")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatLegacyMediaApiIntegrationTest {

    private static final String OLD_BUCKET_URL = "https://demo.supabase.co/storage/v1/object/public/chat-media/old.jpg";

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

    private UUID directRoom() throws Exception {
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        String response = mockMvc.perform(post("/api/chat/rooms/direct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + LocalDemoAuthenticationAdapter.TENANT_1_ID + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private ChatMessage oldMessage(UUID roomId, String mediaUrl) {
        return chatMessageRepository.save(new ChatMessage(UUID.randomUUID(), roomId, LocalDemoAuthenticationAdapter.OWNER_1_ID,
            "Chủ trọ Nguyễn Văn B", "Ảnh cũ", mediaUrl, "IMAGE", Instant.now(), null));
    }

    private static String endpoint(UUID roomId, ChatMessage message) {
        return "/api/chat/rooms/" + roomId + "/messages/" + message.id() + "/legacy-media";
    }

    @Test
    @DisplayName("An old message keeps its image: room readers are redirected to the old bucket, the API never returns that URL")
    void oldMessageStillShowsItsImage() throws Exception {
        UUID roomId = directRoom();
        String url = endpoint(roomId, oldMessage(roomId, OLD_BUCKET_URL));

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);
        String list = mockMvc.perform(get("/api/chat/rooms/" + roomId + "/messages")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(list.contains("\"legacyMediaUrl\":\"" + url + "\""), list);
        assertTrue(list.contains("\"legacyMediaType\":\"IMAGE\""), list);
        assertFalse(list.contains("supabase.co"), "API không trả URL bucket cũ");

        mockMvc.perform(get(url)).andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, OLD_BUCKET_URL))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_STAFF_1);
        mockMvc.perform(get(url)).andExpect(status().isForbidden());

        authAdapter.setNoUser();
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Old local files, URLs outside the old bucket and messages without media are not found")
    void otherOldUrlsAreNotFound() throws Exception {
        UUID roomId = directRoom();
        authAdapter.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TENANT_1);

        mockMvc.perform(get(endpoint(roomId, oldMessage(roomId, "/uploads/chat-media/old.jpg"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(get(endpoint(roomId, oldMessage(roomId, "https://demo.supabase.co.evil.example/storage/v1/object/public/x.jpg"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(get(endpoint(roomId, oldMessage(roomId, "https://demo.supabase.co/storage/v1/object/sign/private-files/x.jpg"))))
            .andExpect(status().isNotFound());

        ChatMessage text = chatMessageRepository.save(ChatMessage.create(roomId, LocalDemoAuthenticationAdapter.OWNER_1_ID,
            "Chủ trọ Nguyễn Văn B", "Chỉ có chữ", Instant.now()));
        mockMvc.perform(get(endpoint(roomId, text))).andExpect(status().isNotFound());
    }
}
