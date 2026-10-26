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
import java.util.ArrayList;
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
    private RecordingPublisher recorder;
    private ChatService chatService;

    private CurrentUser userA;
    private CurrentUser userB;
    private CurrentUser userC;

    @BeforeEach
    void setUp() {
        chatRoomRepo = new InMemoryChatRoomRepository();
        chatMessageRepo = new InMemoryChatMessageRepository();
        userRepo = new InMemoryUserRepository();
        recorder = new RecordingPublisher();

        chatService = new ChatService(chatRoomRepo, chatMessageRepo, userRepo, recorder, new IncreasingClock());

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
    @DisplayName("Tin nhắn cũ có ảnh/video (trước khi gỡ media khỏi chat) vẫn đọc được")
    void legacyMediaMessageStillReadable() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        chatMessageRepo.save(new ChatMessage(UUID.randomUUID(), room.id(), userA.id(), userA.fullName(),
            "Ảnh phòng trọ", "https://example.invalid/chat-media/old.png", "IMAGE", Instant.now(), null));

        List<ChatMessage> messages = chatService.getMessages(userB, room.id(), null, 10);
        assertEquals(1, messages.size());
        assertEquals("Ảnh phòng trọ", messages.get(0).content());

        var summary = chatService.getRoomsForUser(userB).stream()
            .filter(r -> r.id().equals(room.id())).findFirst().orElseThrow();
        assertEquals("[Hình ảnh] Ảnh phòng trọ", summary.lastMessageContent());
    }

    @Test
    @DisplayName("Tin mới phát sự kiện kèm phòng để chỉ thành viên phòng 1-1 nhận")
    void newMessageEventCarriesRoomMembers() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());

        ChatMessage sent = chatService.sendMessage(userA, room.id(), "Chào bạn");

        assertEquals(1, recorder.messages.size());
        ChatRoom eventRoom = recorder.messageRooms.get(0);
        assertEquals(sent.id(), recorder.messages.get(0).id());
        assertTrue(eventRoom.hasMember(userA.id()));
        assertTrue(eventRoom.hasMember(userB.id()));
        assertFalse(eventRoom.hasMember(userC.id()));
    }

    @Test
    @DisplayName("Trạng thái đã xem chỉ phát cho phòng 1-1, người ngoài phòng đánh dấu đã đọc bị chặn")
    void readReceiptOnlyForDirectRoomMembers() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());

        chatService.markAsRead(userB, room.id());
        chatService.markAsRead(userB, ChatRoom.GLOBAL_ROOM_ID);

        assertEquals(List.of(room.id()), recorder.receiptRooms.stream().map(ChatRoom::id).toList());
        assertThrows(UnauthorizedChatAccessException.class, () -> chatService.markAsRead(userC, room.id()));
        assertEquals(1, recorder.receiptRooms.size(), "người ngoài phòng không tạo sự kiện đã xem");
    }

    /** Ghi lại sự kiện realtime ChatService phát ra. */
    private static final class RecordingPublisher implements ChatEventPublisherPort {
        private final List<ChatRoom> messageRooms = new ArrayList<>();
        private final List<ChatMessage> messages = new ArrayList<>();
        private final List<ChatRoom> receiptRooms = new ArrayList<>();

        @Override
        public void publishNewMessage(ChatRoom room, ChatMessage message) {
            messageRooms.add(room);
            messages.add(message);
        }

        @Override
        public void publishReadReceipt(ChatRoom room, UUID userId, Instant readAt) {
            receiptRooms.add(room);
        }
    }
}
