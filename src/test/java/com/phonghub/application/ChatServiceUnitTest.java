package com.phonghub.application;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.ChatService;
import com.phonghub.domain.exception.ChatRoomNotFoundException;
import com.phonghub.domain.exception.InvalidChatTargetException;
import com.phonghub.domain.exception.UnauthorizedChatAccessException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceUnitTest {

    /** Đồng hồ luôn tăng: hai tin gửi liền nhau không trùng thời điểm dù đồng hồ máy (Windows) có độ phân giải thấp. */
    private static final class IncreasingClock extends Clock {
        private Instant last = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public synchronized Instant instant() {
            Instant now = Instant.now();
            last = now.isAfter(last) ? now : last.plusNanos(1_000);
            return last;
        }
    }

    private InMemoryChatRoomRepository chatRoomRepo;
    private InMemoryChatMessageRepository chatMessageRepo;
    private InMemoryUserRepository userRepo;
    private ChatEventPublisherPort eventPublisher;
    private ChatService chatService;

    private CurrentUser userA;
    private CurrentUser userB;
    private CurrentUser userC;

    @BeforeEach
    void setUp() {
        chatRoomRepo = new InMemoryChatRoomRepository();
        chatMessageRepo = new InMemoryChatMessageRepository();
        userRepo = new InMemoryUserRepository();
        eventPublisher = (roomId, message) -> {}; // no-op for unit test

        chatService = new ChatService(chatRoomRepo, chatMessageRepo, userRepo, eventPublisher,
            new com.phonghub.adapter.out.storage.LocalMediaStorageAdapter(), new IncreasingClock());

        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();

        userA = new CurrentUser(idA, "a@phonghub.local", "User A", UserRole.OWNER);
        userB = new CurrentUser(idB, "b@phonghub.local", "User B", UserRole.TENANT);
        userC = new CurrentUser(idC, "c@phonghub.local", "User C", UserRole.STAFF);

        userRepo.save(new User(idA, "usera", "a@phonghub.local", "User A", "0900000001", UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.now()));
        userRepo.save(new User(idB, "userb", "b@phonghub.local", "User B", "0900000002", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));
        userRepo.save(new User(idC, "userc", "c@phonghub.local", "User C", "0900000003", UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()));
    }

    @Test
    @DisplayName("Kênh Chung Global luôn có trong danh sách phòng chat của mọi người dùng")
    void globalRoomAlwaysAvailable() {
        var rooms = chatService.getRoomsForUser(userA);
        assertFalse(rooms.isEmpty());
        assertEquals(ChatRoomType.GLOBAL, rooms.get(0).type());
        assertEquals("Kênh Chung Ban Quản Lý", rooms.get(0).name());
    }

    @Test
    @DisplayName("Tạo phòng chat 1-1 thành công giữa 2 người dùng khác nhau")
    void createDirectRoomSuccess() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        assertNotNull(room);
        assertEquals(ChatRoomType.DIRECT, room.type());
        assertTrue(room.hasMember(userA.id()));
        assertTrue(room.hasMember(userB.id()));
        assertFalse(room.hasMember(userC.id()));

        // Gọi lại thì trả về chính phòng đã có (idempotent)
        ChatRoom sameRoom = chatService.getOrCreateDirectRoom(userA, userB.id());
        assertEquals(room.id(), sameRoom.id());
    }

    @Test
    @DisplayName("Tạo phòng chat 1-1 với chính mình sẽ ném InvalidChatTargetException (409)")
    void createDirectRoomWithSelfThrowsConflict() {
        assertThrows(InvalidChatTargetException.class, () -> {
            chatService.getOrCreateDirectRoom(userA, userA.id());
        });
    }

    @Test
    @DisplayName("Gửi tin nhắn vào Kênh Chung thành công và lưu vào repository")
    void sendMessageToGlobalRoomSuccess() {
        ChatMessage msg = chatService.sendMessage(userA, ChatRoom.GLOBAL_ROOM_ID, "Xin chào mọi người");
        assertNotNull(msg);
        assertEquals("Xin chào mọi người", msg.content());
        assertEquals(userA.id(), msg.senderId());

        List<ChatMessage> list = chatService.getMessages(userB, ChatRoom.GLOBAL_ROOM_ID, null, 10);
        assertEquals(1, list.size());
        assertEquals("Xin chào mọi người", list.get(0).content());
    }

    @Test
    @DisplayName("Người ngoài phòng chat 1-1 không có quyền truy cập (403 Forbidden)")
    void nonMemberAccessDirectRoomThrowsForbidden() {
        ChatRoom directRoom = chatService.getOrCreateDirectRoom(userA, userB.id());

        // User C không phải thành viên -> Bị chặn 403 khi lấy thông tin phòng
        assertThrows(UnauthorizedChatAccessException.class, () -> {
            chatService.getRoomById(userC, directRoom.id());
        });

        // User C không được phép gửi tin nhắn vào phòng này
        assertThrows(UnauthorizedChatAccessException.class, () -> {
            chatService.sendMessage(userC, directRoom.id(), "Tin nhắn đọc trộm");
        });

        // User C không được phép đọc tin nhắn của phòng này
        assertThrows(UnauthorizedChatAccessException.class, () -> {
            chatService.getMessages(userC, directRoom.id(), null, 10);
        });
    }

    @Test
    @DisplayName("Chưa xác thực người dùng sẽ ném UnauthorizedChatAccessException")
    void unauthenticatedUserThrowsUnauthorized() {
        assertThrows(UnauthorizedChatAccessException.class, () -> {
            chatService.getRoomsForUser(null);
        });

        assertThrows(UnauthorizedChatAccessException.class, () -> {
            chatService.sendMessage(null, ChatRoom.GLOBAL_ROOM_ID, "Hello");
        });
    }

    @Test
    @DisplayName("Đếm tin chưa đọc và cập nhật đã đọc khi mở tin nhắn")
    void unreadCountTracking() {
        ChatRoom directRoom = chatService.getOrCreateDirectRoom(userA, userB.id());

        // User A gửi tin cho User B
        chatService.sendMessage(userA, directRoom.id(), "Tin nhắn 1");
        chatService.sendMessage(userA, directRoom.id(), "Tin nhắn 2");

        // User B có 2 tin chưa đọc
        int unreadBefore = chatService.getTotalUnreadCount(userB);
        assertEquals(2, unreadBefore);

        // User B mở phòng đọc tin
        chatService.getMessages(userB, directRoom.id(), null, 10);

        // Sau khi đọc -> Số tin chưa đọc trở về 0
        int unreadAfter = chatService.getTotalUnreadCount(userB);
        assertEquals(0, unreadAfter);
    }

    @Test
    @DisplayName("Lấy thời điểm đã xem của đối phương trong phòng chat 1-1")
    void peerLastReadAtTracking() {
        ChatRoom directRoom = chatService.getOrCreateDirectRoom(userA, userB.id());

        // Ban đầu User B đọc tin nhắn
        chatService.markAsRead(userB, directRoom.id());

        Instant peerRead = chatService.getPeerLastReadAt(userA, directRoom.id());
        assertNotNull(peerRead);

        // Kênh Global không có đối phương 1-1 -> null
        assertNull(chatService.getPeerLastReadAt(userA, ChatRoom.GLOBAL_ROOM_ID));
    }

    @Test
    @DisplayName("Gửi hình ảnh và video thành công với MediaStoragePort")
    void sendMediaMessageSuccess() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());

        byte[] fakeImage = new byte[] { 1, 2, 3, 4 };
        ChatMessage imageMsg = chatService.sendMediaMessage(
            userA, room.id(), "photo.png", "image/png", fakeImage, "Ảnh phòng trọ mới"
        );

        assertNotNull(imageMsg);
        assertTrue(imageMsg.hasMedia());
        assertTrue(imageMsg.isImage());
        assertFalse(imageMsg.isVideo());
        assertEquals("IMAGE", imageMsg.mediaType());
        assertNotNull(imageMsg.mediaUrl());
        assertEquals("Ảnh phòng trọ mới", imageMsg.content());

        byte[] fakeVideo = new byte[] { 5, 6, 7, 8 };
        ChatMessage videoMsg = chatService.sendMediaMessage(
            userA, room.id(), "tour.mp4", "video/mp4", fakeVideo, null
        );

        assertNotNull(videoMsg);
        assertTrue(videoMsg.hasMedia());
        assertTrue(videoMsg.isVideo());
        assertEquals("VIDEO", videoMsg.mediaType());
        assertNotNull(videoMsg.mediaUrl());

        // Kiểm tra danh sách tóm tắt phòng hiển thị nhãn media
        var rooms = chatService.getRoomsForUser(userA);
        var targetRoomSummary = rooms.stream().filter(r -> r.id().equals(room.id())).findFirst().orElseThrow();
        assertEquals("[Video]", targetRoomSummary.lastMessageContent());
    }

    @Test
    @DisplayName("Gửi media vượt quá 25MB phải ném IllegalArgumentException")
    void sendMediaExceedsLimitThrows() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        byte[] largeBytes = new byte[26 * 1024 * 1024];

        assertThrows(IllegalArgumentException.class, () -> {
            chatService.sendMediaMessage(userA, room.id(), "big.mp4", "video/mp4", largeBytes, null);
        });
    }

    @Test
    @DisplayName("Gửi tệp không hỗ trợ phải ném IllegalArgumentException")
    void sendMediaUnsupportedFormatThrows() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        byte[] docBytes = new byte[] { 1, 2, 3 };

        assertThrows(IllegalArgumentException.class, () -> {
            chatService.sendMediaMessage(userA, room.id(), "contract.pdf", "application/pdf", docBytes, null);
        });
    }
}
