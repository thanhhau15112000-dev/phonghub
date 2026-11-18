package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.ChatContactScope;
import com.phonghub.application.service.ChatService;
import com.phonghub.domain.exception.UnauthorizedChatAccessException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #345: ChatService áp dụng phạm vi liên hệ cho danh bạ và việc tạo chat riêng; chat đã có từ trước vẫn mở được. */
class ChatContactScopeUnitTest {

    private final UUID viewerId = UUID.randomUUID();
    private final UUID colleagueId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    private InMemoryChatRoomRepository rooms;
    private InMemoryUserRepository users;
    private CurrentUser viewer;
    private Set<UUID> reachable;
    private ChatService chat;
    private ChatEventPublisherPort publisher;
    private int unrestrictedPublishes;
    private Set<UUID> lastAudience;

    @BeforeEach
    void setUp() {
        rooms = new InMemoryChatRoomRepository();
        users = new InMemoryUserRepository();
        for (UUID id : List.of(viewerId, colleagueId, strangerId)) {
            users.save(new User(id, "u" + id.toString().substring(0, 4), id + "@x", "Tên " + id.toString().substring(0, 4),
                "0900000000", UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()));
        }
        viewer = new CurrentUser(viewerId, viewerId + "@x", "Người xem", UserRole.STAFF);
        reachable = Set.of(colleagueId);
        publisher = new ChatEventPublisherPort() {
            @Override
            public void publishNewMessage(ChatRoom room, ChatMessage message) {
                unrestrictedPublishes++;
            }

            @Override
            public void publishNewMessage(ChatRoom room, ChatMessage message, Set<UUID> audience) {
                lastAudience = audience;
            }
        };
        ChatContactScope scope = v -> reachable;
        chat = new ChatService(rooms, new InMemoryChatMessageRepository(), users, publisher, null, 1, 1,
            Clock.fixed(Instant.parse("2026-10-14T05:00:00Z"), ZoneOffset.UTC), scope);
    }

    @Test
    @DisplayName("danh bạ chỉ gồm người trong phạm vi, không gồm người lạ và chính mình")
    void contactsOnlyReachableUsers() {
        assertEquals(List.of(colleagueId), chat.getAvailableContacts(viewer).stream().map(c -> c.userId()).toList());
    }

    @Test
    @DisplayName("tạo chat riêng với người ngoài phạm vi bị từ chối")
    void directRoomWithStrangerIsDenied() {
        assertThrows(UnauthorizedChatAccessException.class, () -> chat.getOrCreateDirectRoom(viewer, strangerId));
    }

    @Test
    @DisplayName("tạo chat riêng với người trong phạm vi được; phạm vi thu hẹp sau đó thì chat cũ vẫn mở được")
    void existingDirectRoomSurvivesScopeChange() {
        ChatRoom created = chat.getOrCreateDirectRoom(viewer, colleagueId);

        reachable = Set.of();

        assertEquals(created.id(), chat.getOrCreateDirectRoom(viewer, colleagueId).id());
        assertTrue(chat.getAvailableContacts(viewer).isEmpty());
    }

    @Test
    @DisplayName("tin kênh chung chỉ đẩy tới người cùng nhà trọ với người gửi và chính người gửi")
    void globalMessageIsPushedOnlyToTheSendersProperty() {
        chat.sendMessage(viewer, ChatRoom.GLOBAL_ROOM_ID, "Thông báo");

        assertEquals(Set.of(colleagueId, viewerId), lastAudience);
        assertEquals(0, unrestrictedPublishes);
    }

    @Test
    @DisplayName("tin phòng riêng đẩy như cũ tới hai thành viên, không dùng danh sách người nhận của kênh chung")
    void directMessageUsesMembershipDelivery() {
        ChatRoom room = chat.getOrCreateDirectRoom(viewer, colleagueId);

        chat.sendMessage(viewer, room.id(), "Chào");

        assertEquals(1, unrestrictedPublishes);
        assertEquals(null, lastAudience);
    }

    @Test
    @DisplayName("tin kênh chung của người ngoài phạm vi không hiện trong danh sách tin, tin của mình và đồng nghiệp thì có")
    void globalMessagesAreFilteredBySender() {
        CurrentUser colleague = new CurrentUser(colleagueId, colleagueId + "@x", "Đồng nghiệp", UserRole.STAFF);
        CurrentUser stranger = new CurrentUser(strangerId, strangerId + "@x", "Người lạ", UserRole.STAFF);
        chat.sendMessage(colleague, ChatRoom.GLOBAL_ROOM_ID, "từ đồng nghiệp");
        chat.sendMessage(stranger, ChatRoom.GLOBAL_ROOM_ID, "từ người lạ");
        chat.sendMessage(viewer, ChatRoom.GLOBAL_ROOM_ID, "của tôi");

        List<String> seen = chat.getMessages(viewer, ChatRoom.GLOBAL_ROOM_ID, null, 30).stream()
            .map(ChatMessage::content).toList();

        assertEquals(List.of("từ đồng nghiệp", "của tôi"), seen);
    }
}
