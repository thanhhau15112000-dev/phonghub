package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatMessageRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryChatRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.application.port.in.ChatMessageUseCase.MediaUpload;
import com.phonghub.application.port.out.ChatEventPublisherPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.service.ChatService;
import com.phonghub.domain.exception.ChatMessageNotFoundException;
import com.phonghub.domain.exception.FileStorageUnavailableException;
import com.phonghub.domain.exception.UnauthorizedChatAccessException;
import com.phonghub.domain.model.ChatAttachment;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Gửi và xem ảnh/video trong chat (#251): giới hạn, định dạng, kho riêng tư, quyền xem, dọn tệp khi lỗi. */
class ChatMediaServiceUnitTest {

    private static final long MAX_IMAGE = 200_000;
    private static final long MAX_VIDEO = 50_000;

    private InMemoryChatRoomRepository rooms;
    private InMemoryChatMessageRepository messages;
    private TrackingStorage storage;
    private List<ChatMessage> published;
    private ChatService chat;
    private CurrentUser alice;
    private CurrentUser bob;
    private CurrentUser carol;
    private ChatRoom direct;

    /** Kho in-memory ghi lại các khóa đã ghi/xóa; có thể giả lập lỗi ở lần ghi thứ n. */
    private static final class TrackingStorage implements FileStoragePort {
        final InMemoryFileStorageAdapter delegate = new InMemoryFileStorageAdapter();
        final List<String> stored = Collections.synchronizedList(new ArrayList<>());
        final List<String> deleted = Collections.synchronizedList(new ArrayList<>());
        int failOnPut = -1;

        @Override
        public void put(String objectKey, String contentType, byte[] content) {
            if (stored.size() == failOnPut) {
                throw new IllegalStateException("bucket down");
            }
            delegate.put(objectKey, contentType, content);
            stored.add(objectKey);
        }

        @Override
        public byte[] get(String objectKey) {
            return delegate.get(objectKey);
        }

        @Override
        public URI createSignedUrl(String objectKey, Duration ttl) {
            return delegate.createSignedUrl(objectKey, ttl);
        }

        @Override
        public void delete(String objectKey) {
            delegate.delete(objectKey);
            deleted.add(objectKey);
        }
    }

    @BeforeEach
    void setUp() {
        rooms = new InMemoryChatRoomRepository();
        messages = new InMemoryChatMessageRepository();
        InMemoryUserRepository users = new InMemoryUserRepository();
        storage = new TrackingStorage();
        published = new ArrayList<>();
        ChatEventPublisherPort publisher = new ChatEventPublisherPort() {
            @Override
            public void publishNewMessage(ChatRoom room, ChatMessage message) {
                published.add(message);
            }

            @Override
            public void publishReadReceipt(ChatRoom room, UUID readerId, Instant readAt) {
            }
        };
        chat = new ChatService(rooms, messages, users, publisher, storage, MAX_IMAGE, MAX_VIDEO, Clock.systemUTC());

        alice = new CurrentUser(UUID.randomUUID(), "alice@phonghub.local", "Alice", UserRole.OWNER);
        bob = new CurrentUser(UUID.randomUUID(), "bob@phonghub.local", "Bob", UserRole.TENANT);
        carol = new CurrentUser(UUID.randomUUID(), "carol@phonghub.local", "Carol", UserRole.TENANT);
        for (CurrentUser u : List.of(alice, bob, carol)) {
            users.save(new User(u.id(), u.email().split("@")[0], u.email(), u.fullName(), "0900000000", u.role(),
                User.UserStatus.ACTIVE, false, Instant.now()));
        }
        direct = chat.getOrCreateDirectRoom(alice, bob.id());
    }

    static byte[] jpeg() {
        return image("jpg");
    }

    static byte[] png() {
        return image("png");
    }

    private static byte[] image(String format) {
        try {
            BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] mp4(int size) {
        byte[] bytes = new byte[size];
        byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    @Test
    @DisplayName("Images and a video are stored privately under chat/{roomId}/ and the message carries keys, not URLs")
    void sendsImagesAndVideo() {
        ChatMessage sent = chat.sendMediaMessage(alice, direct.id(), "  Phòng bị dột  ", List.of(
            new MediaUpload("image/jpeg", jpeg()), new MediaUpload("image/png", png()),
            new MediaUpload("video/mp4", mp4(2_000))));

        assertEquals("Phòng bị dột", sent.content());
        assertEquals(3, sent.attachments().size());
        assertEquals(List.of("IMAGE", "IMAGE", "VIDEO"), sent.attachments().stream().map(ChatAttachment::mediaType).toList());
        assertEquals(List.of(0, 1, 2), sent.attachments().stream().map(ChatAttachment::position).toList());
        assertTrue(sent.attachments().stream().allMatch(a -> a.objectKey().startsWith("chat/" + direct.id() + "/")));
        assertTrue(sent.attachments().get(0).objectKey().endsWith(".jpg"));
        assertTrue(sent.attachments().get(2).objectKey().endsWith(".mp4"));
        assertEquals(3, storage.stored.size());
        assertEquals(sent.id(), messages.findById(sent.id()).orElseThrow().id());
        assertEquals(1, published.size());
        assertTrue(chat.getRoomsForUser(bob).stream().anyMatch(r -> "[Video] Phòng bị dột".equals(r.lastMessageContent())));
    }

    @Test
    @DisplayName("A message may carry images only, with no caption")
    void imageWithoutCaption() {
        ChatMessage sent = chat.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg())));
        assertEquals("", sent.content());
        assertTrue(chat.getRoomsForUser(bob).stream().anyMatch(r -> "[Hình ảnh]".equals(r.lastMessageContent())));
    }

    @Test
    @DisplayName("Count, type and size rules are checked before anything reaches storage")
    void rejectsInvalidUploads() {
        List<List<MediaUpload>> invalid = List.of(
            List.of(),
            Collections.nCopies(6, new MediaUpload("image/jpeg", jpeg())),
            List.of(new MediaUpload("video/mp4", mp4(1_000)), new MediaUpload("video/webm", new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0})),
            List.of(new MediaUpload("image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII))),
            List.of(new MediaUpload("video/x-msvideo", mp4(1_000))),
            List.of(new MediaUpload("application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII))),
            // đổi đuôi: khai báo ảnh nhưng nội dung là chữ
            List.of(new MediaUpload("image/png", "not a png at all".getBytes(StandardCharsets.US_ASCII))),
            List.of(new MediaUpload("video/mp4", "not a video".getBytes(StandardCharsets.US_ASCII))),
            List.of(new MediaUpload("image/jpeg", new byte[(int) MAX_IMAGE + 1])),
            List.of(new MediaUpload("video/mp4", mp4((int) MAX_VIDEO + 1))),
            // ảnh hợp lệ đi kèm một tệp sai: cả tin bị từ chối
            List.of(new MediaUpload("image/jpeg", jpeg()), new MediaUpload("image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII))));
        for (List<MediaUpload> uploads : invalid) {
            assertThrows(IllegalArgumentException.class, () -> chat.sendMediaMessage(alice, direct.id(), "x", uploads));
        }
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class, () ->
            chat.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("video/mp4", mp4((int) MAX_VIDEO + 1)))));
        assertTrue(tooBig.getMessage().startsWith("Video vượt quá dung lượng"));
        assertTrue(storage.stored.isEmpty());
        assertTrue(messages.findByRoomId(direct.id(), null, 50).isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    @DisplayName("Someone outside a direct room can neither send media into it nor open its attachments")
    void outsiderIsRefused() {
        ChatMessage sent = chat.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg())));
        UUID attachmentId = sent.attachments().get(0).id();

        assertThrows(UnauthorizedChatAccessException.class, () ->
            chat.sendMediaMessage(carol, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg()))));
        assertThrows(UnauthorizedChatAccessException.class, () ->
            chat.attachmentFile(carol, direct.id(), sent.id(), attachmentId));
        assertThrows(UnauthorizedChatAccessException.class, () ->
            chat.attachmentSignedUrl(carol, direct.id(), sent.id(), attachmentId));

        // Thành viên mở được; nội dung là ảnh đã làm sạch đã lưu
        assertArrayEquals(storage.get(sent.attachments().get(0).objectKey()),
            chat.attachmentFile(bob, direct.id(), sent.id(), attachmentId).content());
        assertEquals("image/jpeg", chat.attachmentFile(bob, direct.id(), sent.id(), attachmentId).contentType());
    }

    @Test
    @DisplayName("Old messages from before #201 give their stored media URL only to readers of the room")
    void legacyMediaUrl() {
        ChatMessage legacy = messages.save(new ChatMessage(UUID.randomUUID(), direct.id(), alice.id(), "Alice", "Ảnh cũ",
            "https://demo.supabase.co/storage/v1/object/public/chat-media/old.jpg", "IMAGE", Instant.now(), null));
        ChatMessage text = chat.sendMessage(alice, direct.id(), "Chỉ có chữ");
        ChatRoom other = chat.getOrCreateDirectRoom(alice, carol.id());

        assertEquals(legacy.mediaUrl(), chat.legacyMediaUrl(bob, direct.id(), legacy.id()));
        assertThrows(UnauthorizedChatAccessException.class, () -> chat.legacyMediaUrl(carol, direct.id(), legacy.id()));
        assertThrows(ChatMessageNotFoundException.class, () -> chat.legacyMediaUrl(alice, other.id(), legacy.id()));
        assertThrows(ChatMessageNotFoundException.class, () -> chat.legacyMediaUrl(alice, direct.id(), text.id()));
    }

    @Test
    @DisplayName("Attachment ids must belong to the message and the message to the room")
    void attachmentMustMatchRoom() {
        ChatMessage sent = chat.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg())));
        ChatRoom other = chat.getOrCreateDirectRoom(alice, carol.id());
        UUID attachmentId = sent.attachments().get(0).id();

        assertThrows(ChatMessageNotFoundException.class, () -> chat.attachmentFile(alice, other.id(), sent.id(), attachmentId));
        assertThrows(ChatMessageNotFoundException.class, () -> chat.attachmentFile(alice, direct.id(), sent.id(), UUID.randomUUID()));
        assertThrows(ChatMessageNotFoundException.class, () -> chat.attachmentFile(alice, direct.id(), UUID.randomUUID(), attachmentId));
    }

    @Test
    @DisplayName("Everyone can post media in the public channel and every member can open it")
    void globalChannel() {
        ChatRoom global = rooms.save(ChatRoom.createGlobal());
        ChatMessage sent = chat.sendMediaMessage(bob, global.id(), "Thông báo", List.of(new MediaUpload("video/mp4", mp4(1_000))));
        assertEquals("video/mp4", chat.attachmentFile(carol, global.id(), sent.id(), sent.attachments().get(0).id()).contentType());
    }

    @Test
    @DisplayName("Storage failure: files already stored are removed, no message is created, a Vietnamese error is raised")
    void storageFailureCleansUp() {
        storage.failOnPut = 1;
        FileStorageUnavailableException ex = assertThrows(FileStorageUnavailableException.class, () ->
            chat.sendMediaMessage(alice, direct.id(), "x", List.of(new MediaUpload("image/jpeg", jpeg()),
                new MediaUpload("image/png", png()))));
        assertTrue(ex.getMessage().startsWith("Không lưu được ảnh/video"));
        assertEquals(storage.stored, storage.deleted);
        assertTrue(messages.findByRoomId(direct.id(), null, 50).isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    @DisplayName("Saving the message fails: uploaded files are deleted again")
    void saveFailureCleansUp() {
        ChatService failing = new ChatService(rooms, new InMemoryChatMessageRepository() {
            @Override
            public ChatMessage save(ChatMessage message) {
                throw new IllegalStateException("db down");
            }
        }, new InMemoryUserRepository(), new ChatEventPublisherPort() {
            @Override
            public void publishNewMessage(ChatRoom room, ChatMessage message) {
            }

            @Override
            public void publishReadReceipt(ChatRoom room, UUID readerId, Instant readAt) {
            }
        }, storage, MAX_IMAGE, MAX_VIDEO, Clock.systemUTC());
        assertThrows(IllegalStateException.class, () ->
            failing.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg()))));
        assertEquals(1, storage.stored.size());
        assertEquals(storage.stored, storage.deleted);
    }

    @Test
    @DisplayName("Without configured storage, media sending is refused and text chat still works")
    void noStorageConfigured() {
        ChatService textOnly = new ChatService(rooms, messages, new InMemoryUserRepository(), new ChatEventPublisherPort() {
            @Override
            public void publishNewMessage(ChatRoom room, ChatMessage message) {
            }

            @Override
            public void publishReadReceipt(ChatRoom room, UUID readerId, Instant readAt) {
            }
        }, Clock.systemUTC());
        assertThrows(IllegalStateException.class, () ->
            textOnly.sendMediaMessage(alice, direct.id(), null, List.of(new MediaUpload("image/jpeg", jpeg()))));
        assertFalse(textOnly.sendMessage(alice, direct.id(), "chào").hasAttachments());
    }
}
