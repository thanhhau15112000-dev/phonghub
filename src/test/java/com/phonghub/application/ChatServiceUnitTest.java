package com.phonghub.application;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.service.ChatService;
import com.phonghub.domain.exception.ChatMessageNotFoundException;
import com.phonghub.domain.exception.ChatRoomNotFoundException;
import com.phonghub.domain.exception.InvalidChatTargetException;
import com.phonghub.domain.exception.UnauthorizedChatAccessException;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceUnitTest {

    private static final long MAX_MEDIA_BYTES = 1024 * 1024;

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
    private InMemoryFileStorageAdapter storage;
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

        storage = new InMemoryFileStorageAdapter();

        chatService = new ChatService(chatRoomRepo, chatMessageRepo, userRepo, eventPublisher, storage,
            MAX_MEDIA_BYTES, new IncreasingClock());

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
    @DisplayName("Gửi ảnh và video: lưu vào kho private theo khóa của phòng, tải lại qua URL có chữ ký")
    void sendMediaMessageSuccess() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());

        ChatMessage imageMsg = chatService.sendMediaMessage(userA, room.id(), "image/png", pngBytes(), "Ảnh phòng trọ mới");

        assertTrue(imageMsg.isImage());
        assertEquals("Ảnh phòng trọ mới", imageMsg.content());
        assertTrue(imageMsg.mediaKey().matches("chat/" + room.id() + "/[0-9a-f-]{36}\\.png"));
        URI signed = chatService.mediaSignedUrl(userB, room.id(), imageMsg.id());
        assertTrue(storage.openSignedUrl(signed).isPresent());

        ChatMessage videoMsg = chatService.sendMediaMessage(userA, room.id(), "video/mp4", mp4Bytes(), null);

        assertTrue(videoMsg.isVideo());
        assertTrue(videoMsg.mediaKey().endsWith(".mp4"));
        assertArrayEquals(mp4Bytes(), storage.get(videoMsg.mediaKey()));

        var rooms = chatService.getRoomsForUser(userA);
        var targetRoomSummary = rooms.stream().filter(r -> r.id().equals(room.id())).findFirst().orElseThrow();
        assertEquals("[Video]", targetRoomSummary.lastMessageContent());
    }

    @Test
    @DisplayName("Media vượt giới hạn dung lượng bị từ chối và không lưu tin nhắn")
    void sendMediaExceedsLimitThrows() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        byte[] largeVideo = new byte[(int) MAX_MEDIA_BYTES + 1];
        System.arraycopy(mp4Bytes(), 0, largeVideo, 0, 12);

        assertThrows(IllegalArgumentException.class,
            () -> chatService.sendMediaMessage(userA, room.id(), "video/mp4", largeVideo, null));
        assertTrue(chatMessageRepo.findByRoomId(room.id(), null, 10).isEmpty());
    }

    @Test
    @DisplayName("Tệp không phải ảnh/video được hỗ trợ hoặc sai chữ ký nội dung bị từ chối")
    void sendMediaUnsupportedFormatThrows() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());

        assertThrows(IllegalArgumentException.class, () -> chatService.sendMediaMessage(
            userA, room.id(), "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII), null));
        assertThrows(IllegalArgumentException.class, () -> chatService.sendMediaMessage(
            userA, room.id(), "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII), null));
        assertThrows(IllegalArgumentException.class, () -> chatService.sendMediaMessage(
            userA, room.id(), "image/png", new byte[] {1, 2, 3, 4}, null));
        assertTrue(chatMessageRepo.findByRoomId(room.id(), null, 10).isEmpty());
    }

    @Test
    @DisplayName("Người ngoài phòng 1-1 không tải lên được và không lấy được URL media")
    void mediaAccessRequiresRoomMembership() {
        ChatRoom room = chatService.getOrCreateDirectRoom(userA, userB.id());
        ChatMessage imageMsg = chatService.sendMediaMessage(userA, room.id(), "image/png", pngBytes(), null);

        assertThrows(UnauthorizedChatAccessException.class,
            () -> chatService.mediaSignedUrl(userC, room.id(), imageMsg.id()));
        assertThrows(UnauthorizedChatAccessException.class,
            () -> chatService.sendMediaMessage(userC, room.id(), "image/png", pngBytes(), null));
    }

    @Test
    @DisplayName("Tin nhắn thuộc phòng khác hoặc không có media trả về không tìm thấy")
    void mediaSignedUrlRejectsForeignRoomAndTextMessage() {
        ChatRoom roomAB = chatService.getOrCreateDirectRoom(userA, userB.id());
        ChatRoom roomAC = chatService.getOrCreateDirectRoom(userA, userC.id());
        ChatMessage imageMsg = chatService.sendMediaMessage(userA, roomAB.id(), "image/png", pngBytes(), null);
        ChatMessage textMsg = chatService.sendMessage(userA, roomAB.id(), "Xin chào");

        assertThrows(ChatMessageNotFoundException.class,
            () -> chatService.mediaSignedUrl(userA, roomAC.id(), imageMsg.id()));
        assertThrows(ChatMessageNotFoundException.class,
            () -> chatService.mediaSignedUrl(userA, roomAB.id(), textMsg.id()));
        assertThrows(ChatMessageNotFoundException.class,
            () -> chatService.mediaSignedUrl(userA, roomAB.id(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("Lỗi kho lưu trữ trả về cho người gọi, không lưu tin nhắn")
    void storageFailurePropagatesWithoutSavingMessage() {
        FileStoragePort failing = new FileStoragePort() {
            @Override
            public void put(String objectKey, String contentType, byte[] content) {
                throw new IllegalStateException("Supabase Storage unavailable");
            }

            @Override
            public byte[] get(String objectKey) {
                throw new UnsupportedOperationException();
            }

            @Override
            public URI createSignedUrl(String objectKey, Duration ttl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String objectKey) {
                throw new UnsupportedOperationException();
            }
        };
        ChatService service = new ChatService(chatRoomRepo, chatMessageRepo, userRepo, eventPublisher, failing,
            MAX_MEDIA_BYTES, new IncreasingClock());
        ChatRoom room = service.getOrCreateDirectRoom(userA, userB.id());

        assertThrows(IllegalStateException.class,
            () -> service.sendMediaMessage(userA, room.id(), "image/png", pngBytes(), null));
        assertTrue(chatMessageRepo.findByRoomId(room.id(), null, 10).isEmpty());
    }

    @Test
    @DisplayName("Lưu tin nhắn thất bại thì xóa đối tượng vừa tải lên")
    void messageSaveFailureDeletesStoredObject() {
        String[] storedKey = new String[1];
        InMemoryFileStorageAdapter trackingStorage = new InMemoryFileStorageAdapter() {
            @Override
            public void put(String objectKey, String contentType, byte[] content) {
                storedKey[0] = objectKey;
                super.put(objectKey, contentType, content);
            }
        };
        InMemoryChatMessageRepository failingRepo = new InMemoryChatMessageRepository() {
            @Override
            public ChatMessage save(ChatMessage message) {
                throw new IllegalStateException("database unavailable");
            }
        };
        ChatService service = new ChatService(chatRoomRepo, failingRepo, userRepo, eventPublisher, trackingStorage,
            MAX_MEDIA_BYTES, new IncreasingClock());
        ChatRoom room = service.getOrCreateDirectRoom(userA, userB.id());

        assertThrows(IllegalStateException.class,
            () -> service.sendMediaMessage(userA, room.id(), "image/png", pngBytes(), null));
        assertNotNull(storedKey[0]);
        assertThrows(IllegalArgumentException.class, () -> trackingStorage.get(storedKey[0]));
    }

    private static byte[] pngBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] mp4Bytes() {
        return new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }
}
